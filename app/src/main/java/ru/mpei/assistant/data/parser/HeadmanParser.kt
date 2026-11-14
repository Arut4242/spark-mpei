package ru.mpei.assistant.data.parser

import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import ru.mpei.assistant.data.model.headman.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object HeadmanParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val uuid = Regex("[0-9a-fA-F-]{36}")

    fun hasStudyGroups(html: String): Boolean = Jsoup.parse(html).select("a[href]").any {
        it.text().trim().equals("Учебные группы", ignoreCase = true) &&
            it.attr("href").substringBefore('?').substringBefore('#').endsWith("/SG/StudyGroup/ListStudyGroup")
    }

    private fun id(link: Element?, key: String): String? = link?.attr("href")?.let {
        Regex("[?&]$key=([^&#]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)
    }

    fun firstGroup(html: String): HeadmanGroup? {
        val table = Jsoup.parse(html).getElementById("tbl__PartialListStudyGroup") ?: return null
        val link = table.select("tbody a[href*='EditStudyGroup?sgID=']").firstOrNull() ?: return null
        val groupId = id(link, "sgID")?.takeIf { uuid.matches(it) } ?: return null
        return HeadmanGroup(groupId, link.text().trim())
    }

    fun journals(html: String): List<HeadmanJournal> = Jsoup.parse(html)
        .select("#tbl__PartialListStudyGroup__TrainingJournals tbody tr").mapNotNull { row ->
            val link = row.select("a[href*='EditTrainingJournal?tjID=']").firstOrNull { it.text().isNotBlank() }
            val journalId = id(link, "tjID")?.takeIf { it.toLongOrNull() != null } ?: return@mapNotNull null
            HeadmanJournal(journalId, link!!.text())
        }.distinctBy { it.id }

    fun currentJournal(journals: List<HeadmanJournal>, date: LocalDate = headmanDay()): HeadmanJournal? {
        val autumn = date.monthValue >= 9 || date.monthValue == 1
        val startYear = if (date.monthValue >= 9) date.year else date.year - 1
        return journals.firstOrNull {
            it.name.contains("$startYear/${startYear + 1}") &&
                it.name.contains(if (autumn) "Осенний" else "Весенний", ignoreCase = true)
        }
    }

    fun semesterId(html: String): String? {
        val options = Jsoup.parse(html).select("select#SemesterID option")
        return (options.firstOrNull { it.hasAttr("selected") } ?: options.firstOrNull())
            ?.attr("value")?.takeIf { it.toIntOrNull() != null }
    }

    fun students(html: String): List<GroupStudent> = Jsoup.parse(html)
        .select("#tbl__PartialListStudyGroup__Students tbody tr").mapNotNull { row ->
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.size < 4 || cells[0].text().trim().toIntOrNull() == null) return@mapNotNull null
            val studentId = id(cells[1].select("a[href*='studentID=']").firstOrNull(), "studentID")
                ?: return@mapNotNull null
            GroupStudent(studentId, cells[0].text().trim(), cells[1].text().trim(),
                cells[2].select("a[href^='mailto:']").firstOrNull()?.attr("href")?.removePrefix("mailto:").orEmpty(),
                cells[3].text().trim())
        }

    fun subjects(html: String): List<HeadmanSubject> = Jsoup.parse(html)
        .select("#tbl__PartialListTrainingJournal_AttendanceSheets tbody tr").mapNotNull { row ->
            val link = row.select("a[href*='EditAttendanceSheet?asID=']").firstOrNull { it.text().isNotBlank() }
            val sheetId = id(link, "asID") ?: return@mapNotNull null
            HeadmanSubject(sheetId, link!!.text())
        }.distinctBy { it.id }

    fun lessons(html: String): List<HeadmanLesson> = Jsoup.parse(html)
        .select("#tbl__PartialListTrainingJournal_Lessons tr[data-les-id]").mapNotNull { row ->
            val title = row.select("td label").firstOrNull()?.text() ?: return@mapNotNull null
            val match = Regex("^(\\d{2}\\.\\d{2}\\.\\d{2,4}),\\s*(\\d+)\\s+пара").find(title)
                ?: return@mapNotNull null
            val dateText = match.groupValues[1]
            val date = runCatching { LocalDate.parse(dateText, DateTimeFormatter.ofPattern(
                if (dateText.length == 8) "dd.MM.yy" else "dd.MM.yyyy", Locale.ROOT)) }.getOrNull()
                ?: return@mapNotNull null
            HeadmanLesson(row.attr("data-les-id"), date, match.groupValues[2].toInt(), title,
                row.select(".ls-header").joinToString(" ") { it.text() }, row.select(".badge").text())
        }

    fun lessonStudents(html: String): List<LessonStudent> {
        val doc = Jsoup.parse(html)
        val serialized = doc.select("input[name='AttendanceSheetLessonStudentListSerialized']").attr("value")
        if (serialized.isBlank()) return emptyList()
        val root = json.parseToJsonElement(serialized).jsonObject
        return root["AttendanceSheetLessonStudents"]?.jsonArray.orEmpty().flatMap { sheet ->
            sheet.jsonObject["Students"]?.jsonArray.orEmpty().map { item ->
                val student = item.jsonObject
                LessonStudent(student["StudentID"]!!.jsonPrimitive.content,
                    student["DisplayName"]!!.jsonPrimitive.content,
                    student["LessonSkipReasonID"]?.jsonPrimitive?.contentOrNull)
            }
        }.distinctBy { it.id }
    }

    fun attendanceForm(html: String, lessonId: String): LessonAttendance {
        val form = Jsoup.parse(html).select("form").firstOrNull {
            it.select("input[name='AttendanceSheetLessonStudentListSerialized']").isNotEmpty()
        } ?: error("БАРС не вернул форму посещаемости")
        val fields = form.select("input[name], select[name], textarea[name]").flatMap { el ->
            val name = el.attr("name")
            when {
                el.hasAttr("disabled") -> emptyList()
                el.tagName() == "select" -> {
                    val options = el.select("option[selected]").ifEmpty {
                        if (el.hasAttr("multiple")) emptyList() else el.select("option").take(1)
                    }
                    options.map { name to it.attr("value") }
                }
                el.tagName() == "textarea" -> listOf(name to el.text())
                el.attr("type") in listOf("submit", "button", "file") -> emptyList()
                el.attr("type") in listOf("checkbox", "radio") && !el.hasAttr("checked") -> emptyList()
                else -> listOf(name to el.attr("value"))
            }
        }
        check(fields.any { it.first == "__RequestVerificationToken" && it.second.isNotBlank() }) {
            "Сессия формы истекла. Откройте занятие заново"
        }
        check(fields.firstOrNull { it.first == "ID" }?.second == lessonId) { "БАРС вернул другое занятие" }
        return LessonAttendance(lessonId, lessonStudents(html),
            fields.first { it.first == "AttendanceSheetLessonStudentListSerialized" }.second, fields)
    }

    fun updateAttendance(serialized: String, absentIds: Set<String>): String {
        val root = json.parseToJsonElement(serialized).jsonObject
        val sheets = root["AttendanceSheetLessonStudents"]!!.jsonArray.map { sheet ->
            val values = sheet.jsonObject.toMutableMap()
            values["Students"] = JsonArray(values["Students"]!!.jsonArray.map { item ->
                val student = item.jsonObject.toMutableMap()
                val studentId = student["StudentID"]!!.jsonPrimitive.content
                student["LessonSkipReasonID"] = if (studentId in absentIds) {
                    student["LessonSkipReasonID"]?.takeUnless { it is JsonNull } ?: JsonPrimitive(1)
                } else JsonNull
                JsonObject(student)
            })
            JsonObject(values)
        }
        return JsonObject(root.toMutableMap().apply {
            put("AttendanceSheetLessonStudents", JsonArray(sheets))
        }).toString()
    }

    fun hasNextPage(html: String): Boolean = Jsoup.parse(html).select(".pagination a[rel=next]").isNotEmpty()
}
