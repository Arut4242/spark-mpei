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

class CryptoException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

interface KeyProvider {
    fun getKey(): SecretKey
}

internal class AndroidKeystoreKeyProvider(
    private val keyAlias: String = KeystoreCrypto.KEY_ALIAS
) : KeyProvider {
    override fun getKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(KeystoreCrypto.ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KeystoreCrypto.ANDROID_KEYSTORE
                )
                val keyGenSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenSpec)
                keyGenerator.generateKey()
            } else {
                val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
                    ?: throw CryptoException("Ключ '$keyAlias' в AndroidKeyStore не является SecretKey или повреждён")
                entry.secretKey
            }
        } catch (e: Exception) {
            AppLogger.e("KEYSTORE_CRYPTO", "AndroidKeyStore hardware error: key unavailable", e)
            throw CryptoException("Аппаратное хранилище AndroidKeyStore недоступно. Операция прервана в целях безопасности.", e)
        }
    }
}

/**
 * Криптографический модуль для шифрования чувствительных данных
 * с использованием аппаратного Android Keystore (AES-256 GCM).
 *
 * Безопасность:
 * - В production отсутствует статический тестовый ключ (jvmTestFallbackKey удалён).
 * - При недоступности Keystore операция прерывается исключением CryptoException.
 * - При ошибке шифрования возврат открытого текста строго запрещён.
 */
object KeystoreCrypto {

    private const val TAG = "KEYSTORE_CRYPTO"
    internal const val KEY_ALIAS = "spark_db_encryption_key"
    internal const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val PREFIX = "ENC:"

    @Volatile
    private var keyProvider: KeyProvider = AndroidKeystoreKeyProvider()

    /**
     * Позволяет установить альтернативный KeyProvider исключительно в Unit-тестах на JVM.
     * В production-коде не вызывается (дефолтный провайдер использует аппаратный AndroidKeyStore).
     */
    @androidx.annotation.VisibleForTesting
    fun setKeyProviderForTesting(provider: KeyProvider?) {
        keyProvider = provider ?: AndroidKeystoreKeyProvider()
    }

    /**
     * Шифрует открытый текст с использованием AES-256 GCM.
     * Возвращает строку с префиксом "ENC:", содержащую 12-байтовый IV + шифротекст в Base64.
     *
     * При сбое шифрования выбрасывает CryptoException. Возврат исходного открытого текста строго запрещён!
     */
    @Throws(CryptoException::class)
    fun encrypt(plainText: String?): String? {
        if (plainText.isNullOrEmpty()) return plainText
        return try {
            val secretKey = keyProvider.getKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

            val b64 = Base64.getEncoder().encodeToString(combined)
            PREFIX + b64
        } catch (e: CryptoException) {
            AppLogger.e(TAG, "Encryption rejected: Keystore unavailable", e)
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Encryption failed: sensitive data will NOT be stored in plaintext", e)
            throw CryptoException("Ошибка шифрования конфиденциальных данных", e)
        }
    }

    /**
     * Расшифровывает строку. Если строка не зашифрована (нет префикса "ENC:"),
     * возвращает её исходное значение (обратная совместимость для плавной миграции).
     *
     * При ошибке расшифровки возвращает null (но не шифротекст и не повреждённые данные).
     */
    fun decrypt(cipherText: String?): String? {
        if (cipherText.isNullOrEmpty() || !cipherText.startsWith(PREFIX)) return cipherText
        return try {
            val secretKey = keyProvider.getKey()
            val b64 = cipherText.removePrefix(PREFIX)
            val raw = Base64.getDecoder().decode(b64)
            if (raw.size <= 12) {
                AppLogger.e(TAG, "Decryption error: invalid ciphertext payload size (${raw.size} bytes)")
                return null
            }

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
            null
        }
    }
}
