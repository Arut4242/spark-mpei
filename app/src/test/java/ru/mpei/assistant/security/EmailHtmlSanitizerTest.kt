package ru.mpei.assistant.security

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class EmailHtmlSanitizerTest {
    @Test fun removesActiveContentAndAllResourceAttributes() {
        val payload = """<script>alert(1)</script><iframe src="https://evil.test"></iframe>
            <form action="https://evil.test"><input name="password"></form>
            <object data="file:///secret"></object><embed src="content://secret">
            <style>@import url(https://evil.test/css);</style>
            <img src="https://evil.test/pixel" srcset="https://evil.test/pixel2 2x">
            <svg onload="alert(1)"><image href="https://evil.test"></image></svg>
            <meta http-equiv="refresh" content="0;url=https://evil.test">
            <div onclick="alert(1)" style="background:url(https://evil.test)">Text</div>"""
        val doc = Jsoup.parse(EmailHtmlSanitizer.sanitize(payload))
        assertTrue(doc.select("script,iframe,form,object,embed,style,img,svg,meta,input").isEmpty())
        assertTrue(doc.select("[onclick],[onload],[style],[src],[srcset],[background]").isEmpty())
        assertTrue(doc.text().contains("Text"))
    }

    @Test fun dangerousSchemesAndObfuscatedUrlsAreRemoved() {
        val html = """<a href="javascript:alert(1)">one</a>
            <a href="jav&#x61;script:alert(1)">two</a>
            <a href="file:///secret">three</a><a href="intent://test">four</a>
            <a href="data:text/html,test">five</a><a href="//evil.test">six</a>
            <a href="https://user@evil.test">seven</a>
            <a href="https://example.com/path">safe</a>"""
        val links = Jsoup.parse(EmailHtmlSanitizer.sanitize(html)).select("a[href]")
        assertEquals(1, links.size)
        assertEquals("https://example.com/path", links.first()!!.attr("href"))
    }

    @Test fun preservesTextAndBasicTables() {
        val clean = EmailHtmlSanitizer.sanitize("<p>Hello <b>student</b></p><table><tr><td>Grade</td><td>5</td></tr></table>")
        assertTrue(clean.contains("<b>student</b>"))
        assertEquals(2, Jsoup.parse(clean).select("td").size)
    }
}
