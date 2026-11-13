package ru.mpei.assistant.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mpei.assistant.data.local.SessionStorage

class BarsSyncSchedulerTest {

    @Test
    fun testDefaultIntervalIsThirtyMinutesForBars() {
        assertEquals(30L, BarsSyncScheduler.DEFAULT_INTERVAL_MINUTES)
    }

    @Test
    fun testEmailCheckWorkerIntervalIsTenMinutes() {
        assertEquals(10L, EmailCheckWorker.INTERVAL_MINUTES)
    }

    @Test
    fun testThemeModeConstants() {
        assertEquals("SYSTEM", SessionStorage.THEME_SYSTEM)
        assertEquals("LIGHT", SessionStorage.THEME_LIGHT)
        assertEquals("DARK", SessionStorage.THEME_DARK)
    }

    @Test
    fun testAllAvailableIntervalsSatisfyAndroidWorkManagerConstraint() {
        // Android WorkManager PeriodicWorkRequest requires interval >= 15 minutes
        assertTrue(BarsSyncScheduler.AVAILABLE_INTERVALS.isNotEmpty())
        for (interval in BarsSyncScheduler.AVAILABLE_INTERVALS) {
            assertTrue("Interval $interval must be at least 15 min", interval >= 15L)
        }
    }

    @Test
    fun testRiskEvaluation() {
        assertTrue(BarsSyncScheduler.isHighRisk(15L))
        assertTrue(BarsSyncScheduler.isHighRisk(30L))
        assertFalse(BarsSyncScheduler.isHighRisk(60L))
        assertFalse(BarsSyncScheduler.isHighRisk(120L))
        assertFalse(BarsSyncScheduler.isHighRisk(180L))
        assertFalse(BarsSyncScheduler.isHighRisk(360L))
    }

    @Test
    fun testIntervalLabels() {
        assertTrue(BarsSyncScheduler.getIntervalLabel(15L).contains("Высокий риск"))
        assertTrue(BarsSyncScheduler.getIntervalLabel(30L).contains("По умолчанию"))
        assertTrue(BarsSyncScheduler.getShortLabel(30L).contains("30 минут"))
    }
}
