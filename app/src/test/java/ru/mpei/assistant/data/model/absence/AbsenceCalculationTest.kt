package ru.mpei.assistant.data.model.absence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbsenceCalculationTest {

    @Test
    fun `test 16 pairs per semester allows exactly 1 unexcused pair`() {
        val absence0 = SubjectAbsence(
            subjectName = "Физика",
            unexcusedPairs = 0,
            excusedPairs = 0,
            totalSemesterPairs = 16
        )
        assertEquals(1, absence0.maxAllowedUnexcusedPairs)
        assertEquals(1, absence0.remainingAllowedPairs)
        assertTrue(absence0.isAutomatAllowedByAbsences)
        assertEquals(0f, absence0.unexcusedPercent, 0.001f)

        // 1 unexcused pair = 6.25% (on the limit, automat still allowed)
        val absence1 = absence0.copy(unexcusedPairs = 1)
        assertEquals(1, absence1.maxAllowedUnexcusedPairs)
        assertEquals(0, absence1.remainingAllowedPairs)
        assertTrue(absence1.isAutomatAllowedByAbsences)
        assertEquals(6.25f, absence1.unexcusedPercent, 0.001f)

        // 2 unexcused pairs = 12.5% (over limit, automat blocked)
        val absence2 = absence0.copy(unexcusedPairs = 2)
        assertEquals(1, absence2.maxAllowedUnexcusedPairs)
        assertEquals(0, absence2.remainingAllowedPairs)
        assertFalse(absence2.isAutomatAllowedByAbsences)
        assertEquals(12.5f, absence2.unexcusedPercent, 0.001f)
    }

    @Test
    fun `test 32 pairs per semester allows exactly 2 unexcused pairs`() {
        val absence = SubjectAbsence(
            subjectName = "Математический анализ",
            unexcusedPairs = 0,
            excusedPairs = 0,
            totalSemesterPairs = 32
        )
        assertEquals(2, absence.maxAllowedUnexcusedPairs)
        assertEquals(2, absence.remainingAllowedPairs)
        assertTrue(absence.isAutomatAllowedByAbsences)

        val absence2 = absence.copy(unexcusedPairs = 2)
        assertEquals(0, absence2.remainingAllowedPairs)
        assertTrue(absence2.isAutomatAllowedByAbsences)
        assertEquals(6.25f, absence2.unexcusedPercent, 0.001f)

        val absence3 = absence.copy(unexcusedPairs = 3)
        assertEquals(0, absence3.remainingAllowedPairs)
        assertFalse(absence3.isAutomatAllowedByAbsences)
    }

    @Test
    fun `test 24 pairs per semester floor gives 1 unexcused pair`() {
        // 24 * 0.0625 = 1.5 -> floor is 1
        val absence = SubjectAbsence(
            subjectName = "Информатика",
            unexcusedPairs = 1,
            excusedPairs = 3,
            totalSemesterPairs = 24
        )
        assertEquals(1, absence.maxAllowedUnexcusedPairs)
        assertEquals(0, absence.remainingAllowedPairs)
        assertTrue(absence.isAutomatAllowedByAbsences)
        assertFalse(absence.isBorderline24PairsCase)
        assertEquals(4, absence.totalAbsencePairs) // 1 unexcused + 3 excused
    }

    @Test
    fun `test 24 pairs with 2 unexcused pairs detects borderline case for coordination`() {
        val absence = SubjectAbsence(
            subjectName = "Микропроцессорные системы",
            unexcusedPairs = 2,
            excusedPairs = 0,
            totalSemesterPairs = 24
        )
        assertEquals(1, absence.maxAllowedUnexcusedPairs)
        assertFalse(absence.isAutomatAllowedByAbsences)
        assertTrue(absence.isBorderline24PairsCase)
        assertEquals(8.333f, absence.unexcusedPercent, 0.01f)
    }

    @Test
    fun `test excused absences do not degrade automat status`() {
        val absence = SubjectAbsence(
            subjectName = "История",
            unexcusedPairs = 1,
            excusedPairs = 10,
            totalSemesterPairs = 16
        )
        // Even with 10 excused pairs, unexcused is 1 <= 1 -> automat is kept
        assertTrue(absence.isAutomatAllowedByAbsences)
        assertEquals(0, absence.remainingAllowedPairs)
        assertEquals(11, absence.totalAbsencePairs)
    }

    @Test
    fun `test cumulative credit subject detection`() {
        val cumulative1 = SubjectAbsence(
            subjectName = "Физическая культура",
            controlType = "Зачет по совокупности"
        )
        assertTrue(cumulative1.isCumulativeCredit)

        val cumulative2 = SubjectAbsence(
            subjectName = "Информатика (Зачёт по совокупности)",
            controlType = ""
        )
        assertTrue(cumulative2.isCumulativeCredit)

        val nonCumulative = SubjectAbsence(
            subjectName = "Математический анализ",
            controlType = "Экзамен"
        )
        assertFalse(nonCumulative.isCumulativeCredit)
    }
}
