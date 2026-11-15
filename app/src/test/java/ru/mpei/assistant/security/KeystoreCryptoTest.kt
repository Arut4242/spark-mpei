package ru.mpei.assistant.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeystoreCryptoTest {

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
}
