package ru.mpei.assistant.data.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsWebClientSecurityTest {

    @Test
    fun `test parseCookies applies domain and security flags`() {
        val raw = ".ASPXAUTH=12345ABCDE; SessionId=XYZ987"
        val cookies = BarsWebClient.parseCookies(raw, "bars.mpei.ru")

        assertEquals(2, cookies.size)
        val authCookie = cookies.first { it.name == ".ASPXAUTH" }
        assertEquals("12345ABCDE", authCookie.value)
        assertEquals("bars.mpei.ru", authCookie.domain)
        assertEquals("/", authCookie.path)
        assertTrue("Cookie must be marked secure", authCookie.secure)
        assertTrue("Cookie must be marked httpOnly", authCookie.httpOnly)
    }

    @Test
    fun `test cookie jar matching for https bars host and isolation for cross-domain`() {
        val raw = ".ASPXAUTH=SECRET_SESSION_TOKEN; AppUser=student"
        val cookies = BarsWebClient.parseCookies(raw, "bars.mpei.ru")

        val barsHttpsUrl = "https://bars.mpei.ru/bars_web/ST_Study/Main/Main".toHttpUrl()
        val barsHttpUrl = "http://bars.mpei.ru/bars_web/ST_Study/Main/Main".toHttpUrl()
        val externalHttpsUrl = "https://attacker-host.com/steal".toHttpUrl()
        val mpeiOtherHostUrl = "https://mail.mpei.ru/owa".toHttpUrl()

        // 1. Для легитимного HTTPS-запроса к bars.mpei.ru куки матчатся
        val matchedForBars = cookies.filter { it.matches(barsHttpsUrl) }
        assertEquals(2, matchedForBars.size)

        // 2. Для незащищённого HTTP куки со secure флагом НЕ матчатся
        val matchedForHttp = cookies.filter { it.matches(barsHttpUrl) }
        assertTrue("Cookies must not match non-HTTPS URL", matchedForHttp.isEmpty())

        // 3. Для стороннего домена куки БАРС категорически НЕ матчатся
        val matchedForExternal = cookies.filter { it.matches(externalHttpsUrl) }
        assertTrue("Cookies must never match external domain", matchedForExternal.isEmpty())

        // 4. Для другого поддомена МЭИ (например, почты) куки БАРС также НЕ матчатся
        val matchedForOtherMpei = cookies.filter { it.matches(mpeiOtherHostUrl) }
        assertTrue("Cookies must be scoped strictly to bars.mpei.ru", matchedForOtherMpei.isEmpty())
    }

    @Test
    fun `test parseCookies handles malformed input safely`() {
        val emptyCookies = BarsWebClient.parseCookies(null, "bars.mpei.ru")
        assertTrue(emptyCookies.isEmpty())

        val malformed = "; ; invalid_no_eq; =no_name; no_val=; ; "
        val parsed = BarsWebClient.parseCookies(malformed, "bars.mpei.ru")
        assertEquals(1, parsed.size)
        assertEquals("no_val", parsed[0].name)
        assertEquals("", parsed[0].value)
    }
}
