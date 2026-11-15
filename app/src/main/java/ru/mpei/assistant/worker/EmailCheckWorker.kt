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
 * Фоновый воркер для проверки почты ОСЭП МЭИ с интервалом 30 минут.
 * Прекращает фоновые опросы при неудачной аутентификации для защиты от блокировки аккаунта.
 */
class EmailCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val storage = SessionStorage(context)
        val db = AppDatabase.getInstance(context)

        AppLogger.i("EMAIL_WORKER", "Запуск проверки почты ОСЭП МЭИ (интервал 30 мин)...")
        var shouldReschedule = true

        try {
            if (storage.isEmailNotificationsEnabled && storage.isOsepAuthorized) {
                val emailRepo = EmailRepository(
                    emailDao = db.emailDao(),
                    sessionStorage = storage,
                    appContext = context
                )
                val result = emailRepo.refreshEmails(notifyOnNew = true, context = context)
                if (result.isSuccess) {
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
        const val INTERVAL_MINUTES = 30L

        fun scheduleNext(context: Context) {
            val storage = SessionStorage(context)
            if (!storage.isEmailNotificationsEnabled || !storage.isOsepAuthorized) {
                AppLogger.d("EMAIL_WORKER", "Планирование отменено: уведомления выключены или нет сессии ОСЭП")
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val emailRequest = OneTimeWorkRequestBuilder<EmailCheckWorker>()
                .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                emailRequest
            )
            AppLogger.d("EMAIL_WORKER", "Следующая проверка почты запланирована через $INTERVAL_MINUTES мин")
        }

        fun start(context: Context) {
            val storage = SessionStorage(context)
            if (storage.isEmailNotificationsEnabled && storage.isOsepAuthorized) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val emailRequest = OneTimeWorkRequestBuilder<EmailCheckWorker>()
                    .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    emailRequest
                )
                AppLogger.d("EMAIL_WORKER", "Цепочка проверки почты запущена (интервал $INTERVAL_MINUTES мин)")
            }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            AppLogger.d("EMAIL_WORKER", "Фоновая проверка почты отменена")
        }
    }
}
