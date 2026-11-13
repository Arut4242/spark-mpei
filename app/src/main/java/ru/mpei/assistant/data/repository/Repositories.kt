package ru.mpei.assistant.data.repository

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.Flow
import org.jsoup.Jsoup
import java.io.File
import java.net.URLEncoder
import ru.mpei.assistant.data.local.AbsenceDao
import ru.mpei.assistant.data.local.BarsDao
import ru.mpei.assistant.data.local.ScheduleDao
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.model.absence.SubjectAbsence
import ru.mpei.assistant.data.model.bars.BarsSubject
import ru.mpei.assistant.data.model.schedule.Lesson
import ru.mpei.assistant.data.model.schedule.SearchEntity
import ru.mpei.assistant.data.network.BarsWebClient
import ru.mpei.assistant.data.network.MpeiScheduleApi
import ru.mpei.assistant.data.parser.BarsAttendanceParser
import ru.mpei.assistant.data.parser.BarsHtmlParser
import ru.mpei.assistant.worker.NotificationHelper
import ru.mpei.assistant.worker.buildAbsenceNotification
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Stable
class BarsRepository(
    private val barsWebClient: BarsWebClient,
    private val barsDao: BarsDao,
    private val sessionStorage: SessionStorage
) {
    fun getSubjectsFlow(): Flow<List<BarsSubject>> = barsDao.getAllSubjects()

    suspend fun refreshGrades(semesterId: String? = null): Result<List<BarsSubject>> {
        ru.mpei.assistant.util.AppLogger.i("BARS_REPO", "refreshGrades started (semesterId=$semesterId)")
        val semId = semesterId ?: BarsAttendanceParser.estimateCurrentSemesterId()
        val semName = BarsAttendanceParser.formatSemesterName(semId)
        val htmlResult = barsWebClient.fetchGradesHtml(semesterId = semesterId)
        if (htmlResult.isFailure) {
            val err = htmlResult.exceptionOrNull() ?: Exception("Неизвестная ошибка")
            ru.mpei.assistant.util.AppLogger.e("BARS_REPO", "refreshGrades fetchHtml failed", err)
            return Result.failure(err)
        }

        val html = htmlResult.getOrThrow()
        val subjects = BarsHtmlParser.parseSubjects(html, semName)
        ru.mpei.assistant.util.AppLogger.i("BARS_REPO", "parseSubjects finished: found ${subjects.size} subjects ($semName)")

        if (subjects.isNotEmpty()) {
            barsDao.clearAll()
            barsDao.insertAll(subjects)
            ru.mpei.assistant.util.AppLogger.i("BARS_REPO", "Saved ${subjects.size} subjects to database")
        } else {
            ru.mpei.assistant.util.AppLogger.w("BARS_REPO", "Parsed 0 subjects from HTML")
        }

        return Result.success(subjects)
    }

    fun isAuthorized(): Boolean = !sessionStorage.barsCookies.isNullOrBlank()

    fun logout() {
        sessionStorage.clearSession()
    }
}

