package ru.mpei.assistant.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

interface DataCrypto {
    fun encrypt(plainText: String?): String?
    fun decrypt(cipherText: String?): String?
}

object KeystoreCrypto : DataCrypto by AesGcmCrypto(
    AndroidKeystoreKeyProvider()
) {
    class EncryptionException(cause: Throwable) :
        Exception("Не удалось зашифровать данные", cause)

    class DecryptionException(cause: Throwable) :
        Exception("Не удалось расшифровать данные", cause)
}

internal class AesGcmCrypto(
    private val keyProvider: KeyProvider
) : DataCrypto {

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
        private const val PREFIX = "ENC:"
    }

    override fun encrypt(plainText: String?): String? {
        if (plainText.isNullOrEmpty()) return plainText

        return try {
            val key = keyProvider.getKey(createIfMissing = true)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv
            check(iv.size == IV_LENGTH) { "Некорректная длина IV" }
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            PREFIX + Base64.getEncoder().encodeToString(iv + encrypted)
        } catch (e: Exception) {
            throw KeystoreCrypto.EncryptionException(e)
        }
    }

    override fun decrypt(cipherText: String?): String? {
        // Обратная совместимость со старыми незашифрованными записями.
        if (cipherText.isNullOrEmpty() || !cipherText.startsWith(PREFIX)) {
            return cipherText
        }

        return try {
            val raw = Base64.getDecoder().decode(cipherText.removePrefix(PREFIX))
            require(raw.size >= IV_LENGTH + GCM_TAG_LENGTH / 8) {
                "Некорректная длина зашифрованных данных"
            }

            val key = keyProvider.getKey(createIfMissing = false)
            val iv = raw.copyOfRange(0, IV_LENGTH)
            val encrypted = raw.copyOfRange(IV_LENGTH, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            throw KeystoreCrypto.DecryptionException(e)
        }
    }
}
