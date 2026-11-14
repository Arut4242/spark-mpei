package ru.mpei.assistant.security

import javax.crypto.SecretKey

interface KeyProvider {
    fun getKey(createIfMissing: Boolean): SecretKey
}
