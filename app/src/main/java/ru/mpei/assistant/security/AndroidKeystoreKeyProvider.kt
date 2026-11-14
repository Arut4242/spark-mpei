package ru.mpei.assistant.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class AndroidKeystoreKeyProvider : KeyProvider {

    companion object {
        private const val KEY_ALIAS = "spark_db_encryption_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    @Synchronized
    override fun getKey(createIfMissing: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null)
                as? KeyStore.SecretKeyEntry
                ?: error("Некорректный тип ключа шифрования")
            return entry.secretKey
        }

        check(createIfMissing) { "Ключ расшифровки отсутствует" }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        generator.init(specification)
        return generator.generateKey()
    }
}
