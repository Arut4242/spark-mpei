package ru.mpei.assistant.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.mpei.assistant.data.local.AppDatabase
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.repository.EmailRepository
import ru.mpei.assistant.util.AppLogger
import java.util.concurrent.TimeUnit

/**
 * Фоновый воркер для проверки почты ОСЭП МЭИ с базовым интервалом 1 час.
 * Использует легковесную проверку статуса папки (без загрузки тел), jitter 5-15 мин,
 * паузу для неактивных пользователей (>7 дней) и прекращает опросы при ошибках аутентификации.
 */
class EmailCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val storage = SessionStorage(context)
        val db = AppDatabase.getInstance(context)
        val now = System.currentTimeMillis()

        AppLogger.i("EMAIL_WORKER", "Запуск проверки почты ОСЭП МЭИ (базовый интервал 1 час)...")
        var shouldReschedule = true

        try {
            if (storage.isEmailNotificationsEnabled && storage.isOsepAuthorized) {
                // 1. Приостановка при неактивности пользователя (> 7 дней -> не чаще 1 раза в сутки)
                val lastOpen = storage.lastAppOpenTime
                val isInactiveUser = lastOpen > 0L && (now - lastOpen > 7L * 24 * 60 * 60 * 1000L)
                if (isInactiveUser) {
                    val lastSync = storage.lastEmailSyncTime
                    if (now - lastSync < 24L * 60 * 60 * 1000L) {
                        AppLogger.i(
                            "EMAIL_WORKER",
                            "Пользователь не открывал приложение >7 дней. Пропуск проверки почты (лимит 1 раз в сутки для неактивных)."
                        )
                        return Result.success()
                    }
                }

                val login = storage.osepLogin
                val pass = storage.osepPassword
                val mailClient = ru.mpei.assistant.data.network.OsepMailClient()

                // 2. Быстрая проверка заголовков без скачивания (unseenMessageCount / newest UID)
                if (!login.isNullOrBlank() && !pass.isNullOrBlank()) {
                    val statusRes = mailClient.checkInboxStatus(login, pass)
                    if (statusRes.isSuccess) {
                        val status = statusRes.getOrThrow()
                        val lastUid = storage.lastEmailNewestUid
                        val lastUnread = storage.lastEmailUnreadCount

                        if (status.newestUid > 0L && status.newestUid == lastUid && status.unseenCount == lastUnread) {
                            AppLogger.i(
                                "EMAIL_WORKER",
                                "Быстрая проверка IMAP: новых писем нет (newest UID=$lastUid, unseen=$lastUnread). Завершено без скачивания тел."
                            )
                            storage.lastEmailSyncTime = now
                            return Result.success()
                        }
                        storage.lastEmailNewestUid = status.newestUid
                        storage.lastEmailUnreadCount = status.unseenCount
                    } else {
                        val ex = statusRes.exceptionOrNull()
                        if (ex is javax.mail.AuthenticationFailedException) {
                            AppLogger.w("EMAIL_WORKER", "Неверный логин или пароль почты. Остановка фоновой работы во избежание блокировки.")
                            shouldReschedule = false
                            return Result.success()
                        }
                    }
                }

                // 3. Скачивание при наличии изменений
                val emailRepo = EmailRepository(
                    emailDao = db.emailDao(),
                    sessionStorage = storage,
                    mailClient = mailClient,
                    appContext = context
                )
                val result = emailRepo.refreshEmails(notifyOnNew = true, context = context)
                if (result.isSuccess) {
                    storage.lastEmailSyncTime = now
                    AppLogger.i("EMAIL_WORKER", "Проверка почты успешно завершена")
                } else {
                    val ex = result.exceptionOrNull()
                    AppLogger.w("EMAIL_WORKER", "Ошибка проверки почты: ${ex?.message}")
                    if (ex is javax.mail.AuthenticationFailedException) {
                        AppLogger.w("EMAIL_WORKER", "Неверный логин или пароль почты. Остановка фоновой работы во избежание блокировки.")
                        shouldReschedule = false
                    }
                }
            } else {
                AppLogger.d("EMAIL_WORKER", "Пропуск проверки почты: уведомления=${storage.isEmailNotificationsEnabled}, авторизован=${storage.isOsepAuthorized}")
            }
        } catch (e: Exception) {
            AppLogger.e("EMAIL_WORKER", "Исключение при фоновой проверке почты", e)
            if (e is javax.mail.AuthenticationFailedException) {
                AppLogger.w("EMAIL_WORKER", "Неверный логин или пароль почты. Остановка фоновой работы во избежание блокировки.")
                shouldReschedule = false
            }
        } finally {
            if (shouldReschedule) {
                scheduleNext(context)
            }
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "email_sync_work"
        const val INTERVAL_MINUTES = 60L // 1 час

        fun scheduleNext(context: Context) {
            val storage = SessionStorage(context)
            if (!storage.isEmailNotificationsEnabled || !storage.isOsepAuthorized) {
                AppLogger.d("EMAIL_WORKER", "Планирование отменено: уведомления выключены или нет сессии ОСЭП")
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Рандомизация смещения (Jitter 5..15 минут) во избежание thundering herd
            val jitterMinutes = (5L..15L).random()
            val totalDelay = INTERVAL_MINUTES + jitterMinutes

            val emailRequest = OneTimeWorkRequestBuilder<EmailCheckWorker>()
                .setInitialDelay(totalDelay, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                emailRequest
            )
            AppLogger.d("EMAIL_WORKER", "Следующая проверка почты запланирована через $totalDelay мин (базовый $INTERVAL_MINUTES + jitter $jitterMinutes мин)")
        }

        fun start(context: Context) {
            val storage = SessionStorage(context)
            if (storage.isEmailNotificationsEnabled && storage.isOsepAuthorized) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val jitterMinutes = (5L..15L).random()
                val totalDelay = INTERVAL_MINUTES + jitterMinutes

                val emailRequest = OneTimeWorkRequestBuilder<EmailCheckWorker>()
                    .setInitialDelay(totalDelay, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    emailRequest
                )
                AppLogger.d("EMAIL_WORKER", "Цепочка проверки почты запущена (интервал $totalDelay мин)")
            }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            AppLogger.d("EMAIL_WORKER", "Фоновая проверка почты отменена")
        }
    }
}
