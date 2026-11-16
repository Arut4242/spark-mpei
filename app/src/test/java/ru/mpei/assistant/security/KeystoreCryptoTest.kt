package ru.mpei.assistant.security

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.mpei.assistant.test.JvmTestKeyProvider
import javax.crypto.SecretKey

class KeystoreCryptoTest {

    @Before
    fun setup() {
        KeystoreCrypto.setKeyProviderForTesting(JvmTestKeyProvider())
    }

    @After
    fun tearDown() {
        KeystoreCrypto.setKeyProviderForTesting(null)
    }

    @Test
    fun testEncryptionAndDecryptionRoundtrip() {
        val originalText = "Секретное письмо с оценками за семестр: Математический анализ - 5 (отлично)"
        val encrypted = KeystoreCrypto.encrypt(originalText)

        assertNotEquals(originalText, encrypted)
        assertTrue(encrypted!!.startsWith("ENC:"))

        val decrypted = KeystoreCrypto.decrypt(encrypted)
        assertEquals(originalText, decrypted)
    }

    @Test
    fun testNullAndEmptyHandling() {
        assertNull(KeystoreCrypto.encrypt(null))
        assertNull(KeystoreCrypto.decrypt(null))
        assertEquals("", KeystoreCrypto.encrypt(""))
        assertEquals("", KeystoreCrypto.decrypt(""))
    }

    @Test
    fun testBackwardsCompatibilityWithPlaintext() {
        val legacyPlaintext = "<html><body>Обычное незашифрованное письмо</body></html>"
        val decrypted = KeystoreCrypto.decrypt(legacyPlaintext)
        assertEquals(legacyPlaintext, decrypted)
    }

    @Test
    fun testEncryptionFailureThrowsCryptoExceptionAndNeverReturnsPlaintext() {
        // Симулируем отказ KeyStore/провайдера ключа
        KeystoreCrypto.setKeyProviderForTesting(object : KeyProvider {
            override fun getKey(): SecretKey {
                throw CryptoException("Hardware Keystore unavailable")
            }
        })

        val sensitiveData = "5.0 (Отлично)"
        assertThrows(CryptoException::class.java) {
            KeystoreCrypto.encrypt(sensitiveData)
        }
    }

    @Test
    fun testDecryptionFailureReturnsNull() {
        // Повреждённый зашифрованный текст (некорректный base64 или повреждённые байты GCM)
        val corruptedEncryptedText = "ENC:invalid_base64_payload_###"
        val decrypted = KeystoreCrypto.decrypt(corruptedEncryptedText)
        assertNull(decrypted)
    }
}
