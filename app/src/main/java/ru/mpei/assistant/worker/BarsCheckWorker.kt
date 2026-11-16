package ru.mpei.assistant.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.serialization.json.Json
import ru.mpei.assistant.data.local.AppDatabase
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.model.bars.ControlMeasure
import ru.mpei.assistant.data.network.BarsWebClient
import ru.mpei.assistant.data.network.MpeiScheduleApi
import ru.mpei.assistant.data.parser.BarsHtmlParser
import ru.mpei.assistant.data.repository.AbsenceRepository
import ru.mpei.assistant.data.repository.ScheduleRepository
import ru.mpei.assistant.security.KeystoreCrypto

class BarsCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val storage = SessionStorage(context)
        val db = AppDatabase.getInstance(context)
        val now = System.currentTimeMillis()

        // 1. Ограничение по учебному времени и ночной режим
        // Обновление разрешено только с 05:00 до 09:30 утра и с 15:00 дня до 03:00 ночи
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val totalMinutes = hour * 60 + minute
        val isAllowedTimeWindow = (totalMinutes in 300..570) || (totalMinutes >= 900 || totalMinutes < 180)
        if (!isAllowedTimeWindow) {
            ru.mpei.assistant.util.AppLogger.i(
                "BARS_CHECK_WORKER",
                "Фоновое обновление БАРС пропущено: вне разрешённого интервала (05:00-09:30, 15:00-03:00). Текущее время: %02d:%02d".format(hour, minute)
            )
            return Result.success()
        }

        // 2. Приостановка при неактивности пользователя (> 7 дней -> не чаще 1 раза в сутки)
        val lastAppOpen = storage.lastAppOpenTime
        val isInactiveUser = lastAppOpen > 0L && (now - lastAppOpen > 7L * 24 * 60 * 60 * 1000L)
        if (isInactiveUser) {
            val lastSync = storage.lastBarsSyncTime
            if (now - lastSync < 24L * 60 * 60 * 1000L) {
                ru.mpei.assistant.util.AppLogger.i(
                    "BARS_CHECK_WORKER",
                    "Пользователь не открывал приложение >7 дней. Пропуск фоновой проверки (лимит 1 раз в сутки для неактивных)."
                )
                return Result.success()
            }
        }

        // 3. Пауза при недавних ошибках сервера БАРС (экспоненциальная защита сервера)
        val lastError = storage.lastBarsErrorTime
        if (now - lastError < 15L * 60 * 1000L) {
            ru.mpei.assistant.util.AppLogger.w(
                "BARS_CHECK_WORKER",
                "Сервер БАРС недавно возвращал ошибку. Пауза перед повторным запросом во избежание перегрузки."
            )
            return Result.success()
        }

        // 4. Оптимизация расписания: проверка не чаще 1 раза в сутки и без принудительного сброса кэша
        val selectedGroupId = storage.selectedGroupId
        if (selectedGroupId != -1L) {
            try {
                val scheduleApi = MpeiScheduleApi.create()
                val scheduleRepo = ScheduleRepository(scheduleApi, db.scheduleDao(), storage)
                val hasCache = scheduleRepo.hasCachedLessons(selectedGroupId)
                val lastScheduleSync = storage.lastScheduleSyncTime
                if (!hasCache || (now - lastScheduleSync > 24L * 60 * 60 * 1000L)) {
                    scheduleRepo.refreshSchedule(selectedGroupId, force = false)
                }
            } catch (_: Exception) {}
        }

        val checkGrades = storage.isNotificationsEnabled && !storage.barsCookies.isNullOrBlank()
        val checkAbsences = storage.isAbsenceNotificationsEnabled && !storage.barsCookies.isNullOrBlank()

        if (!checkGrades && !checkAbsences) {
            return Result.success()
        }

        val client = BarsWebClient(storage)
        var anySuccess = false
        var anyFailure = false
        var isAuthError = false

        // 5. Проверка новых оценок
        if (checkGrades) {
            val htmlResult = client.fetchGradesHtml()
            if (htmlResult.isFailure) {
                anyFailure = true
                storage.lastBarsErrorTime = now
                val ex = htmlResult.exceptionOrNull()
                if (ex is IllegalStateException && ex.message?.contains("сессия", ignoreCase = true) == true) {
                    isAuthError = true
                }
            } else {
                anySuccess = true
                val html = htmlResult.getOrThrow()
                val semId = ru.mpei.assistant.data.parser.BarsAttendanceParser.estimateCurrentSemesterId()
                val semName = ru.mpei.assistant.data.parser.BarsAttendanceParser.formatSemesterName(semId)
                val newSubjects = BarsHtmlParser.parseSubjects(html, semName)

                // Читаем предыдущие оценки из БД
                val oldSubjects = db.barsDao().getAllSubjectsList()
                val oldScoresMap = mutableMapOf<String, String?>()
                for (sub in oldSubjects) {
                    try {
                        val decryptedJson = KeystoreCrypto.decrypt(sub.controlMeasuresJson) ?: sub.controlMeasuresJson
                        val measures: List<ControlMeasure> = json.decodeFromString(decryptedJson)
                        for (m in measures) {
                            if (!m.grade.isNullOrBlank()) {
                                oldScoresMap["${sub.name}_${m.name}"] = m.grade
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Сравниваем изменения и отправляем уведомления о новых оценках
                for (newSub in newSubjects) {
                    val newMeasures: List<ControlMeasure> = try {
                        json.decodeFromString(newSub.controlMeasuresJson)
                    } catch (e: Exception) {
                        emptyList()
                    }

                    for (measure in newMeasures) {
                        if (!measure.grade.isNullOrBlank()) {
                            val key = "${newSub.name}_${measure.name}"
                            val oldGrade = oldScoresMap[key]
                            if (oldGrade == null && oldScoresMap.isNotEmpty()) {
                                NotificationHelper.showGradeNotification(
                                    context = context,
                                    id = key.hashCode(),
                                    title = "Новая оценка: ${newSub.name}",
                                    text = "${measure.name}: получена оценка ${measure.grade}"
                                )
                            }
                        }
                    }
                }

                if (newSubjects.isNotEmpty()) {
                    val encryptedSubjects = newSubjects.map {
                        val encMeasures = if (it.controlMeasuresJson.isNotEmpty() && it.controlMeasuresJson != "[]") {
                            KeystoreCrypto.encrypt(it.controlMeasuresJson) ?: ""
                        } else it.controlMeasuresJson
                        it.copy(controlMeasuresJson = encMeasures)
                    }
                    db.barsDao().replaceAll(encryptedSubjects)
                }
                storage.lastBarsSyncTime = now
            }
        }

        // 2. Проверка новых пропусков и расчёт правила 6,25%
        if (checkAbsences) {
            try {
                val absenceRepo = AbsenceRepository(
                    absenceDao = db.absenceDao(),
                    barsDao = db.barsDao(),
                    scheduleDao = db.scheduleDao(),
                    barsWebClient = client,
                    cacheDir = context.cacheDir
                )
                val syncRes = absenceRepo.syncWithBarsAndSchedule(notifyOnNew = true, context = context)
                if (syncRes.isSuccess) {
                    anySuccess = true
                } else {
                    anyFailure = true
                    storage.lastBarsErrorTime = now
                }
            } catch (e: Exception) {
                anyFailure = true
                storage.lastBarsErrorTime = now
            }
        }

        if (isAuthError) {
            ru.mpei.assistant.util.AppLogger.w("BARS_CHECK_WORKER", "Сессия БАРС истекла. Остановка повторных попыток до входа пользователя.")
            return Result.success()
        }

        return if (!anySuccess && anyFailure) Result.retry() else Result.success()
    }
}
