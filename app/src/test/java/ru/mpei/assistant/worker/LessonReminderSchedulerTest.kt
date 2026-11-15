package ru.mpei.assistant.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mpei.assistant.ui.schedule.parseCustomDateTime
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LessonReminderSchedulerTest {

    @Test
    fun testCalculateReminderTimeMillis() {
        val date = "2026.11.16"
        val beginLesson = "11:10"

        // 15 minutes before 11:10 should be 10:55
        val time15 = LessonReminderScheduler.calculateReminderTimeMillis(date, beginLesson, 15)
        assertNotNull(time15)
        val cal15 = Calendar.getInstance().apply { timeInMillis = time15!! }
        assertEquals(10, cal15.get(Calendar.HOUR_OF_DAY))
        assertEquals(55, cal15.get(Calendar.MINUTE))

        // 1 hour (60 min) before 11:10 should be 10:10
        val time60 = LessonReminderScheduler.calculateReminderTimeMillis(date, beginLesson, 60)
        assertNotNull(time60)
        val cal60 = Calendar.getInstance().apply { timeInMillis = time60!! }
        assertEquals(10, cal60.get(Calendar.HOUR_OF_DAY))
        assertEquals(10, cal60.get(Calendar.MINUTE))

        // 2 hours (120 min) before 11:10 should be 09:10
        val time120 = LessonReminderScheduler.calculateReminderTimeMillis(date, beginLesson, 120)
        assertNotNull(time120)
        val cal120 = Calendar.getInstance().apply { timeInMillis = time120!! }
        assertEquals(9, cal120.get(Calendar.HOUR_OF_DAY))
        assertEquals(10, cal120.get(Calendar.MINUTE))
    }

    @Test
    fun testParseCustomDateTime() {
        val defaultYear = 2026

        // Format: dd.MM.yyyy HH:mm
        val time1 = parseCustomDateTime("16.11.2026", "10:30", defaultYear)
        assertNotNull(time1)
        val cal1 = Calendar.getInstance().apply { timeInMillis = time1!! }
        assertEquals(2026, cal1.get(Calendar.YEAR))
        assertEquals(10, cal1.get(Calendar.MONTH)) // November is 10 (0-indexed)
        assertEquals(16, cal1.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, cal1.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal1.get(Calendar.MINUTE))

        // Format: dd.MM HH:mm (uses defaultYear)
        val time2 = parseCustomDateTime("16.11", "09:15", defaultYear)
        assertNotNull(time2)
        val cal2 = Calendar.getInstance().apply { timeInMillis = time2!! }
        assertEquals(2026, cal2.get(Calendar.YEAR))
        assertEquals(10, cal2.get(Calendar.MONTH))
        assertEquals(16, cal2.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, cal2.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, cal2.get(Calendar.MINUTE))

        // Format: yyyy.MM.dd HH:mm
        val time3 = parseCustomDateTime("2026.11.16", "14:00", defaultYear)
        assertNotNull(time3)
        val cal3 = Calendar.getInstance().apply { timeInMillis = time3!! }
        assertEquals(2026, cal3.get(Calendar.YEAR))
        assertEquals(14, cal3.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal3.get(Calendar.MINUTE))

        // Format with slashes or dashes: 16-11-2026 10.30
        val time4 = parseCustomDateTime("16-11-2026", "10.30", defaultYear)
        assertNotNull(time4)
        val cal4 = Calendar.getInstance().apply { timeInMillis = time4!! }
        assertEquals(2026, cal4.get(Calendar.YEAR))
        assertEquals(10, cal4.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal4.get(Calendar.MINUTE))

        // Invalid cases
        assertNull(parseCustomDateTime("invalid", "10:30", defaultYear))
        assertNull(parseCustomDateTime("16.11.2026", "invalid", defaultYear))
        assertNull(parseCustomDateTime("16.13.2026", "10:30", defaultYear)) // month 13
        assertNull(parseCustomDateTime("32.11.2026", "10:30", defaultYear)) // day 32
        assertNull(parseCustomDateTime("16.11.2026", "25:00", defaultYear)) // hour 25
        assertNull(parseCustomDateTime("16.11.2026", "10:60", defaultYear)) // min 60
    }

    @Test
    fun testGenerateRequestCode() {
        val code1 = LessonReminderScheduler.generateRequestCode(101L, "2026.11.16", "11:10")
        val code2 = LessonReminderScheduler.generateRequestCode(101L, "2026.11.16", "11:10")
        val code3 = LessonReminderScheduler.generateRequestCode(101L, "2026.11.16", "12:50")

        assertEquals(code1, code2)
        assertTrue(code1 >= 0)
        assertTrue(code1 != code3)
    }
}
