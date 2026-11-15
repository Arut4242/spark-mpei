package ru.mpei.assistant.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import ru.mpei.assistant.MainActivity
import ru.mpei.assistant.data.model.absence.SubjectAbsence

data class AbsenceNotificationData(
    val title: String,
    val text: String,
    val isWarning: Boolean
)

/**
 * Чистая функция для формирования текста уведомления о новых пропусках и статусе автомата (6,25%).
 */
fun buildAbsenceNotification(
    subjectName: String,
    diffUnexcused: Int,
    diffExcused: Int,
    newAbsence: SubjectAbsence
): AbsenceNotificationData? {
    if (diffUnexcused <= 0 && diffExcused <= 0) return null

    if (diffUnexcused > 0) {
        val title = "Новый пропуск: $subjectName"
        val remaining = newAbsence.remainingAllowedPairs
        val maxAllowed = newAbsence.maxAllowedUnexcusedPairs
        val totalUnexcused = newAbsence.unexcusedPairs

        val text = when {
            !newAbsence.isAutomatAllowedByAbsences ->
                "Зафиксирован пропуск без ув. причины (+$diffUnexcused). Лимит 6,25% превышен ($totalUnexcused из макс. $maxAllowed пар). Автомат утерян!"
            remaining == 0 ->
                "Зафиксирован пропуск (+$diffUnexcused). Вы на грани лимита 6,25% ($totalUnexcused из макс. $maxAllowed пар)! Больше пропускать нельзя."
            else ->
                "Зафиксирован пропуск (+$diffUnexcused). Всего пропусков: $totalUnexcused. До потери автомата осталось: $remaining пар(ы)."
        }

        return AbsenceNotificationData(
            title = title,
            text = text,
            isWarning = !newAbsence.isAutomatAllowedByAbsences || remaining == 0
        )
    } else {
        val title = "Уважительный пропуск: $subjectName"
        val text = "В БАРС внесён пропуск по справке/уваж. причине (+$diffExcused). Всего: ${newAbsence.excusedPairs}. На автомат не влияет."
        return AbsenceNotificationData(
            title = title,
            text = text,
            isWarning = false
        )
    }
}

object NotificationHelper {

    const val CHANNEL_GRADES = "channel_bars_grades"
    const val CHANNEL_ABSENCES = "channel_bars_absences"
    const val CHANNEL_EMAILS = "channel_mpei_emails"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val gradesChannel = NotificationChannel(
                CHANNEL_GRADES,
                "Оценки и БАРС",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о выставлении новых оценок и баллов за КМ"
            }

            val absencesChannel = NotificationChannel(
                CHANNEL_ABSENCES,
                "Пропуски и автомат",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых пропусках в БАРС и риске потери автомата (6,25%)"
            }

            val emailsChannel = NotificationChannel(
                CHANNEL_EMAILS,
                "Почта ОСЭП МЭИ",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых письмах из деканата и от преподавателей"
            }

            manager.createNotificationChannel(gradesChannel)
            manager.createNotificationChannel(absencesChannel)
            manager.createNotificationChannel(emailsChannel)
        }
    }

    fun showGradeNotification(context: Context, id: Int, title: String, text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "bars")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_GRADES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(id, notification)
    }

    fun showAbsenceNotification(
        context: Context,
        id: Int,
        title: String,
        text: String,
        isWarning: Boolean = false
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "absences")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = if (isWarning) android.R.drawable.ic_dialog_alert else android.R.drawable.ic_dialog_info

        val notification = NotificationCompat.Builder(context, CHANNEL_ABSENCES)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(id, notification)
    }

    fun showEmailNotification(
        context: Context,
        id: Int,
        sender: String,
        subject: String,
        snippet: String
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "email")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Новое письмо: $sender"
        val body = if (snippet.isNotBlank()) "$subject\n$snippet" else subject

        val notification = NotificationCompat.Builder(context, CHANNEL_EMAILS)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(subject)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(id, notification)
    }
}
