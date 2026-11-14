package ru.mpei.assistant.security

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyStoreException
import java.util.Base64

class KeystoreCryptoTest {
    private val crypto = AesGcmCrypto(TestKeyProvider())

    @Test
    fun roundtrip() {
        val original = "Конфиденциальное письмо"
        val encrypted = checkNotNull(crypto.encrypt(original))
        assertNotEquals(original, encrypted)
        assertTrue(encrypted.startsWith("ENC:"))
        assertEquals(original, crypto.decrypt(encrypted))
    }

    @Test
    fun repeatedEncryptionUsesDifferentIv() {
        val first = crypto.encrypt("Одинаковый текст")
        val second = crypto.encrypt("Одинаковый текст")
        assertNotEquals(first, second)
        assertEquals("Одинаковый текст", crypto.decrypt(first))
        assertEquals("Одинаковый текст", crypto.decrypt(second))
    }

    @Test
    fun nullAndEmptyValues() {
        assertNull(crypto.encrypt(null))
        assertNull(crypto.decrypt(null))
        assertEquals("", crypto.encrypt(""))
        assertEquals("", crypto.decrypt(""))
    }

    @Test
    fun legacyPlaintextRemainsReadable() {
        assertEquals("Старые данные", crypto.decrypt("Старые данные"))
    }

    @Test(expected = KeystoreCrypto.EncryptionException::class)
    fun unavailableKeyProviderStopsEncryption() {
        val provider = object : KeyProvider {
            override fun getKey(createIfMissing: Boolean) =
                throw KeyStoreException("Keystore недоступен")
        }
        AesGcmCrypto(provider).encrypt("Секретные данные")
    }

    @Test(expected = KeystoreCrypto.DecryptionException::class)
    fun unavailableKeyProviderStopsDecryption() {
        val encrypted = crypto.encrypt("Секретные данные")
        val provider = object : KeyProvider {
            override fun getKey(createIfMissing: Boolean) =
                throw KeyStoreException("Keystore недоступен")
        }
        AesGcmCrypto(provider).decrypt(encrypted)
    }

    @Test(expected = KeystoreCrypto.DecryptionException::class)
    fun missingKeyIsNotRecreatedDuringDecryption() {
        val encrypted = crypto.encrypt("Секретные данные")
        AesGcmCrypto(TestKeyProvider()).decrypt(encrypted)
    }

    @Test(expected = KeystoreCrypto.DecryptionException::class)
    fun modifiedCiphertextIsRejected() {
        val encrypted = checkNotNull(crypto.encrypt("Секретные данные"))
        val bytes = Base64.getDecoder().decode(encrypted.removePrefix("ENC:"))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        crypto.decrypt("ENC:" + Base64.getEncoder().encodeToString(bytes))
    }
}
