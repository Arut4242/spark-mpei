package ru.mpei.assistant.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import ru.mpei.assistant.data.model.headman.*
import ru.mpei.assistant.data.network.BarsWebClient
import ru.mpei.assistant.data.parser.HeadmanParser
import ru.mpei.assistant.data.parser.HeadmanReportParser
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HeadmanRepository(private val client: BarsWebClient) {
    private val _group = MutableStateFlow<HeadmanGroup?>(null)
    val group = _group.asStateFlow()
    @Volatile private var generation = 0
    private val json = Json { ignoreUnknownKeys = true }
    private val reports = mutableMapOf<Pair<String, HeadmanReportKind>, HeadmanReport>()

    fun reset() { generation++; _group.value = null; reports.clear() }

    fun cachedReport(kind: HeadmanReportKind, journal: HeadmanJournal): HeadmanReport? = reports[journal.id to kind]

    private suspend fun <T> result(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        val requestGeneration = generation
        try {
            val value = block()
            check(requestGeneration == generation && client.isAuthorized()) { "Сессия БАРС изменилась" }
            Result.success(value)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun discover(): Result<HeadmanGroup?> {
        val found = result {
            val menu = client.fetchHtml("https://bars.mpei.ru/bars_web/Auth/Menu").getOrThrow()
            if (!HeadmanParser.hasStudyGroups(menu)) null
            else HeadmanParser.firstGroup(client.fetchHtml(
                "https://bars.mpei.ru/bars_web/SG/StudyGroup/ListStudyGroup").getOrThrow())
        }
        if (found.isSuccess) _group.value = found.getOrNull()
        return found
    }

    private fun groupId() = checkNotNull(_group.value) { "Кабинет старосты недоступен" }.id

    private suspend fun html(path: String): String = client.fetchHtml("https://bars.mpei.ru/bars_web/$path").getOrThrow()

    suspend fun journals(): Result<List<HeadmanJournal>> = result {
        HeadmanParser.journals(html("SG/TrainingJournal/ListStudyGroup__TrainingJournals?sgID=${groupId()}"))
    }

    suspend fun students(): Result<List<GroupStudent>> = result {
        val id = groupId()
        val students = mutableListOf<GroupStudent>()
        var page = 1
        do {
            val query = buildJsonObject {
                put("ID", id); put("Page", page.toString()); put("PageSize", "500")
                put("FilterTrainingStatus", buildJsonObject { put("Value", JsonArray(listOf(JsonPrimitive("*")))) })
            }
            val url = client.trustedUrl("SG/Student/_PartialListStudyGroup__Students?sgID=$id")
                .newBuilder().addQueryParameter("query", query.toString()).build()
            val response = client.fetchHtml(url.toString()).getOrThrow()
            students += HeadmanParser.students(response)
            if (!HeadmanParser.hasNextPage(response)) break
            page++
            check(page <= 20) { "БАРС вернул слишком много страниц списка группы" }
        } while (true)
        students.distinctBy { it.id }
    }

    suspend fun subjects(journal: HeadmanJournal): Result<List<HeadmanSubject>> = result {
        val query = buildJsonObject { put("ID", journal.id); put("Page", "1"); put("PageSize", "500") }
        val url = client.trustedUrl("SG/AttendanceSheet/_PartialListTrainingJournal_AttendanceSheets?tjID=${journal.id}")
            .newBuilder().addQueryParameter("query", query.toString()).build()
        HeadmanParser.subjects(client.fetchHtml(url.toString()).getOrThrow())
    }

    suspend fun lessons(journal: HeadmanJournal, date: LocalDate?, subject: String?): Result<List<HeadmanLesson>> = result {
        val lessons = mutableListOf<HeadmanLesson>()
        var page = 1
        do {
            val query = buildJsonObject {
                put("ID", journal.id); put("Page", page.toString()); put("PageSize", "100")
                put("SortOrder", "number desc"); put("SearchText", subject.orEmpty())
                val formatted = date?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")).orEmpty()
                put("FilterStartDate", formatted); put("FilterEndDate", formatted)
                put("DisplayModeFilter", buildJsonObject { put("Value", "LIS_1;LIS_2;LIS_3;LT_1;LT_2;LT_3;LT_4;1;2;") })
            }
            val url = client.trustedUrl("SG/Lesson/_PartialListTrainingJournal_Lessons?tjID=${journal.id}")
                .newBuilder().addQueryParameter("query", query.toString()).build()
            val response = client.fetchHtml(url.toString()).getOrThrow()
            lessons += HeadmanParser.lessons(response)
            if (!HeadmanParser.hasNextPage(response)) break
            page++
            check(page <= 50) { "Слишком много занятий. Уточните фильтр" }
        } while (true)
        val filtered = lessons.distinctBy { it.id }.filter { date == null || it.date == date }
        if (date == null) filtered.sortedWith(compareByDescending<HeadmanLesson> { it.date }.thenBy { it.number })
        else filtered.sortedBy { it.number }
    }

    suspend fun attendance(lessonId: String): Result<LessonAttendance> = result {
        require(lessonId.toLongOrNull() != null)
        HeadmanParser.attendanceForm(html("SG/Lesson/EditLesson?lesID=$lessonId&uip=tj"), lessonId)
    }

    suspend fun saveAttendance(original: LessonAttendance, absentIds: Set<String>): Result<Unit> = result {
        check(absentIds.all { id -> original.students.any { it.id == id } }) { "В списке есть неизвестный студент" }
        val fresh = attendance(original.lessonId).getOrThrow()
        check(fresh.students.associate { it.id to it.reason } == original.students.associate { it.id to it.reason }) {
            "Отметки уже изменены в БАРС. Закройте и откройте занятие заново"
        }
        val updated = HeadmanParser.updateAttendance(fresh.serialized, absentIds)
        val fields = fresh.fields.map { if (it.first == "AttendanceSheetLessonStudentListSerialized") it.first to updated else it }
        val response = client.requestData("SG/Lesson/EditLesson?lesID=${original.lessonId}&uip=tj", fields).getOrThrow()
        val answer = jsonResponse(response)
        if (answer?.get("success")?.jsonPrimitive?.booleanOrNull != true) {
            val validation = Jsoup.parse(response.text()).select(".validation-summary-errors, .field-validation-error").text()
            error(answer?.get("error")?.jsonPrimitive?.contentOrNull ?: validation.ifBlank { "БАРС не подтвердил сохранение" })
        }
    }

    /** Called only from the report button; never by workers, tab changes, or filters. */
    suspend fun requestReport(kind: HeadmanReportKind, journal: HeadmanJournal): Result<HeadmanReport> {
        val response = result {
            val group = checkNotNull(_group.value)
            val semester = HeadmanParser.semesterId(html("SG/TrainingJournal/EditTrainingJournal?tjID=${journal.id}"))
                ?: error("Не удалось определить семестр журнала")
            val path = "R/${kind.endpoint}/Main"
            val reportForm = Jsoup.parse(html(path))
            val hiddenFields = reportForm.select("form input[type=hidden][name]").map { it.attr("name") to it.attr("value") }
            val form = client.requestData(path, hiddenFields + listOf("StudyGroupName" to group.name, "SemesterID" to semester), multipart = true).getOrThrow()
            val answer = jsonResponse(form)
            val file = if (answer != null) {
                check(answer["success"]?.jsonPrimitive?.booleanOrNull == true) {
                    answer["error"]?.jsonPrimitive?.contentOrNull ?: "Не удалось сформировать отчёт"
                }
                val url = answer["url"]?.jsonPrimitive?.contentOrNull ?: error("БАРС не вернул ссылку на отчёт")
                client.requestData(url).getOrThrow()
            } else form
            HeadmanReportParser.parse(file.bytes, students().getOrThrow())
        }
        response.onSuccess { reports[journal.id to kind] = it }
        return response
    }

    private fun jsonResponse(page: BarsWebClient.PageData): JsonObject? {
        if (!page.contentType.contains("json") && page.bytes.take(64).map { it.toInt().toChar() }.joinToString("").trimStart().firstOrNull() != '{') {
            return null
        }
        return runCatching { json.parseToJsonElement(page.text()).jsonObject }.getOrNull()
    }
}
