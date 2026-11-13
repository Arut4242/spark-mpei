package ru.mpei.assistant.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SemesterWeekTest {

    @Test
    fun testCurrentWeekInNovember2026IsWeek11() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 9, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val week = getSemesterWeekNumber(cal)
        assertEquals(11, week)
    }

    @Test
    fun testFirstWeekInSeptember2026IsWeek1() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 31, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val week = getSemesterWeekNumber(cal)
        assertEquals(1, week)
    }

    @Test
    fun testWeek10InNovember2026() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 2, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val week = getSemesterWeekNumber(cal)
        assertEquals(10, week)
    }

    @Test
    fun testWeek12InNovember2026() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 16, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val week = getSemesterWeekNumber(cal)
        assertEquals(12, week)
    }
}
