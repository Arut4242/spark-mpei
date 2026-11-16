package ru.mpei.assistant.test

import ru.mpei.assistant.security.KeyProvider
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Тестовый провайдер ключа шифрования, используемый исключительно в Unit-тестах на стандартной JVM.
 * Расположен в test-исходниках и полностью исключён из production и release APK.
 */
class JvmTestKeyProvider : KeyProvider {
    private val secretKey: SecretKey by lazy {
        val testBytes = ByteArray(32) { (it * 7 + 13).toByte() }
        SecretKeySpec(testBytes, "AES")
    }

    override fun getKey(): SecretKey = secretKey
}
