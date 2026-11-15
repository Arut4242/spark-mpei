package ru.mpei.assistant.ui.bars

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class BarsLoginWebViewSecurityTest {

    private fun isAllowedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = try {
            URI.create(url)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && scheme != "http") return false
        val host = uri.host?.lowercase() ?: return false
        return host == "bars.mpei.ru" || host == "mpei.ru" || host.endsWith(".mpei.ru")
    }

    @Test
    fun testAllowedMpeiHosts() {
        assertTrue(isAllowedUrl("https://bars.mpei.ru/bars_web/"))
        assertTrue(isAllowedUrl("https://bars.mpei.ru/bars_web/ST_Study/Main/Main?studentID=123"))
        assertTrue(isAllowedUrl("https://sso.mpei.ru/auth/realms/mpei/protocol/openid-connect/auth"))
        assertTrue(isAllowedUrl("https://login.mpei.ru/adfs/ls/"))
        assertTrue(isAllowedUrl("https://idp.mpei.ru/idp/profile/SAML2/Redirect/SSO"))
        assertTrue(isAllowedUrl("https://mpei.ru/"))
    }

    @Test
    fun testBlockedExternalAndPhishingHosts() {
        // Сторонние домены
        assertFalse(isAllowedUrl("https://google.com/"))
        assertFalse(isAllowedUrl("https://evil-attacker.com/steal-creds"))
        // Фишинговые домены с подменой
        assertFalse(isAllowedUrl("https://bars-mpei.ru/login"))
        assertFalse(isAllowedUrl("https://bars.mpei.ru.attacker.com/login"))
        assertFalse(isAllowedUrl("https://phishingmpei.ru"))
        assertFalse(isAllowedUrl("https://mpei.ru.attacker.com"))
        // Небезопасные схемы
        assertFalse(isAllowedUrl("javascript:alert(1)"))
        assertFalse(isAllowedUrl("file:///android_asset/exploit.html"))
        assertFalse(isAllowedUrl(null))
        assertFalse(isAllowedUrl(""))
    }
}