@Stable
class ScheduleRepository(
    private val api: MpeiScheduleApi,
    private val scheduleDao: ScheduleDao,
    private val sessionStorage: SessionStorage
) {
    fun getLessonsFlow(groupId: Long): Flow<List<Lesson>> =
        scheduleDao.getLessonsByGroup(groupId)

    suspend fun search(query: String, type: String = "group"): Result<List<SearchEntity>> {
        return try {
            val normalized = if (type == "group") normalizeQuery(query) else query.trim()
            ru.mpei.assistant.util.AppLogger.i("SCHEDULE", "search: query='$query', type='$type', normalized='$normalized'")
            var results = api.search(normalized, type = type)
            if (results.isEmpty() && type == "group") {
                // Fallback: search without type filter and filter on client
                val raw = api.search(normalized, type = "")
                results = raw.filter { it.type == "group" || it.description?.contains("Институт", ignoreCase = true) == true }
            }
            // Ensure type is preserved in items
            val typedResults = results.map { it.copy(type = it.type ?: type) }
            ru.mpei.assistant.util.AppLogger.i("SCHEDULE", "search: found ${typedResults.size} results for type='$type'")
            Result.success(typedResults)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.e("SCHEDULE", "search failed for query='$query', type='$type'", e)
            Result.failure(e)
        }
    }

    suspend fun searchGroups(query: String): Result<List<SearchEntity>> = search(query, type = "group")

    private fun normalizeQuery(query: String): String {
        return query
            .replace('A', 'А').replace('a', 'а')
            .replace('B', 'В')
            .replace('E', 'Е').replace('e', 'е')
            .replace('K', 'К')
            .replace('M', 'М')
            .replace('H', 'Н')
            .replace('O', 'О').replace('o', 'о')
            .replace('P', 'Р').replace('p', 'р')
            .replace('C', 'С').replace('c', 'с')
            .replace('T', 'Т')
            .replace('X', 'Х').replace('x', 'х')
    }

    suspend fun refreshSchedule(
        groupId: Long,
        force: Boolean = false,
        type: String = sessionStorage.selectedScheduleType
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            ru.mpei.assistant.util.AppLogger.i("SCHEDULE", "refreshSchedule started: targetId=$groupId, force=$force, type=$type")
            // If not forced and refreshed recently (within 30 minutes), skip network request
            if (!force && (now - sessionStorage.lastScheduleSyncTime < 30 * 60 * 1000L)) {
                ru.mpei.assistant.util.AppLogger.i("SCHEDULE", "Schedule cache is fresh (< 30 min), skipping network")
                return Result.success(Unit)
            }

            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) // 0-based: 8 = Sep, 0 = Jan

            val (startDate, finishDate) = if (month >= Calendar.SEPTEMBER) {
                Pair("$year.09.01", "${year + 1}.01.31")
            } else if (month <= Calendar.JANUARY) {
                Pair("${year - 1}.09.01", "$year.01.31")
            } else {
                Pair("$year.02.01", "$year.07.15")
            }

            val rawLessons = when (type.lowercase()) {
                "person" -> api.getPersonSchedule(groupId, startDate, finishDate)
                "auditorium" -> api.getAuditoriumSchedule(groupId, startDate, finishDate)
                else -> api.getSchedule(groupId, startDate, finishDate)
            }
            ru.mpei.assistant.util.AppLogger.i("SCHEDULE", "api schedule returned ${rawLessons.size} lessons for type=$type")
            val lessonsWithGroup = rawLessons.map { it.toLesson(groupId) }

            if (lessonsWithGroup.isNotEmpty()) {
                scheduleDao.replaceLessonsForGroup(groupId, lessonsWithGroup)
                sessionStorage.lastScheduleSyncTime = now
                ru.mpei.assistant.util.AppLogger.i("SCHEDULE", "Inserted ${lessonsWithGroup.size} lessons into DB")
            } else {
                ru.mpei.assistant.util.AppLogger.w("SCHEDULE", "API returned 0 lessons for targetId=$groupId (type=$type)")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.e("SCHEDULE", "refreshSchedule failed for targetId=$groupId", e)
            Result.failure(e)
        }
    }

    suspend fun hasCachedLessons(groupId: Long): Boolean {
        return try {
            scheduleDao.getLessonsCount(groupId) > 0
        } catch (_: Exception) {
            false
        }
    }
}

