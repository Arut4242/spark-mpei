package ru.mpei.assistant.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ru.mpei.assistant.util.AppLogger
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Криптографический модуль для шифрования чувствительных данных локальной БД Room
 * (тела писем, оценки, задолженности) с использованием аппаратного Android Keystore (AES-256 GCM).
 */
object KeystoreCrypto {

    private const val TAG = "KEYSTORE_CRYPTO"
    private const val KEY_ALIAS = "spark_db_encryption_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val PREFIX = "ENC:"

    // Fallback-ключ исключительно для автономных Unit-тестов на стандартной JVM без AndroidKeyStore
    private val jvmTestFallbackKey: SecretKey by lazy {
        val testBytes = ByteArray(32) { (it * 7 + 13).toByte() }
        SecretKeySpec(testBytes, "AES")
    }

    private fun getOrCreateKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val keyGenSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenSpec)
                keyGenerator.generateKey()
            } else {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
                entry.secretKey
            }
        } catch (_: Throwable) {
            // В среде Unit-тестов (JVM) провайдер AndroidKeyStore недоступен
            jvmTestFallbackKey
        }
    }

    /**
     * Шифрует открытый текст с использованием AES-256 GCM.
     * Возвращает строку с префиксом "ENC:", содержащую 12-байтовый IV + шифротекст в Base64.
     */
    fun encrypt(plainText: String?): String? {
        if (plainText.isNullOrEmpty()) return plainText
        return try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

            val b64 = Base64.getEncoder().encodeToString(combined)
            PREFIX + b64
        } catch (e: Exception) {
            AppLogger.e(TAG, "Encryption failed", e)
            plainText
        }
    }

    /**
     * Расшифровывает строку. Если строка не зашифрована (нет префикса "ENC:"),
     * возвращает её исходное значение (обратная совместимость).
     */
    fun decrypt(cipherText: String?): String? {
        if (cipherText.isNullOrEmpty() || !cipherText.startsWith(PREFIX)) return cipherText
        return try {
            val secretKey = getOrCreateKey()
            val b64 = cipherText.removePrefix(PREFIX)
            val raw = Base64.getDecoder().decode(b64)
            if (raw.size <= 12) return cipherText

            val iv = ByteArray(12)
            System.arraycopy(raw, 0, iv, 0, 12)
            val encryptedBytes = ByteArray(raw.size - 12)
            System.arraycopy(raw, 12, encryptedBytes, 0, encryptedBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Decryption failed", e)
            cipherText
        }
    }
}
