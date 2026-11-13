package ru.mpei.assistant.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mpei.assistant.data.model.absence.SubjectAbsence

class AbsenceNotificationTest {

    @Test
    fun testNoDifferencesReturnsNull() {
        val subject = SubjectAbsence(
            subjectName = "Физика",
            totalSemesterPairs = 32,
            unexcusedPairs = 1,
            excusedPairs = 0
        )
        val result = buildAbsenceNotification(
            subjectName = "Физика",
            diffUnexcused = 0,
            diffExcused = 0,
            newAbsence = subject
        )
        assertNull(result)
    }

    @Test
    fun testExcusedAbsenceOnly() {
        val subject = SubjectAbsence(
            subjectName = "Математика",
            totalSemesterPairs = 32,
            unexcusedPairs = 0,
            excusedPairs = 2
        )
        val result = buildAbsenceNotification(
            subjectName = "Математика",
            diffUnexcused = 0,
            diffExcused = 1,
            newAbsence = subject
        )
        assertNotNull(result)
        assertEquals("Уважительный пропуск: Математика", result!!.title)
        assertTrue(result.text.contains("+1"))
        assertTrue(result.text.contains("Всего: 2"))
        assertTrue(result.text.contains("На автомат не влияет"))
        assertFalse(result.isWarning)
    }

    @Test
    fun testUnexcusedWithSafeRemainingBuffer() {
        // 32 pairs * 0.0625 = 2 max allowed unexcused pairs
        val subject = SubjectAbsence(
            subjectName = "Информатика",
            totalSemesterPairs = 32,
            unexcusedPairs = 1,
            excusedPairs = 0
        )
        assertEquals(2, subject.maxAllowedUnexcusedPairs)
        assertEquals(1, subject.remainingAllowedPairs)
        assertTrue(subject.isAutomatAllowedByAbsences)

        val result = buildAbsenceNotification(
            subjectName = "Информатика",
            diffUnexcused = 1,
            diffExcused = 0,
            newAbsence = subject
        )
        assertNotNull(result)
        assertEquals("Новый пропуск: Информатика", result!!.title)
        assertTrue(result.text.contains("+1"))
        assertTrue(result.text.contains("Всего пропусков: 1"))
        assertTrue(result.text.contains("До потери автомата осталось: 1 пар(ы)"))
        assertFalse(result.isWarning)
    }

    @Test
    fun testUnexcusedAtBorderlineThreshold() {
        // 32 pairs * 0.0625 = 2 max allowed. Currently at 2 -> remaining = 0
        val subject = SubjectAbsence(
            subjectName = "Электротехника",
            totalSemesterPairs = 32,
            unexcusedPairs = 2,
            excusedPairs = 0
        )
        assertEquals(2, subject.maxAllowedUnexcusedPairs)
        assertEquals(0, subject.remainingAllowedPairs)
        assertTrue(subject.isAutomatAllowedByAbsences)

        val result = buildAbsenceNotification(
            subjectName = "Электротехника",
            diffUnexcused = 1,
            diffExcused = 0,
            newAbsence = subject
        )
        assertNotNull(result)
        assertEquals("Новый пропуск: Электротехника", result!!.title)
        assertTrue(result.text.contains("Вы на грани лимита 6,25%"))
        assertTrue(result.text.contains("2 из макс. 2 пар"))
        assertTrue(result.text.contains("Больше пропускать нельзя"))
        assertTrue(result.isWarning)
    }

    @Test
    fun testUnexcusedExceededAutomatLost() {
        // 32 pairs * 0.0625 = 2 max allowed. Currently at 3 -> exceeded!
        val subject = SubjectAbsence(
            subjectName = "Философия",
            totalSemesterPairs = 32,
            unexcusedPairs = 3,
            excusedPairs = 0
        )
        assertEquals(2, subject.maxAllowedUnexcusedPairs)
        assertEquals(0, subject.remainingAllowedPairs)
        assertFalse(subject.isAutomatAllowedByAbsences)

        val result = buildAbsenceNotification(
            subjectName = "Философия",
            diffUnexcused = 1,
            diffExcused = 0,
            newAbsence = subject
        )
        assertNotNull(result)
        assertEquals("Новый пропуск: Философия", result!!.title)
        assertTrue(result.text.contains("Лимит 6,25% превышен"))
        assertTrue(result.text.contains("3 из макс. 2 пар"))
        assertTrue(result.text.contains("Автомат утерян!"))
        assertTrue(result.isWarning)
    }
}
