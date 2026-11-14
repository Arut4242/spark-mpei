package ru.mpei.assistant.data.local

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.mpei.assistant.security.DatabaseKeyStorage
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "encryption-test-${UUID.randomUUID()}.db"
    private val file get() = context.getDatabasePath(name)
    private val password = UUID.randomUUID().toString().toByteArray()
    private val marker = "CONFIDENTIAL_${UUID.randomUUID()}"

    @Before fun loadLibrary() {
        System.loadLibrary("sqlcipher")
        file.parentFile!!.mkdirs()
    }
    @After fun cleanUp() {
        context.deleteDatabase(name)
        File(file.path + ".encrypting").delete()
        password.fill(0)
    }

    private fun plaintextRoom(): AppDatabase = Room.databaseBuilder(
        context, AppDatabase::class.java, name
    ).build()

    private fun encryptedRoom(): AppDatabase = Room.databaseBuilder(
        context, AppDatabase::class.java, name
    ).openHelperFactory(PreservingSqlCipherFactory(password.copyOf()))
        .addMigrations(AppDatabase.MIGRATION_5_6)
        .build()

    private fun seed(db: SupportSQLiteDatabase) {
        // Populate every column, so subjects/scores/attendance/mail/attachment metadata
        // are all covered without coupling this test to UI/model constructors.
        for (table in listOf("bars_subjects", "subject_absences", "emails", "schedule_lessons")) {
            val values = ContentValues()
            db.query("PRAGMA table_info(`$table`)").use { columns ->
                while (columns.moveToNext()) {
                    val column = columns.getString(1)
                    val type = columns.getString(2)
                    when {
                        type.contains("INT", true) -> values.put(column, 1)
                        type.contains("REAL", true) -> values.put(column, 1.5)
                        else -> values.put(column, "${marker}_$column")
                    }
                }
            }
            db.insert(table, 0, values)
        }
    }

    private fun snapshot(db: SupportSQLiteDatabase): List<List<String?>> = buildList {
        for (table in listOf("bars_subjects", "subject_absences", "emails", "schedule_lessons")) {
            db.query("SELECT * FROM `$table`").use { rows ->
                while (rows.moveToNext()) add((0 until rows.columnCount).map { rows.getString(it) })
            }
        }
    }

    private fun assertNotReadableOnDisk() {
        for (candidate in listOf(file, File(file.path + "-wal"), File(file.path + "-journal"))) {
            if (!candidate.exists()) continue
            val bytes = candidate.readBytes()
            for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE)) {
                assertFalse("Sensitive marker found in ${candidate.name}", bytes.toString(charset).contains(marker))
            }
        }
        assertFalse(EncryptedDatabaseMigration.isPlaintext(file))
    }

    @Test fun plaintextMigrationPreservesEveryFieldAndCanReopenWithRoom() {
        val before = plaintextRoom().use { room ->
            val db = room.openHelper.writableDatabase
            seed(db)
            snapshot(db)
        }
        assertTrue(EncryptedDatabaseMigration.isPlaintext(file))
        EncryptedDatabaseMigration.prepare(file, password)
        encryptedRoom().use { room ->
            assertEquals(before, snapshot(room.openHelper.writableDatabase))
            // Check new writes as well, including encrypted WAL pages while open.
            room.openHelper.writableDatabase.execSQL(
                "UPDATE emails SET subject = ?", arrayOf<Any>(marker + "_new_subject")
            )
            assertNotReadableOnDisk()
        }
        assertNotReadableOnDisk()
        EncryptedDatabaseMigration.prepare(file, password) // Repeat startup is idempotent.
        encryptedRoom().use { assertEquals(6, it.openHelper.writableDatabase.version) }
    }

    @Test fun sourceWalIsIncluded() {
        val plain = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
        plain.enableWriteAheadLogging()
        plain.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { it.moveToFirst() }
        plain.execSQL("CREATE TABLE pending(value TEXT)")
        plain.execSQL("INSERT INTO pending VALUES (?)", arrayOf<Any>(marker))
        // Recreate a quiescent crash snapshot with a committed, uncheckpointed WAL.
        val mainBytes = file.readBytes()
        val walFile = File(file.path + "-wal")
        val walBytes = walFile.readBytes()
        assertTrue(walBytes.isNotEmpty())
        plain.close()
        file.writeBytes(mainBytes)
        walFile.writeBytes(walBytes)
        File(file.path + "-shm").delete()
        EncryptedDatabaseMigration.prepare(file, password)
        SQLiteDatabase.openDatabase(file.path, password, null, SQLiteDatabase.OPEN_READONLY,
            DatabaseErrorHandler { error("Corruption") }, null).use {
            it.rawQuery("SELECT value FROM pending", emptyArray<String>()).use { rows ->
                assertTrue(rows.moveToFirst())
                assertEquals(marker, rows.getString(0))
            }
        }
        assertNotReadableOnDisk()
    }

    @Test fun olderSchemaStillRunsRoomMigrationAfterEncryption() {
        plaintextRoom().use { room ->
            val db = room.openHelper.writableDatabase
            db.execSQL("DROP TABLE subject_absences")
            db.execSQL("CREATE TABLE subject_absences (subjectName TEXT NOT NULL PRIMARY KEY, " +
                "unexcusedPairs INTEGER NOT NULL, excusedPairs INTEGER NOT NULL, totalSemesterPairs INTEGER NOT NULL)")
            db.execSQL("INSERT INTO subject_absences VALUES (?, 2, 1, 16)", arrayOf<Any>(marker))
            db.version = 5
        }
        EncryptedDatabaseMigration.prepare(file, password)
        encryptedRoom().use { room ->
            val db = room.openHelper.writableDatabase
            assertEquals(6, db.version)
            db.query("SELECT subjectName, unexcusedPairs, controlType FROM subject_absences").use {
                assertTrue(it.moveToFirst())
                assertEquals(marker, it.getString(0))
                assertEquals(2, it.getInt(1))
                assertEquals("", it.getString(2))
            }
        }
        assertNotReadableOnDisk()
    }

    @Test fun staleExportIsRebuiltFromTheOriginal() {
        plaintextRoom().use { seed(it.openHelper.writableDatabase) }
        File(file.path + ".encrypting").writeText("interrupted export")
        EncryptedDatabaseMigration.prepare(file, password)
        encryptedRoom().use { assertEquals(4, snapshot(it.openHelper.writableDatabase).size) }
        assertFalse(File(file.path + ".encrypting").exists())
    }

    @Test fun exportPreparationFailurePreservesThePlaintextOriginal() {
        plaintextRoom().use { seed(it.openHelper.writableDatabase) }
        val before = file.readBytes()
        val blocked = File(file.path + ".encrypting").apply { mkdirs() }
        val obstacle = File(blocked, "obstacle").apply { writeText("test") }
        try {
            assertTrue(runCatching { EncryptedDatabaseMigration.prepare(file, password) }.isFailure)
            assertArrayEquals(before, file.readBytes())
            plaintextRoom().use { assertEquals(4, snapshot(it.openHelper.writableDatabase).size) }
        } finally {
            obstacle.delete()
            blocked.delete()
        }
    }

    @Test fun wrongKeyDoesNotDeleteOrReplaceTheDatabase() {
        plaintextRoom().use { seed(it.openHelper.writableDatabase) }
        EncryptedDatabaseMigration.prepare(file, password)
        val before = file.readBytes()
        val failed = runCatching {
            EncryptedDatabaseMigration.prepare(file, "wrong-key".toByteArray())
        }
        assertTrue(failed.isFailure)
        assertArrayEquals(before, file.readBytes())
        encryptedRoom().use { assertEquals(4, snapshot(it.openHelper.writableDatabase).size) }
    }

    @Test fun wrappedPassphraseSurvivesReloadAndIsNotStoredInPlaintext() {
        val dir = File(context.cacheDir, "key-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = dir
        }
        try {
            val first = DatabaseKeyStorage.loadOrCreate(isolated, false)
            val second = DatabaseKeyStorage.loadOrCreate(isolated, true)
            assertArrayEquals(first, second)
            val stored = File(dir, "sqlcipher-passphrase.enc").readText()
            assertTrue(stored.startsWith("ENC:"))
            assertFalse(stored.contains(first.toString(Charsets.UTF_8)))
            first.fill(0)
            second.fill(0)
        } finally { dir.deleteRecursively() }
    }

    @Test fun missingWrappedKeyIsNotRegeneratedForExistingEncryptedDatabase() {
        val dir = File(context.cacheDir, "missing-key-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = dir
        }
        try {
            assertTrue(runCatching { DatabaseKeyStorage.loadOrCreate(isolated, true) }.isFailure)
            assertFalse(File(dir, "sqlcipher-passphrase.enc").exists())
        } finally { dir.deleteRecursively() }
    }
}
