package ru.mpei.assistant.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpeiCertificatesTest {

    @Test
    fun testCertificatesParsedSuccessfully() {
        val certs = MpeiCertificates.getMpeiCertificates()
        assertEquals(2, certs.size)

        val root = certs[0]
        val intermediate = certs[1]

        assertTrue(root.subjectX500Principal.name.contains("MPEI (TU)"))
        assertTrue(intermediate.subjectX500Principal.name.contains("public-PUBLICCA-CA"))
    }

    @Test
    fun testTrustManagerCreation() {
        val tm = MpeiCertificates.createMpeiTrustManager("mail.mpei.ru")
        assertNotNull(tm)
        assertTrue(tm.acceptedIssuers.isNotEmpty())
    }

    @Test
    fun testSslContextCreation() {
        val sslContext = MpeiCertificates.createMpeiSslContext("mail.mpei.ru")
        assertNotNull(sslContext)
        assertNotNull(sslContext.socketFactory)
    }
}
