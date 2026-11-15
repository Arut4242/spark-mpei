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

        // Silently update schedule in background for selected group
        val selectedGroupId = storage.selectedGroupId
        if (selectedGroupId != -1L) {
            try {
                val scheduleApi = MpeiScheduleApi.create()
                val scheduleRepo = ScheduleRepository(scheduleApi, db.scheduleDao(), storage)
                scheduleRepo.refreshSchedule(selectedGroupId, force = true)
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

        // 1. Проверка новых оценок
        if (checkGrades) {
            val htmlResult = client.fetchGradesHtml()
            if (htmlResult.isFailure) {
                anyFailure = true
                val ex = htmlResult.exceptionOrNull()
                if (ex is IllegalStateException && ex.message?.contains("сессия", ignoreCase = true) == true) {
                    isAuthError = true
                }
            } else {
                anySuccess = true
                val html = htmlResult.getOrThrow()
                val newSubjects = BarsHtmlParser.parseSubjects(html, "Текущий семестр")

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
                            KeystoreCrypto.encrypt(it.controlMeasuresJson) ?: it.controlMeasuresJson
                        } else it.controlMeasuresJson
                        it.copy(controlMeasuresJson = encMeasures)
                    }
                    db.barsDao().insertAll(encryptedSubjects)
                }
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
                }
            } catch (e: Exception) {
                anyFailure = true
            }
        }

        if (isAuthError) {
            ru.mpei.assistant.util.AppLogger.w("BARS_CHECK_WORKER", "Сессия БАРС истекла. Остановка повторных попыток до входа пользователя.")
            return Result.success()
        }

        return if (!anySuccess && anyFailure) Result.retry() else Result.success()
    }
}
