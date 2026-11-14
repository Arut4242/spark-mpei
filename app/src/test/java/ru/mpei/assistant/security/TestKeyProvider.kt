package ru.mpei.assistant.security

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class TestKeyProvider : KeyProvider {
    private var key: SecretKey? = null

    override fun getKey(createIfMissing: Boolean): SecretKey {
        key?.let { return it }
        check(createIfMissing) { "Тестовый ключ отсутствует" }

        return KeyGenerator.getInstance("AES").run {
            init(256)
            generateKey()
        }.also { key = it }
    }
}