@Stable
class AbsenceRepository(
    private val absenceDao: AbsenceDao,
    private val barsDao: BarsDao,
    private val scheduleDao: ScheduleDao,
    private val barsWebClient: BarsWebClient? = null,
    private val cacheDir: File? = null
) {
    fun getAbsencesFlow(): Flow<List<SubjectAbsence>> = absenceDao.getAllAbsences()

    fun isAuthorizedInBars(): Boolean = barsWebClient?.isAuthorized() == true

    suspend fun incrementUnexcused(subjectName: String) {
        val current = absenceDao.getAbsenceForSubject(subjectName) ?: SubjectAbsence(subjectName)
        absenceDao.insertOrUpdate(current.copy(unexcusedPairs = current.unexcusedPairs + 1))
    }

    suspend fun decrementUnexcused(subjectName: String) {
        val current = absenceDao.getAbsenceForSubject(subjectName) ?: return
        if (current.unexcusedPairs > 0) {
            absenceDao.insertOrUpdate(current.copy(unexcusedPairs = current.unexcusedPairs - 1))
        }
    }

    suspend fun incrementExcused(subjectName: String) {
        val current = absenceDao.getAbsenceForSubject(subjectName) ?: SubjectAbsence(subjectName)
        absenceDao.insertOrUpdate(current.copy(excusedPairs = current.excusedPairs + 1))
    }

    suspend fun decrementExcused(subjectName: String) {
        val current = absenceDao.getAbsenceForSubject(subjectName) ?: return
        if (current.excusedPairs > 0) {
            absenceDao.insertOrUpdate(current.copy(excusedPairs = current.excusedPairs - 1))
        }
    }

    suspend fun updateTotalSemesterPairs(subjectName: String, totalPairs: Int) {
        val validTotal = totalPairs.coerceAtLeast(1)
        val current = absenceDao.getAbsenceForSubject(subjectName) ?: SubjectAbsence(subjectName)
        absenceDao.insertOrUpdate(current.copy(totalSemesterPairs = validTotal))
    }

    suspend fun addCustomSubject(subjectName: String, totalPairs: Int = 16) {
        val trimmed = subjectName.trim()
        if (trimmed.isEmpty()) return
        val existing = absenceDao.getAbsenceForSubject(trimmed)
        if (existing == null) {
            absenceDao.insertOrUpdate(
                SubjectAbsence(
                    subjectName = trimmed,
                    unexcusedPairs = 0,
                    excusedPairs = 0,
                    totalSemesterPairs = totalPairs.coerceAtLeast(1)
                )
            )
        }
    }

    suspend fun deleteSubject(subjectName: String) {
        absenceDao.delete(subjectName)
    }

    suspend fun resetSubjectAbsences(subjectName: String) {
        val current = absenceDao.getAbsenceForSubject(subjectName) ?: return
        absenceDao.insertOrUpdate(current.copy(unexcusedPairs = 0, excusedPairs = 0))
    }

    /**
     * Автоматически синхронизирует список предметов из БАРС и локального расписания.
     * Не перезаписывает уже учтенные пользователем пропуски.
     */
    /**
     * Автоматически загружает из БАРС:
     * 1. Реальное количество пар по дисциплине за семестр.
     * 2. Количество пропущенных пар без уважительной причины.
     * 3. Количество пропущенных пар по уважительной причине.
     *
     * Если сеть недоступна или сессия истекла, сохраняет локальные данные и возвращает результат.
     */
    suspend fun syncWithBarsAndSchedule(
        notifyOnNew: Boolean = false,
        context: Context? = null
    ): Result<Unit> {
        val barsClient = barsWebClient
        if (barsClient == null || !barsClient.isAuthorized()) {
            ru.mpei.assistant.util.AppLogger.i("ABSENCE_REPO", "syncWithBarsAndSchedule: BARS not authorized, using local DB")
            syncFromLocalBarsDb()
            return Result.success(Unit)
        }

        return try {
            ru.mpei.assistant.util.AppLogger.i("ABSENCE_REPO", "syncWithBarsAndSchedule started")
            val mainHtmlRes = barsClient.fetchGradesHtml()
            if (mainHtmlRes.isFailure) {
                syncFromLocalBarsDb()
                val err = mainHtmlRes.exceptionOrNull() ?: Exception("Ошибка загрузки БАРС")
                ru.mpei.assistant.util.AppLogger.e("ABSENCE_REPO", "Failed to fetch BARS main study page", err)
                return Result.failure(err)
            }

            val mainHtml = mainHtmlRes.getOrThrow()
            val mainDoc = Jsoup.parse(mainHtml)
            val studentId = barsClient.resolveStudentId()
            val attendanceLink = mainDoc.select("a").firstOrNull {
                it.text().contains("Посещение", ignoreCase = true) ||
                it.attr("href").contains("LessonStudent", ignoreCase = true) ||
                it.attr("href").contains("Attendance", ignoreCase = true)
            }

            var attMeta = if (attendanceLink != null) {
                var attUrl = attendanceLink.attr("href")
                if (!attUrl.startsWith("http")) {
                    attUrl = if (attUrl.startsWith("/")) "https://bars.mpei.ru$attUrl" else "https://bars.mpei.ru/bars_web/$attUrl"
                }
                ru.mpei.assistant.util.AppLogger.i("ABSENCE_REPO", "Found attendance link: $attUrl")
                val attPageRes = barsClient.fetchHtml(attUrl)
                if (attPageRes.isSuccess) {
                    BarsAttendanceParser.extractMeta(attPageRes.getOrThrow())
                } else null
            } else null

            val actualStudentId = attMeta?.studentId ?: studentId
            if (actualStudentId.isNullOrBlank()) {
                ru.mpei.assistant.util.AppLogger.w("ABSENCE_REPO", "No studentId found for attendance sync")
                syncFromLocalBarsDb()
                return Result.success(Unit)
            }

            val semId = attMeta?.semesterId ?: BarsAttendanceParser.estimateCurrentSemesterId()

            // 1. Все занятия семестра (для подсчета общего числа пар по предметам)
            val allLessonsUrl = attMeta?.buildQueryUrl(skipType = "*", skipReason = "*", pageSize = 500)
                ?: BarsAttendanceParser.buildAttendanceUrl(actualStudentId, semId, skipType = "*", skipReason = "*", pageSize = 500)
            val allLessonsRes = barsClient.fetchHtml(allLessonsUrl)
            val totalPairsMap = if (allLessonsRes.isSuccess) {
                BarsAttendanceParser.parseDisciplineCounts(allLessonsRes.getOrThrow())
            } else emptyMap()

            // 2. Пропуски без уважительной причины (влияют на автомат)
            val unexcusedUrl = attMeta?.buildQueryUrl(skipType = "1", skipReason = "1", pageSize = 500)
                ?: BarsAttendanceParser.buildAttendanceUrl(actualStudentId, semId, skipType = "1", skipReason = "1", pageSize = 500)
            val unexcusedRes = barsClient.fetchHtml(unexcusedUrl)
            val unexcusedMap = if (unexcusedRes.isSuccess) {
                BarsAttendanceParser.parseDisciplineCounts(unexcusedRes.getOrThrow())
            } else emptyMap()

            // 3. Пропуски по уважительной причине (по справке)
            val excusedUrl = attMeta?.buildQueryUrl(skipType = "1", skipReason = "2", pageSize = 500)
                ?: BarsAttendanceParser.buildAttendanceUrl(actualStudentId, semId, skipType = "1", skipReason = "2", pageSize = 500)
            val excusedRes = barsClient.fetchHtml(excusedUrl)
            val excusedMap = if (excusedRes.isSuccess) {
                BarsAttendanceParser.parseDisciplineCounts(excusedRes.getOrThrow())
            } else emptyMap()

            ru.mpei.assistant.util.AppLogger.i("BARS_SYNC", "Syncing parsed attendance from BARS:")
            ru.mpei.assistant.util.AppLogger.i("BARS_SYNC", "  totalPairsMap: $totalPairsMap")
            ru.mpei.assistant.util.AppLogger.i("BARS_SYNC", "  unexcusedMap: $unexcusedMap")
            ru.mpei.assistant.util.AppLogger.i("BARS_SYNC", "  excusedMap: $excusedMap")

            // 4. Формируем единый набор дисциплин
            val allSubjectNames = mutableSetOf<String>()
            allSubjectNames.addAll(totalPairsMap.keys)
            allSubjectNames.addAll(unexcusedMap.keys)
            allSubjectNames.addAll(excusedMap.keys)

            val localBarsSubjects = barsDao.getAllSubjectsList()
            val controlTypeMap = localBarsSubjects.associate { it.name.trim().lowercase() to it.controlType }
            localBarsSubjects.forEach {
                allSubjectNames.add(it.name.trim())
            }

            for (subjectName in allSubjectNames) {
                if (subjectName.isBlank()) continue
                val totalFromBars = totalPairsMap[subjectName]
                val unexcusedFromBars = unexcusedMap[subjectName] ?: 0
                val excusedFromBars = excusedMap[subjectName] ?: 0
                val subjectControlType = controlTypeMap[subjectName.trim().lowercase()] ?: ""

                val existing = absenceDao.getAbsenceForSubject(subjectName)
                if (existing != null) {
                    val diffUnexcused = (unexcusedFromBars - existing.unexcusedPairs).coerceAtLeast(0)
                    val diffExcused = (excusedFromBars - existing.excusedPairs).coerceAtLeast(0)

                    val updated = existing.copy(
                        totalSemesterPairs = totalFromBars ?: existing.totalSemesterPairs,
                        unexcusedPairs = unexcusedFromBars,
                        excusedPairs = excusedFromBars,
                        controlType = if (subjectControlType.isNotEmpty()) subjectControlType else existing.controlType
                    )
                    absenceDao.insertOrUpdate(updated)

                    // Оповещаем пользователя только если есть новые пропуски и передан context
                    if (notifyOnNew && context != null && (diffUnexcused > 0 || diffExcused > 0)) {
                        val notifData = buildAbsenceNotification(
                            subjectName = subjectName,
                            diffUnexcused = diffUnexcused,
                            diffExcused = diffExcused,
                            newAbsence = updated
                        )
                        if (notifData != null) {
                            NotificationHelper.showAbsenceNotification(
                                context = context,
                                id = (subjectName.hashCode() and 0x7FFFFFFF) + 2000,
                                title = notifData.title,
                                text = notifData.text,
                                isWarning = notifData.isWarning
                            )
                        }
                    }
                } else {
                    absenceDao.insertOrUpdate(
                        SubjectAbsence(
                            subjectName = subjectName,
                            totalSemesterPairs = totalFromBars ?: 16,
                            unexcusedPairs = unexcusedFromBars,
                            excusedPairs = excusedFromBars,
                            controlType = subjectControlType
                        )
                    )
                }
            }

            // Очищаем ошибочные записи вида "часть 2", оставшиеся от старого разделения по запятым
            val corruptedAbsences = absenceDao.getAllAbsencesList().filter {
                it.subjectName.matches(Regex("""^(?:часть|ч\.?|модуль|раздел)\s*\d+.*""", RegexOption.IGNORE_CASE))
            }
            for (corrupted in corruptedAbsences) {
                absenceDao.delete(corrupted.subjectName)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("BARS_ATTENDANCE", "Error syncing attendance", e)
            syncFromLocalBarsDb()
            Result.failure(e)
        }
    }

    private suspend fun syncFromLocalBarsDb() {
        val barsSubjects = barsDao.getAllSubjectsList()
        val newEntries = mutableListOf<SubjectAbsence>()
        barsSubjects.forEach { barsSubject ->
            val name = barsSubject.name.trim()
            if (name.isNotEmpty() && absenceDao.getAbsenceForSubject(name) == null) {
                newEntries.add(
                    SubjectAbsence(
                        subjectName = name,
                        totalSemesterPairs = 16,
                        controlType = barsSubject.controlType
                    )
                )
            }
        }
        if (newEntries.isNotEmpty()) {
            absenceDao.insertAllIgnore(newEntries)
        }

        // Очищаем ошибочные записи вида "часть 2", оставшиеся от старого разделения по запятым
        val corruptedAbsences = absenceDao.getAllAbsencesList().filter {
            it.subjectName.matches(Regex("""^(?:часть|ч\.?|модуль|раздел)\s*\d+.*""", RegexOption.IGNORE_CASE))
        }
        for (corrupted in corruptedAbsences) {
            absenceDao.delete(corrupted.subjectName)
        }
    }
}
