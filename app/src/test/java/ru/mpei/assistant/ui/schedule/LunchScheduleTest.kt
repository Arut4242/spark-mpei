package ru.mpei.assistant.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mpei.assistant.data.model.schedule.Lesson

class LunchScheduleTest {

    private fun createLesson(dbId: Long, begin: String, end: String, discipline: String = "Test"): Lesson {
        return Lesson(
            dbId = dbId,
            auditorium = "A-100",
            beginLesson = begin,
            endLesson = end,
            date = "2026.11.10",
            dayOfWeek = 2,
            discipline = discipline,
            kindOfWork = "Лекция",
            lecturer = "Иванов И.И."
        )
    }

    @Test
    fun testLunchProgressCalculation() {
        // До обеда (12:44)
        val before = calculateLunchProgress(currentMinuteOfDay = 12 * 60 + 44, isToday = true)
        assertFalse(before.isCurrent)
        assertEquals(0, before.remainingMinutes)
        assertEquals(0f, before.progress, 0.001f)

        // Старт обеда (12:45)
        val start = calculateLunchProgress(currentMinuteOfDay = 12 * 60 + 45, isToday = true)
        assertTrue(start.isCurrent)
        assertEquals(60, start.remainingMinutes)
        assertEquals(0f, start.progress, 0.001f)

        // Середина обеда (13:15 -> прошло 30 мин)
        val mid = calculateLunchProgress(currentMinuteOfDay = 13 * 60 + 15, isToday = true)
        assertTrue(mid.isCurrent)
        assertEquals(30, mid.remainingMinutes)
        assertEquals(0.5f, mid.progress, 0.001f)

        // Последняя минута обеда (13:44)
        val almostEnd = calculateLunchProgress(currentMinuteOfDay = 13 * 60 + 44, isToday = true)
        assertTrue(almostEnd.isCurrent)
        assertEquals(1, almostEnd.remainingMinutes)
        assertEquals(59f / 60f, almostEnd.progress, 0.01f)

        // Конец обеда (13:45)
        val end = calculateLunchProgress(currentMinuteOfDay = 13 * 60 + 45, isToday = true)
        assertFalse(end.isCurrent)
        assertEquals(0, end.remainingMinutes)

        // Не сегодня -> не активен
        val notToday = calculateLunchProgress(currentMinuteOfDay = 13 * 60, isToday = false)
        assertFalse(notToday.isCurrent)
    }

    @Test
    fun testInsertLunchBetweenPairs() {
        val p1 = createLesson(1, "09:20", "10:55", "Пара 1")
        val p2 = createLesson(2, "11:10", "12:45", "Пара 2")
        val p3 = createLesson(3, "13:45", "15:20", "Пара 3")
        val p4 = createLesson(4, "15:35", "17:10", "Пара 4")

        val items = insertLunchIntoSchedule(listOf(p1, p2, p3, p4))
        assertEquals(5, items.size)
        assertEquals(ScheduleScreenItem.LessonItem(p1), items[0])
        assertEquals(ScheduleScreenItem.LessonItem(p2), items[1])
        assertEquals(ScheduleScreenItem.LunchItem, items[2])
        assertEquals(ScheduleScreenItem.LessonItem(p3), items[3])
        assertEquals(ScheduleScreenItem.LessonItem(p4), items[4])
    }

    @Test
    fun testNoLunchWhenOnlyMorningPairs() {
        val p1 = createLesson(1, "09:20", "10:55")
        val p2 = createLesson(2, "11:10", "12:45")

        val items = insertLunchIntoSchedule(listOf(p1, p2))
        assertEquals(2, items.size)
        assertEquals(ScheduleScreenItem.LessonItem(p1), items[0])
        assertEquals(ScheduleScreenItem.LessonItem(p2), items[1])
    }

    @Test
    fun testNoLunchWhenOnlyAfternoonPairs() {
        val p3 = createLesson(3, "13:45", "15:20")
        val p4 = createLesson(4, "15:35", "17:10")

        val items = insertLunchIntoSchedule(listOf(p3, p4))
        assertEquals(2, items.size)
        assertEquals(ScheduleScreenItem.LessonItem(p3), items[0])
        assertEquals(ScheduleScreenItem.LessonItem(p4), items[1])
    }

    @Test
    fun testInsertLunchBetweenMorningAndAfternoonPairs() {
        val p2 = createLesson(2, "11:10", "12:45")
        val p3 = createLesson(3, "13:45", "15:20")

        val items = insertLunchIntoSchedule(listOf(p2, p3))
        assertEquals(3, items.size)
        assertEquals(ScheduleScreenItem.LessonItem(p2), items[0])
        assertEquals(ScheduleScreenItem.LunchItem, items[1])
        assertEquals(ScheduleScreenItem.LessonItem(p3), items[2])
    }

    @Test
    fun testNoLunchOnEmptySchedule() {
        val items = insertLunchIntoSchedule(emptyList())
        assertTrue(items.isEmpty())
    }

    @Test
    fun testNoLunchOnEveningOnlyDay() {
        val p5 = createLesson(5, "17:20", "18:55")
        val items = insertLunchIntoSchedule(listOf(p5))
        assertEquals(1, items.size)
        assertEquals(ScheduleScreenItem.LessonItem(p5), items[0])
    }
}
