package ru.mpei.assistant.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLoggerSanitizerTest {

    @Test
    fun `test email addresses are masked`() {
        val raw = "Почта студента: ivanovii@mpei.ru или test.user@gmail.com"
        val sanitized = AppLogger.sanitize(raw)
        assertFalse(sanitized.contains("ivanovii@"))
        assertFalse(sanitized.contains("test.user@"))
        assertTrue(sanitized.contains("***@mpei.ru"))
        assertTrue(sanitized.contains("***@gmail.com"))
    }

    @Test
    fun `test logins and domain credentials are masked`() {
        val raw1 = "fetchEmailsPage started for 'ivanovii', beforeMsg=null"
        assertEquals("fetchEmailsPage started for '***', beforeMsg=null", AppLogger.sanitize(raw1))

        val raw2 = "Connecting to IMAP as 'petrov_pp' with login='petrov_pp'"
        val sanitized2 = AppLogger.sanitize(raw2)
        assertFalse(sanitized2.contains("petrov_pp"))
        assertTrue(sanitized2.contains("as '***'"))

        val raw3 = "auth with mpei\\sidorov"
        assertEquals("auth with mpei\\***", AppLogger.sanitize(raw3))
    }

    @Test
    fun `test student and teacher surnames with initials are masked`() {
        val raw = "Пара: лекция (Иванов И.И.), практика (Петров П. П.)"
        val sanitized = AppLogger.sanitize(raw)
        assertFalse(sanitized.contains("Иванов"))
        assertFalse(sanitized.contains("Петров"))
        assertTrue(sanitized.contains("И*** И.И."))
        assertTrue(sanitized.contains("П*** П.П."))
    }

    @Test
    fun `test full 3-part names are masked`() {
        val raw = "Студент: Иванов Иван Иванович, группа А-04-23"
        val sanitized = AppLogger.sanitize(raw)
        assertFalse(sanitized.contains("Иванов Иван Иванович"))
        assertTrue(sanitized.contains("И*** [ФИО]"))
        assertTrue(sanitized.contains("группа А-04-23"))
    }

    @Test
    fun `test UUID and student IDs are masked`() {
        val raw = "Found studentId=1f061364-9447-ee11-80da-005056be401c in ListStudent"
        val sanitized = AppLogger.sanitize(raw)
        assertFalse(sanitized.contains("1f061364-9447-ee11-80da-005056be401c"))
        assertTrue(sanitized.contains("***-401c"))
    }

    @Test
    fun `test cookies and passwords are masked`() {
        val raw1 = "Response cookie: .ASPXAUTH=0A1B2C3D4E5F; path=/"
        val sanitized1 = AppLogger.sanitize(raw1)
        assertFalse(sanitized1.contains("0A1B2C3D4E5F"))
        assertTrue(sanitized1.contains(".ASPXAUTH=[СКРЫТО]"))

        val raw2 = "Auth credentials: password=Secret12345!"
        val sanitized2 = AppLogger.sanitize(raw2)
        assertFalse(sanitized2.contains("Secret12345!"))
        assertTrue(sanitized2.contains("password=[СКРЫТО]"))
    }

    @Test
    fun `test url query parameters and studentId are masked`() {
        val raw = "Requesting https://bars.mpei.ru/bars_web/ST_Study/Main/Main?studentID=1f061364-9447-ee11-80da-005056be401c&sem=2026-1"
        val sanitized = AppLogger.sanitize(raw)
        assertFalse(sanitized.contains("1f061364-9447-ee11-80da-005056be401c"))
        assertFalse(sanitized.contains("sem=2026-1"))
        assertTrue(sanitized.contains("?[ПАРАМЕТРЫ_СКРЫТЫ]"))
    }
}
