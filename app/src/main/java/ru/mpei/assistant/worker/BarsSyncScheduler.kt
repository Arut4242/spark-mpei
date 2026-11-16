package ru.mpei.assistant.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BarsSyncScheduler {
    const val WORK_NAME = "bars_sync_work"
    const val DEFAULT_INTERVAL_MINUTES = 120L // 2 часа (по умолчанию)

    val AVAILABLE_INTERVALS = listOf(
        30L,   // 30 минут (Повышенная нагрузка)
        60L,   // 1 час
        120L,  // 2 часа (по умолчанию)
        180L,  // 3 часа (Рекомендуемый)
        360L,  // 6 часов (Энергосберегающий)
        720L   // 12 часов
    )

    fun schedule(context: Context, intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES) {
        // Ограничение Android WorkManager: минимальный период 15 минут
        val validInterval = intervalMinutes.coerceAtLeast(15L)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Рандомизация начального смещения (Jitter 5..15 минут) во избежание эффекта thundering herd
        val jitterMinutes = (5L..15L).random()

        val syncRequest = PeriodicWorkRequestBuilder<BarsCheckWorker>(validInterval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(jitterMinutes, TimeUnit.MINUTES)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    fun isHighRisk(minutes: Long): Boolean = minutes < 60L

    fun getIntervalLabel(minutes: Long): String {
        return when (minutes) {
            15L -> "15 минут (Высокий риск блокировки)"
            30L -> "30 минут (Повышенная нагрузка)"
            60L -> "1 час"
            120L -> "2 часа (По умолчанию)"
            180L -> "3 часа (Рекомендуемый)"
            360L -> "6 часов (Энергосберегающий)"
            720L -> "12 часов"
            else -> "$minutes мин"
        }
    }

    fun getShortLabel(minutes: Long): String {
        return when (minutes) {
            15L -> "15 минут ⚠️"
            30L -> "30 минут ⚠️"
            60L -> "1 час"
            120L -> "2 часа"
            180L -> "3 часа (Реком.)"
            360L -> "6 часов"
            720L -> "12 часов"
            else -> "$minutes мин"
        }
    }
}
