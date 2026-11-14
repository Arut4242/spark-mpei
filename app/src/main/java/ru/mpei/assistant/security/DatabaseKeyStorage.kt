package ru.mpei.assistant.security

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.SecureRandom
import java.util.Base64

/** Stores only a Keystore-wrapped random SQLCipher passphrase, never plaintext. */
internal object DatabaseKeyStorage {
    private const val KEY_FILE = "sqlcipher-passphrase.enc"

    fun loadOrCreate(context: Context, encryptedDatabaseExists: Boolean): ByteArray {
        val file = File(context.noBackupFilesDir, KEY_FILE)
        val atomic = AtomicFile(file)
        if (file.exists() || File(file.path + ".bak").exists()) {
            val wrapped = atomic.readFully().toString(Charsets.UTF_8)
            check(wrapped.startsWith("ENC:")) { "Invalid wrapped database key" }
            val encoded = checkNotNull(KeystoreCrypto.decrypt(wrapped))
            val decoded = Base64.getDecoder().decode(encoded)
            try {
                check(decoded.size == 32) { "Invalid database key length" }
            } finally {
                decoded.fill(0)
            }
            return encoded.toByteArray(Charsets.UTF_8)
        }

        check(!encryptedDatabaseExists) {
            "Database key is missing; the existing database has been preserved"
        }
        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encoded = try {
            Base64.getEncoder().encodeToString(random)
        } finally {
            random.fill(0)
        }
        // Encryption must succeed BEFORE anything is written to disk.
        val wrapped = checkNotNull(KeystoreCrypto.encrypt(encoded))
        val stream = atomic.startWrite()
        try {
            stream.write(wrapped.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
        return encoded.toByteArray(Charsets.UTF_8)
    }
}
