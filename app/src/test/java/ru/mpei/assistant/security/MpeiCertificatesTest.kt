package ru.mpei.assistant.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mpei.assistant.data.network.OsepMailClient
import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class MpeiCertificatesTest {

    companion object {
        private const val MITM_SELF_SIGNED_MPEI_CERT_PEM = """-----BEGIN CERTIFICATE-----
MIIDIzCCAgugAwIBAgIIPxnOTkT8ELcwDQYJKoZIhvcNAQEMBQAwQDELMAkGA1UE
BhMCUlUxGjAYBgNVBAoTEUF0dGFja2VyIE1JVE0gT3JnMRUwEwYDVQQDEwxtYWls
Lm1wZWkucnUwHhcNMjYxMTE2MjMxNzE1WhcNMjcxMTE2MjMxNzE1WjBAMQswCQYD
VQQGEwJSVTEaMBgGA1UEChMRQXR0YWNrZXIgTUlUTSBPcmcxFTATBgNVBAMTDG1h
aWwubXBlaS5ydTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMVo8D+c
7x9nhRJ52SXwHWK6gUZZuJwVlai8DRLxTX9t77lPCXltK2AEUPv0OCw41X6Ji5sc
qI8m5CIdbqKNTKG7l69C2p8zaphiAR0ZrE6X/aGKULfqLA7oiAM2Q9C8wi/BRZ1r
scroeXjtdIgln3cY/WcZPSPIqfzOwMlwsBQU+0X2zik0WoMJGlfOCb+S3zrGFp6D
1HqnjVvZTmtj9MWuHSHMHKCKLix2GwV6Vc2hQI5i6+fn6PGfejhFiD3hjU+8lyqM
oNTxKLWQYca1c64bcS0lNEnPFCUnXj94j1S70pTuYl6ny3Pa+deLEI5y+tt0kY+y
a4c1StGf5t/TI9MCAwEAAaMhMB8wHQYDVR0OBBYEFAyleB+ujcrdfASpJr8p6Lp7
8A1OMA0GCSqGSIb3DQEBDAUAA4IBAQBEQq/ZWUWqBvPCYCCVFQ3+6ePOnbZe/Mdj
gL0HpEcvvRFNJt0h4rBErJhYIX+lOyRSDCoajD/ziSffIYZHNN1uE+0tnMJa9xO2
QCszsKKW1gFKVXYCvq+TgOVracUnwuOZYyPjQo9CruZYROea6sdfBWWCYzfFQwIC
ZLYxHz+nxzqIsXjqTT8WES/2vB0HbfJIO4WXGrAjza4Sn+3T88OHUj7Q2HEkPS56
H3mh7RRvHskSyb5/x2qM47YYqZY1YTtc3MxDF9vV3NJyl2GTaNEauWTqoIichjlx
QuIJxWcKJwNOL4KG/1pYEt+pA0FLoKwpes+IDuD++qj3TPBfff77
-----END CERTIFICATE-----"""

        private const val UNTRUSTED_WRONG_HOST_CERT_PEM = """-----BEGIN CERTIFICATE-----
MIIDKDCCAhCgAwIBAgIJAPCHpVvNaDMHMA0GCSqGSIb3DQEBDAUAMEIxCzAJBgNV
BAYTAlJVMRcwFQYDVQQKEw5NYWxpY2lvdXMgSG9zdDEaMBgGA1UEAxMRZXZpbC5h
dHRhY2tlci5jb20wHhcNMjYxMTE2MjMxNzIzWhcNMjcxMTE2MjMxNzIzWjBCMQsw
CQYDVQQGEwJSVTEXMBUGA1UEChMOTWFsaWNpb3VzIEhvc3QxGjAYBgNVBAMTEWV2
aWwuYXR0YWNrZXIuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA
2Sc04vXevZRn9icTVFFnyxh4zIPYW0XPm76odx1or7gUMYpbZuBSJb4nzEEefm9M
WX6PGTlsm+0/nDYQFwHycivhsR4wjd4hquyDXYrU1Jfjhtl5lI8rtNvJFszqmHK1
2BaElVAxUgnpQBXPBOh705U4kufICXPfOtoOetG8xasFtuGZO0OtfDdXp/fGyB+t
0StLH5wL4HPyJcCTilFjirmBU999kFWzUg0bVUljH9ISUEPnKe+9CrAzmxI7eeeB
C0z0mE+BiL59l4YdQu85GdI/aLPjg5MscSjIEyuq+62KdRwRfxb+ZNRZvdHtBMMg
3/JmJ4pKYP4uNJul3w5YgQIDAQABoyEwHzAdBgNVHQ4EFgQUY6xiBIPKybeO1ezx
ElPiwg0ulFQwDQYJKoZIhvcNAQEMBQADggEBAJq1g3J2+jXqoN8baweBXOI98DzF
3BNjEcDryyYw2TcrSoDjZGbLEiMHv72QCO2vXbV/77UtCJiYlalQGbdjfbZtbtqW
RxXb014T0GoyhXPG5THzwD1bjI1XjKBuRfZpir537St+An9i1bQzI4jxpi9xi7n/
Fx5Ijr+u4m15V3Z9FGM3mR85HVNTws8rbPUNOfRj5xTv0A9uOipwQg1z+hOiZhUS
v6NWB2Yf1MMedZhgn0/YiAg/ciVawyYLsmCVOGRjcUZhNe4FAWHXMJ2OQz1reGDN
YLa/czklbyuyEwDIBfz01hQDOYlowkk7aUvQ/gIP2u61y/twkmo+4DN7A5I=
-----END CERTIFICATE-----"""

        private fun parsePem(pem: String): X509Certificate {
            val cf = CertificateFactory.getInstance("X.509")
            return cf.generateCertificate(ByteArrayInputStream(pem.toByteArray(Charsets.UTF_8))) as X509Certificate
        }
    }

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

    @Test
    fun testRejectionOfUntrustedSelfSignedCertificateEvenWithMatchingHostname() {
        val tm = MpeiCertificates.createMpeiTrustManager("mail.mpei.ru")
        val mitmCert = parsePem(MITM_SELF_SIGNED_MPEI_CERT_PEM)

        // Сертификат выпущен для mail.mpei.ru, но самоподписан злоумышленником
        // Валидатор обязан отклонить его с CertificateException (отсутствие доверенного CA)
        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(arrayOf(mitmCert), "RSA")
        }
    }

    @Test
    fun testRejectionOfUntrustedCertificateWithWrongHostname() {
        val tm = MpeiCertificates.createMpeiTrustManager("mail.mpei.ru")
        val wrongHostCert = parsePem(UNTRUSTED_WRONG_HOST_CERT_PEM)

        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(arrayOf(wrongHostCert), "RSA")
        }
    }

    @Test
    fun testRejectionOfEmptyOrNullCertificateChain() {
        val tm = MpeiCertificates.createMpeiTrustManager("mail.mpei.ru")

        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(emptyArray(), "RSA")
        }

        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(null, "RSA")
        }
    }

    @Test
    fun testOsepMailClientSessionPropertiesAreStrictAndDoNotTrustHostBlindly() {
        val client = OsepMailClient()
        val session = client.createSession()
        val props = session.properties

        // КРИТИЧЕСКАЯ ПРОВЕРКА БЕЗОПАСНОСТИ:
        // 1. mail.imaps.ssl.trust ОБЯЗАН отсутствовать, чтобы JavaMail не отключал проверку цепочки CA!
        assertNull(
            "mail.imaps.ssl.trust must NEVER be set because it bypasses CA chain validation!",
            props.getProperty("mail.imaps.ssl.trust")
        )

        // 2. Включена строгая проверка соответствия имени хоста
        assertEquals("true", props.getProperty("mail.imaps.ssl.checkserveridentity"))

        // 3. Установлена защищённая сокет-фабрика на основе цепочки сертификатов МЭИ
        assertNotNull(props.get("mail.imaps.ssl.socketFactory"))

        // 4. Запрещён автоматический откат на незащищённый стандартный сокет
        assertEquals("false", props.getProperty("mail.imaps.ssl.socketFactory.fallback"))
    }
}
