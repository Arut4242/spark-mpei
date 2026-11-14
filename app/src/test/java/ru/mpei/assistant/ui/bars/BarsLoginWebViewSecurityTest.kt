package ru.mpei.assistant.ui.bars

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mpei.assistant.security.WebUrlPolicy

class BarsLoginWebViewSecurityTest {
    @Test fun permitsOnlyVerifiedHttpsHost() {
        assertTrue(WebUrlPolicy.isBarsUrl("https://bars.mpei.ru/bars_web/"))
        assertFalse(WebUrlPolicy.isBarsUrl("http://bars.mpei.ru/"))
        assertFalse(WebUrlPolicy.isBarsUrl("https://sso.mpei.ru/"))
        assertFalse(WebUrlPolicy.isBarsUrl("https://mpei.ru/"))
        assertFalse(WebUrlPolicy.isBarsUrl("https://bars.mpei.ru.attacker.test/"))
        assertFalse(WebUrlPolicy.isBarsUrl("https://user@bars.mpei.ru/"))
        assertFalse(WebUrlPolicy.isBarsUrl("javascript:alert(1)"))
    }
}
