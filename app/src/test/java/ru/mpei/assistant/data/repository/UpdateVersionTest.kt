package ru.mpei.assistant.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    // Simple test version of the logic
    private fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

        val remoteBase = cleanRemote.split("-")[0]
        val currentBase = cleanCurrent.split("-")[0]

        val remoteParts = remoteBase.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentBase.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        if (cleanRemote.contains("hotfix", ignoreCase = true) && !cleanCurrent.contains("hotfix", ignoreCase = true)) {
            return true
        }

        return false
    }

    @Test
    fun `test patch version bump is newer`() {
        assertTrue(isNewerVersion("1.0.5", "1.0.4"))
        assertTrue(isNewerVersion("v1.0.5", "1.0.4"))
        assertTrue(isNewerVersion("v1.0.5.1", "1.0.5"))
        assertTrue(isNewerVersion("v1.0.5-hotfix", "1.0.5"))
        assertTrue(isNewerVersion("v1.0.6", "1.0.5"))
        assertTrue(isNewerVersion("v1.0.10", "1.0.4"))
    }

    @Test
    fun `test minor and major version bumps`() {
        assertTrue(isNewerVersion("1.1.0", "1.0.5"))
        assertTrue(isNewerVersion("v2.0.0", "1.0.5"))
        assertTrue(isNewerVersion("2.0.0-beta", "1.0.5"))
    }

    @Test
    fun `test same or older versions are not newer`() {
        assertFalse(isNewerVersion("1.0.5", "1.0.5"))
        assertFalse(isNewerVersion("v1.0.5", "1.0.5"))
        assertFalse(isNewerVersion("1.0.4", "1.0.5"))
        assertFalse(isNewerVersion("0.9.9", "1.0.5"))
    }
}
