package ru.mpei.assistant.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.mpei.assistant.data.local.AppDatabase
import ru.mpei.assistant.data.model.schedule.LessonNote
import ru.mpei.assistant.util.AppLogger
import java.text.SimpleDateFormat
import java.util.Locale

object LessonReminderScheduler {

    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_DISCIPLINE = "extra_discipline"
    const val EXTRA_BEGIN_TIME = "extra_begin_time"
    const val EXTRA_AUDITORIUM = "extra_auditorium"
    const val EXTRA_NOTE_TEXT = "extra_note_text"
    const val EXTRA_MINUTES_BEFORE = "extra_minutes_before"

    fun calculateReminderTimeMillis(date: String, beginLesson: String, minutesBefore: Int): Long? {
        return try {
            val format = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
            val lessonStartTime = format.parse("$date $beginLesson") ?: return null
            lessonStartTime.time - (minutesBefore * 60 * 1000L)
        } catch (e: Exception) {
            null
        }
    }

    fun scheduleReminder(
        context: Context,
        note: LessonNote,
        discipline: String,
        auditorium: String?
    ) {
        val triggerTime = note.reminderTimeMillis
            ?: (note.reminderMinutesBefore?.let { calculateReminderTimeMillis(note.date, note.beginLesson, it) })
            ?: return

        if (triggerTime <= System.currentTimeMillis()) {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val requestCode = generateRequestCode(note.groupId, note.date, note.beginLesson)
        val minutesBefore = note.reminderMinutesBefore ?: -1

        val intent = Intent(context, LessonReminderReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_ID, requestCode)
            putExtra(EXTRA_DISCIPLINE, discipline)
            putExtra(EXTRA_BEGIN_TIME, note.beginLesson)
            putExtra(EXTRA_AUDITORIUM, auditorium)
            putExtra(EXTRA_NOTE_TEXT, note.text)
            putExtra(EXTRA_MINUTES_BEFORE, minutesBefore)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            AppLogger.d("REMINDER", "Scheduled reminder for $discipline at $triggerTime ($minutesBefore min before)")
        } catch (e: Exception) {
            AppLogger.e("REMINDER", "Failed to schedule reminder", e)
        }
    }

    fun cancelReminder(context: Context, groupId: Long, date: String, beginLesson: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val requestCode = generateRequestCode(groupId, date, beginLesson)
        val intent = Intent(context, LessonReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            AppLogger.d("REMINDER", "Cancelled reminder for $date $beginLesson")
        }
    }

    fun generateRequestCode(groupId: Long, date: String, beginLesson: String): Int {
        return ("${groupId}_${date}_${beginLesson}".hashCode() and 0x7FFFFFFF)
    }
}

class LessonReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val futureNotes = db.scheduleDao().getFutureReminders(System.currentTimeMillis())
                    for (note in futureNotes) {
                        LessonReminderScheduler.scheduleReminder(
                            context = context,
                            note = note,
                            discipline = note.discipline,
                            auditorium = null
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e("REMINDER", "Failed to restore reminders after boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val notificationId = intent.getIntExtra(LessonReminderScheduler.EXTRA_NOTIFICATION_ID, 1001)
        val discipline = intent.getStringExtra(LessonReminderScheduler.EXTRA_DISCIPLINE) ?: "Занятие"
        val beginTime = intent.getStringExtra(LessonReminderScheduler.EXTRA_BEGIN_TIME) ?: ""
        val auditorium = intent.getStringExtra(LessonReminderScheduler.EXTRA_AUDITORIUM)
        val noteText = intent.getStringExtra(LessonReminderScheduler.EXTRA_NOTE_TEXT) ?: ""
        val minutesBefore = intent.getIntExtra(LessonReminderScheduler.EXTRA_MINUTES_BEFORE, 15)

        NotificationHelper.showLessonReminderNotification(
            context = context,
            id = notificationId,
            discipline = discipline,
            beginTime = beginTime,
            auditorium = auditorium,
            noteText = noteText,
            minutesBefore = minutesBefore
        )
    }
}
