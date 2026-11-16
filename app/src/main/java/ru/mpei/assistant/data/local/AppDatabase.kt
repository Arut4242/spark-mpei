package ru.mpei.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import ru.mpei.assistant.data.model.absence.SubjectAbsence
import ru.mpei.assistant.data.model.bars.BarsSubject
import ru.mpei.assistant.data.model.email.EmailMessage
import ru.mpei.assistant.data.model.schedule.Lesson
import ru.mpei.assistant.data.model.schedule.LessonNote
import ru.mpei.assistant.util.AppLogger
import java.io.File
import java.io.FileInputStream

@Database(
    entities = [BarsSubject::class, Lesson::class, SubjectAbsence::class, EmailMessage::class, LessonNote::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun barsDao(): BarsDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun absenceDao(): AbsenceDao
    abstract fun emailDao(): EmailDao

    companion object {
        private const val TAG = "AppDatabase"
        const val DATABASE_NAME = "mpei_assistant.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `subject_absences` (
                        `subjectName` TEXT NOT NULL,
                        `unexcusedPairs` INTEGER NOT NULL,
                        `excusedPairs` INTEGER NOT NULL,
                        `totalSemesterPairs` INTEGER NOT NULL,
                        PRIMARY KEY(`subjectName`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `emails` (
                        `id` INTEGER NOT NULL,
                        `sender` TEXT NOT NULL,
                        `senderName` TEXT NOT NULL,
                        `senderEmail` TEXT NOT NULL,
                        `subject` TEXT NOT NULL,
                        `snippet` TEXT NOT NULL,
                        `bodyText` TEXT NOT NULL,
                        `bodyHtml` TEXT,
                        `dateMillis` INTEGER NOT NULL,
                        `isUnread` INTEGER NOT NULL,
                        `hasAttachments` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `emails` ADD COLUMN `recipient` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `emails` ADD COLUMN `attachmentsJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `emails` ADD COLUMN `isUrgent` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `subject_absences` ADD COLUMN `controlType` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `lesson_notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `groupId` INTEGER NOT NULL,
                        `date` TEXT NOT NULL,
                        `discipline` TEXT NOT NULL,
                        `beginLesson` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_notes_groupId_date_discipline_beginLesson` ON `lesson_notes` (`groupId`, `date`, `discipline`, `beginLesson`)"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `lesson_notes` ADD COLUMN `reminderMinutesBefore` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `lesson_notes` ADD COLUMN `reminderTimeMillis` INTEGER DEFAULT NULL")
            }
        }

        internal fun isPlaintextDatabase(file: File): Boolean {
            if (!file.exists() || file.length() < 16) return false
            return try {
                FileInputStream(file).use { input ->
                    val header = ByteArray(16)
                    val read = input.read(header)
                    read == 16 && String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to check SQLite header", e)
                false
            }
        }

        private fun migratePlaintextToSqlcipher(plaintextDbFile: File, passphrase: ByteArray) {
            AppLogger.i(TAG, "Plaintext database detected. Migrating to SQLCipher AES-256 encryption...")
            try {
                System.loadLibrary("sqlcipher")
                val tempEncryptedFile = File(plaintextDbFile.parentFile, "${plaintextDbFile.name}_encrypted_temp")
                if (tempEncryptedFile.exists()) {
                    tempEncryptedFile.delete()
                }

                val db = net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(
                    plaintextDbFile,
                    null
                )

                val passphraseHex = SessionStorage.bytesToHex(passphrase)
                db.execSQL("ATTACH DATABASE '${tempEncryptedFile.absolutePath.replace("'", "''")}' AS encrypted KEY \"x'$passphraseHex'\";")
                db.execSQL("SELECT sqlcipher_export('encrypted');")
                db.execSQL("DETACH DATABASE encrypted;")
                db.close()

                val walFile = File(plaintextDbFile.path + "-wal")
                if (walFile.exists()) walFile.delete()
                val shmFile = File(plaintextDbFile.path + "-shm")
                if (shmFile.exists()) shmFile.delete()

                if (plaintextDbFile.delete()) {
                    if (tempEncryptedFile.renameTo(plaintextDbFile)) {
                        AppLogger.i(TAG, "Plaintext database successfully migrated to SQLCipher encrypted format")
                    } else {
                        AppLogger.e(TAG, "Failed to rename temp encrypted DB to original DB name")
                    }
                } else {
                    AppLogger.e(TAG, "Failed to delete old plaintext DB file")
                }
            } catch (e: Throwable) {
                AppLogger.e(TAG, "Failed to migrate plaintext database to SQLCipher; fallback to fresh recreation", e)
                try {
                    plaintextDbFile.delete()
                    File(plaintextDbFile.path + "-wal").delete()
                    File(plaintextDbFile.path + "-shm").delete()
                } catch (_: Exception) {}
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val dbFile = appContext.getDatabasePath(DATABASE_NAME)
                val sessionStorage = SessionStorage(appContext)
                val passphrase = sessionStorage.getOrCreateDatabasePassphrase()

                if (dbFile.exists() && isPlaintextDatabase(dbFile)) {
                    migratePlaintextToSqlcipher(dbFile, passphrase)
                }

                System.loadLibrary("sqlcipher")
                val factory = SupportOpenHelperFactory(passphrase)

                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
