package ru.mpei.assistant.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonBreakProgressTest {

    // Стандартная пара МЭИ: 09:20 - 10:55
    // 09:20 = 560 мин, 10:55 = 655 мин (всего 95 мин)
    // 1-я часть: 09:20 - 10:05 (45 мин)
    // Перерыв: 10:05 - 10:10 (5 мин)
    // 2-я часть: 10:10 - 10:55 (45 мин)
    private val beginMin = 9 * 60 + 20 // 560
    private val endMin = 10 * 60 + 55  // 655

    @Test
    fun testParseTimeToMinutes() {
        assertEquals(560, parseTimeToMinutes("09:20"))
        assertEquals(655, parseTimeToMinutes("10:55"))
        assertEquals(0, parseTimeToMinutes("00:00"))
        assertEquals(23 * 60 + 59, parseTimeToMinutes("23:59"))
        assertNull(parseTimeToMinutes(""))
        assertNull(parseTimeToMinutes("invalid"))
    }

    @Test
    fun testAtBeginningOfLesson() {
        val progress = calculateLessonBreakProgress(beginMin, endMin, currentMinuteOfDay = 560)
        assertEquals(LessonStage.FIRST_HALF, progress.stage)
        assertEquals(45, progress.half1Duration)
        assertEquals(5, progress.breakDuration)
        assertEquals(45, progress.half2Duration)
        assertEquals(0f, progress.half1Progress, 0.001f)
        assertEquals(0f, progress.breakProgress, 0.001f)
        assertEquals(0f, progress.half2Progress, 0.001f)
        assertEquals(45, progress.minutesUntilBreak)
        assertEquals(95, progress.lessonRemainingMinutes)
        assertEquals(0f, progress.totalProgress, 0.001f)
    }

    @Test
    fun testMiddleOfFirstHalf() {
        // 09:50 -> 30 минут прошло с начала пары
        val progress = calculateLessonBreakProgress(beginMin, endMin, currentMinuteOfDay = 590)
        assertEquals(LessonStage.FIRST_HALF, progress.stage)
        assertEquals(30f / 45f, progress.half1Progress, 0.01f)
        assertEquals(0f, progress.breakProgress, 0.001f)
        assertEquals(0f, progress.half2Progress, 0.001f)
        assertEquals(15, progress.minutesUntilBreak)
        assertEquals(65, progress.lessonRemainingMinutes)
    }

    @Test
    fun testOneMinuteBeforeBreak() {
        // 10:04 -> 1 минута до перерыва
        val progress = calculateLessonBreakProgress(beginMin, endMin, currentMinuteOfDay = 604)
        assertEquals(LessonStage.FIRST_HALF, progress.stage)
        assertEquals(1, progress.minutesUntilBreak)
        assertEquals(44f / 45f, progress.half1Progress, 0.01f)
    }

    @Test
    fun testBreakStartExact() {
        // 10:05 -> старт перерыва
        val progress = calculateLessonBreakProgress(beginMin, endMin, currentMinuteOfDay = 605)
        assertEquals(LessonStage.BREAK, progress.stage)
        assertEquals(1f, progress.half1Progress, 0.001f)
        assertEquals(0f, progress.breakProgress, 0.001f)
        assertEquals(0f, progress.half2Progress, 0.001f)
        assertEquals(5, progress.breakRemainingMinutes)
        assertEquals(50, progress.lessonRemainingMinutes)
    }

    @Test
    fun testBreakMiddle() {
        // 10:07 -> 2 минуты перерыва прошло, 3 осталось
        val progress = calculateLessonBreakProgress(beginMin, endMin, currentMinuteOfDay = 607)
        assertEquals(LessonStage.BREAK, progress.stage)
        assertEquals(1f, progress.half1Progress, 0.001f)
        assertEquals(2f / 5f, progress.breakProgress, 0.01f)
        assertEquals(3, progress.breakRemainingMinutes)
        assertEquals(48, progress.lessonRemainingMinutes)
    }

    @Test
    fun testSecondHalfStartExact() {
        // 10:10 -> конец перерыва, начало 2-й половины
        val progress = calculateLessonBreakProgress(beginMin, endMin, currentMinuteOfDay = 610)
        assertEquals(LessonStage.SECOND_HALF, progress.stage)
        assertEquals(1f, progress.half1Progress, 0.001f)
        assertEquals(1f, progress.breakProgress, 0.001f)
        assertEquals(0f, progress.half2Progress, 0.001f)
        assertEquals(45, progress.lessonRemainingMinutes)
    }

    @Test
    fun testMiddleOfSecondHalf() {
        // 10:30 -> 20 минут во 2-й половине
        val progress = calculateLessonBreakProgress(beginMin, endMin, currentMinuteOfDay = 630)
        assertEquals(LessonStage.SECOND_HALF, progress.stage)
        assertEquals(1f, progress.half1Progress, 0.001f)
        assertEquals(1f, progress.breakProgress, 0.001f)
        assertEquals(20f / 45f, progress.half2Progress, 0.01f)
        assertEquals(25, progress.lessonRemainingMinutes)
    }

    @Test
    fun testShortPairFallback() {
        // Короткое событие менее 15 минут не дробится перерывом
        val progress = calculateLessonBreakProgress(beginMin = 600, endMin = 610, currentMinuteOfDay = 605)
        assertEquals(LessonStage.FIRST_HALF, progress.stage)
        assertEquals(0, progress.breakDuration)
        assertEquals(10, progress.half1Duration)
        assertEquals(0.5f, progress.half1Progress, 0.01f)
    }
}
