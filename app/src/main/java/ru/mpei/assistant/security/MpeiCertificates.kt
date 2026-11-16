package ru.mpei.assistant.security

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object MpeiCertificates {

    const val MPEI_ROOT_CA_PEM = """-----BEGIN CERTIFICATE-----
MIIEujCCA6KgAwIBAgIQfNNQ6Nka751Pp9NiM3+FNzANBgkqhkiG9w0BAQUFADBB
MRUwEwYKCZImiZPyLGQBGRYFbG9jYWwxFDASBgoJkiaJk/IsZAEZFgRtcGVpMRIw
EAYDVQQDEwlNUEVJIChUVSkwHhcNMDMwOTAxMTIzMzQxWhcNMzEwMTE5MTk0MzIw
WjBBMRUwEwYKCZImiZPyLGQBGRYFbG9jYWwxFDASBgoJkiaJk/IsZAEZFgRtcGVp
MRIwEAYDVQQDEwlNUEVJIChUVSkwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK
AoIBAQD8Lwj/TBgYcwEKLjbuHiPQvK2V5Hs0OjPoqmKpWu/+d5tRItOJN6JZGnb3
0sdxV8ybAo3aQeJwIhStjswFUNsFKrOJmHOTmsg2wk8WOMS4tmcZtzb3H3AQIxnH
DQWa6/gP+CpkCfeRKdw8S0UWSGgd6PInE8aLtLD5cFwpTcTTUQUyZOAkXNiiUEkm
k+goqaq3D3oSgDF7JWKPsg2pTtLm5XVAMH47ElgMTOBJCO8NgUUOvl3WeijN+4aU
X1bDqSboN7Wy95xmnuDqlAP8LveeMYjYjOsdKZ8c5yJmB59OQFERxk6iXB3Hqlmp
/Q8j07scVJDSO8v1rUUAuP6qGl6bAgMBAAGjggGsMIIBqDATBgkrBgEEAYI3FAIE
Bh4EAEMAQTALBgNVHQ8EBAMCAUYwDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4EFgQU
sf8ns3rMEGPRzW8JRWjNhY18LrIwggEbBgNVHR8EggESMIIBDjCCAQqgggEGoIIB
AoaBvWxkYXA6Ly8vQ049TVBFSSUyMCEwMDI4VFUhMDAyOSxDTj1yb290Y2VydHNy
dixDTj1DRFAsQ049UHVibGljJTIwS2V5JTIwU2VydmljZXMsQ049U2VydmljZXMs
Q049Q29uZmlndXJhdGlvbixEQz1tcGVpLERDPWxvY2FsP2NlcnRpZmljYXRlUmV2
b2NhdGlvbkxpc3Q/YmFzZT9vYmplY3RDbGFzcz1jUkxEaXN0cmlidXRpb25Qb2lu
dIZAaHR0cDovL3Jvb3RjZXJ0c3J2Lm1wZWkubG9jYWwvQ2VydEVucm9sbC9NUEVJ
JTIwITAwMjhUVSEwMDI5LmNybDAQBgkrBgEEAYI3FQEEAwIBAjAjBgkrBgEEAYI3
FQIEFgQUArjEzgtjWYT5E1Jd/6s3vubDKuswDQYJKoZIhvcNAQEFBQADggEBAOqH
iLE6/DlgpeeW9TNM1rIG/ancfbSnAIWy1TxB0RgqBMGrbYl1Y2/bmFORTxqlwttz
R0ZLoqg1Ud5QOAgWAKY8Kb+f4cGIuIlDio7FFOJPPTwQRET5Hp5a59f7J7Muqq5U
0UQB1Kw+/TTGP/eEFh9p2wFifcgsRGQ/xQoQt5AduEZDoA6/DArcaqRclubH2kBl
+Z8PgVTmZEioIat/T8qZuZgo/rG53nOAcz7o1gRykB3aBs8J1I4bJvYOKRCvjqRR
HJ6oGl4fna+unW/k5vUGgw+/Awo2aRXCSkpCSfu6CQ7flza42kGMFowQhosSnwGK
em1R8ipEyraXPkuuxNg=
-----END CERTIFICATE-----"""

    const val MPEI_INTERMEDIATE_CA_PEM = """-----BEGIN CERTIFICATE-----
MIIGLjCCBRagAwIBAgIKUdCQiwACAAJwnDANBgkqhkiG9w0BAQUFADBBMRUwEwYK
CZImiZPyLGQBGRYFbG9jYWwxFDASBgoJkiaJk/IsZAEZFgRtcGVpMRIwEAYDVQQD
EwlNUEVJIChUVSkwHhcNMjUwMTE4MTcyOTI1WhcNMjcwMTE4MTczOTI1WjBiMRUw
EwYKCZImiZPyLGQBGRYFbG9jYWwxFDASBgoJkiaJk/IsZAEZFgRtcGVpMRYwFAYK
CZImiZPyLGQBGRYGcHVibGljMRswGQYDVQQDExJwdWJsaWMtUFVCTElDQ0EtQ0Ew
ggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCMWN+HjZPyf0a6X4f69QkW
f3ngqrB7zU+j+xzCqTngw337NgSe7BcbxBfo92ImEmtLTm2BxGYZEjx3qtgdtcoj
Kozx5treTqxmHRbj0FYvUUnUpvGBIdL007Ke19KNlfwZkySBFe1KGolq9NuVUxNJ
B3y9zQJX2Bd9bActm4Bs8PqDnMVLWlBmGewyhYjnDUkSEMmTXDNjYt6h54wQcPGf
p0D42dZ/F+pOKiVRyobsnfqdMrvzhA1JAO2DKbTYwWvWQmvUV3KT5DWtR+mRhRN3
FYSho1vUJ+V+sC3WPLkW1qTTOzGJJkVGKNmF+IHaow3jKVbmG1pSN4G5BSxOsppl
AgMBAAGjggMFMIIDATAQBgkrBgEEAYI3FQEEAwIBAjAjBgkrBgEEAYI3FQIEFgQU
oA/5lwSPUaC2rvUE3XOf6r8WVz4wHQYDVR0OBBYEFPAXcZOHRZQeKdaUoFUid00h
ekm6MBkGCSsGAQQBgjcUAgQMHgoAUwB1AGIAQwBBMAsGA1UdDwQEAwIBhjAPBgNV
HRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFLH/J7N6zBBj0c1vCUVozYWNfC6yMIIB
GwYDVR0fBIIBEjCCAQ4wggEKoIIBBqCCAQKGgb1sZGFwOi8vL0NOPU1QRUklMjAh
MDAyOFRVITAwMjksQ049cm9vdGNlcnRzcnYsQ049Q0RQLENOPVB1YmxpYyUyMEtl
eSUyMFNlcnZpY2VzLENOPVNlcnZpY2VzLENOPUNvbmZpZ3VyYXRpb24sREM9bXBl
aSxEQz1sb2NhbD9jZXJ0aWZpY2F0ZVJldm9jYXRpb25MaXN0P2Jhc2U/b2JqZWN0
Q2xhc3M9Y1JMRGlzdHJpYnV0aW9uUG9pbnSGQGh0dHA6Ly9yb290Y2VydHNydi5t
cGVpLmxvY2FsL0NlcnRFbnJvbGwvTVBFSSUyMCEwMDI4VFUhMDAyOS5jcmwwggEu
BggrBgEFBQcBAQSCASAwggEcMIGxBggrBgEFBQcwAoaBpGxkYXA6Ly8vQ049TVBF
SSUyMCEwMDI4VFUhMDAyOSxDTj1BSUEsQ049UHVibGljJTIwS2V5JTIwU2Vydmlj
ZXMsQ049U2VydmljZXMsQ049Q29uZmlndXJhdGlvbixEQz1tcGVpLERDPWxvY2Fs
P2NBQ2VydGlmaWNhdGU/YmFzZT9vYmplY3RDbGFzcz1jZXJ0aWZpY2F0aW9uQXV0
aG9yaXR5MGYGCCsGAQUFBzAChlpodHRwOi8vcm9vdGNlcnRzcnYubXBlaS5sb2Nh
bC9DZXJ0RW5yb2xsL3Jvb3RjZXJ0c3J2Lm1wZWkubG9jYWxfTVBFSSUyMCEwMDI4
VFUhMDAyOSgyKS5jcnQwDQYJKoZIhvcNAQEFBQADggEBAIbdKgzaGaQZw81qdUDL
ItQtkJm/DBF5qvSU4sO2dTTZJp8zB8LqChySbuxS3KRovM4AhqJhVRnfUso5ashi
YmVjvsIV5XJM3qeF64R0Uy6AW7hd1rV9vterfe/3Xxn1WJuXrvVMnWpXrX0iPbUg
O2vM69lJiOAMn1u3vQ7pW7hB2ZU5KWYguq8naaa/txLHewagstTEyL7AaMmH3SDy
Fx7wTWxKPF4EIhy+GwP47IIo/Ha3jg+fkOvpiWQ5gzSG+EL1n4a50GIbGUxt25MT
NtKxgokscqS+POg/vbtKgld0nvUr6k5OLUUJb7+rzdknkDcvgWm1zsS9oNCYIKOI
a+s=
-----END CERTIFICATE-----"""

    private val cf: CertificateFactory by lazy { CertificateFactory.getInstance("X.509") }

    fun getMpeiCertificates(): List<X509Certificate> {
        val root = cf.generateCertificate(ByteArrayInputStream(MPEI_ROOT_CA_PEM.toByteArray(Charsets.UTF_8))) as X509Certificate
        val intermediate = cf.generateCertificate(ByteArrayInputStream(MPEI_INTERMEDIATE_CA_PEM.toByteArray(Charsets.UTF_8))) as X509Certificate
        return listOf(root, intermediate)
    }

    fun createMpeiTrustManager(imapHost: String = "mail.mpei.ru"): X509TrustManager {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
        }

        // Подключаем системные доверенные корневые сертификаты Android
        try {
            val systemTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(null as KeyStore?)
            }
            for (tm in systemTmf.trustManagers) {
                if (tm is X509TrustManager) {
                    for (cert in tm.acceptedIssuers) {
                        try {
                            keyStore.setCertificateEntry(cert.subjectX500Principal.name, cert)
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        // Добавляем официальные доверенные сертификаты НИУ «МЭИ» (Root CA и Intermediate SubCA)
        for ((idx, cert) in getMpeiCertificates().withIndex()) {
            keyStore.setCertificateEntry("mpei_ca_$idx", cert)
        }

        val customTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        val delegate = customTmf.trustManagers.filterIsInstance<X509TrustManager>().first()

        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                delegate.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain.isNullOrEmpty()) {
                    throw CertificateException("Цепочка сертификатов сервера пуста")
                }

                // 1. Криптографическая валидация всей цепочки сертификатов
                delegate.checkServerTrusted(chain, authType)

                // 2. Проверка хоста (mail.mpei.ru) в SAN или CN конечного сертификата
                val leaf = chain[0]
                val sans = try {
                    leaf.subjectAlternativeNames?.mapNotNull { it.getOrNull(1)?.toString() } ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
                val matchesHost = sans.any { it.equals(imapHost, ignoreCase = true) } ||
                        leaf.subjectX500Principal.name.contains(imapHost, ignoreCase = true)

                if (!matchesHost) {
                    throw CertificateException("Сертификат сервера ($leaf) не содержит имя хоста '$imapHost' в SAN или CN")
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
        }
    }

    fun createMpeiSslContext(imapHost: String = "mail.mpei.ru"): SSLContext {
        val tm = createMpeiTrustManager(imapHost)
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(tm), SecureRandom())
        }
    }
}
