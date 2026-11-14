package ru.mpei.assistant.data.local

import android.system.Os
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.RandomAccessFile

/** Must run before Room opens the file and while no other connection is open. */
internal object EncryptedDatabaseMigration {
    private val header = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val preserveOnCorruption = DatabaseErrorHandler {
        throw IllegalStateException("Database is unreadable; automatic deletion is disabled")
    }

    fun isPlaintext(file: File): Boolean {
        if (!file.exists() || file.length() < header.size) return false
        return file.inputStream().use { input ->
            val bytes = ByteArray(header.size)
            input.read(bytes) == header.size && bytes.contentEquals(header)
        }
    }

    private fun open(file: File, password: ByteArray, readOnly: Boolean = false): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            file.absolutePath, password, null,
            if (readOnly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE,
            preserveOnCorruption, null
        )

    private fun counts(db: SQLiteDatabase): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", emptyArray<String>()).use { tables ->
            while (tables.moveToNext()) {
                val name = tables.getString(0)
                val quoted = "\"" + name.replace("\"", "\"\"") + "\""
                db.rawQuery("SELECT count(*) FROM $quoted", emptyArray<String>()).use {
                    check(it.moveToFirst())
                    result[name] = it.getLong(0)
                }
            }
        }
        return result
    }

    private fun verify(file: File, password: ByteArray, expected: Map<String, Long>? = null, version: Int? = null) {
        open(file, password, readOnly = true).use { db ->
            db.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use {
                check(it.moveToFirst() && it.getString(0) == "ok") { "Database integrity check failed" }
            }
            db.rawQuery("PRAGMA cipher_integrity_check", emptyArray<String>()).use {
                check(!it.moveToFirst()) { "Encrypted page authentication failed" }
            }
            if (expected != null) check(counts(db) == expected) { "Migration row counts differ" }
            if (version != null) check(db.version == version) { "Database version was not preserved" }
        }
    }

    private fun deleteSidecars(file: File) {
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val sidecar = File(file.path + suffix)
            check(!sidecar.exists() || sidecar.delete()) { "Cannot remove closed database sidecar" }
        }
    }

    fun prepare(file: File, password: ByteArray) {
        val temporary = File(file.parentFile, file.name + ".encrypting")
        if (!file.exists()) {
            // An interrupted export is never the only authoritative copy.
            check(!temporary.exists()) { "Orphan migration copy found; manual recovery required" }
            return
        }
        check(file.length() > 0) { "Existing database is empty; refusing to replace it" }
        if (!isPlaintext(file)) {
            verify(file, password)
            // A stale encrypted export can remain after interruption, never a plaintext backup.
            deleteSidecars(temporary)
            check(!temporary.exists() || temporary.delete())
            return
        }

        // The original remains authoritative until the atomic rename below.
        deleteSidecars(temporary)
        check(!temporary.exists() || temporary.delete())
        try {
            lateinit var expected: Map<String, Long>
            var version = 0
            open(file, byteArrayOf()).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray<String>()).use {
                    if (it.moveToFirst()) check(it.getInt(0) == 0) { "Database is busy" }
                }
                db.rawQuery("PRAGMA journal_mode=DELETE", emptyArray<String>()).use {
                    check(it.moveToFirst() && it.getString(0).equals("delete", true))
                }
                version = db.version
                db.execSQL(
                    "ATTACH DATABASE ? AS encrypted KEY ?",
                    arrayOf<Any>(temporary.absolutePath, password.toString(Charsets.UTF_8))
                )
                try {
                    db.beginTransaction()
                    try {
                        expected = counts(db)
                        db.rawQuery("SELECT sqlcipher_export('encrypted')", emptyArray<String>()).use {
                            check(it.moveToFirst()) { "SQLCipher export did not execute" }
                        }
                        db.execSQL("PRAGMA encrypted.user_version = $version")
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                } finally {
                    db.execSQL("DETACH DATABASE encrypted")
                }
            }

            verify(temporary, password, expected, version)
            RandomAccessFile(temporary, "rw").use { it.fd.sync() }
            // Closed source has been checkpointed, so deleting its sidecars is safe.
            deleteSidecars(file)
            // Same-directory POSIX rename atomically replaces the old file, without a plaintext backup.
            Os.rename(temporary.absolutePath, file.absolutePath)
            val directory = Os.open(file.parentFile!!.absolutePath, android.system.OsConstants.O_RDONLY, 0)
            try { Os.fsync(directory) } finally { Os.close(directory) }
        } catch (e: Exception) {
            // Never delete/rename the original on failure. Retry starts from that original.
            temporary.delete()
            deleteSidecars(temporary)
            throw e
        }
    }
}
