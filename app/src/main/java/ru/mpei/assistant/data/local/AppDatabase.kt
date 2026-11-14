package ru.mpei.assistant.data.local

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import ru.mpei.assistant.security.DatabaseKeyStorage
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.mpei.assistant.data.model.absence.SubjectAbsence
import ru.mpei.assistant.data.model.bars.BarsSubject
import ru.mpei.assistant.data.model.email.EmailMessage
import ru.mpei.assistant.data.model.schedule.Lesson

@Database(
    entities = [BarsSubject::class, Lesson::class, SubjectAbsence::class, EmailMessage::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun barsDao(): BarsDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun absenceDao(): AbsenceDao
    abstract fun emailDao(): EmailDao

    companion object {
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

        class DatabaseOpenException(cause: Throwable) :
            Exception("Не удалось открыть защищённую базу. Данные сохранены; повторите попытку.", cause)

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: openEncrypted(context.applicationContext).also { INSTANCE = it }
        }

        private fun openEncrypted(context: Context): AppDatabase {
            try {
                System.loadLibrary("sqlcipher")
                val file = context.getDatabasePath("mpei_assistant.db")
                check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                val lock = File(context.noBackupFilesDir, "database-encryption.lock")
                RandomAccessFile(lock, "rw").use { lockFile ->
                    lockFile.channel.lock().use {
                        val password = DatabaseKeyStorage.loadOrCreate(
                            context,
                            encryptedDatabaseExists = file.exists() &&
                                !EncryptedDatabaseMigration.isPlaintext(file)
                        )
                        try {
                            EncryptedDatabaseMigration.prepare(file, password)
                            val database = Room.databaseBuilder(context, AppDatabase::class.java, file.name)
                                .openHelperFactory(PreservingSqlCipherFactory(password.copyOf()))
                                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                                .build()
                            try {
                                // Force schema validation/migrations before publishing the singleton.
                                database.openHelper.writableDatabase
                                return database
                            } catch (e: Exception) {
                                database.close()
                                throw e
                            }
                        } finally {
                            password.fill(0)
                        }
                    }
                }
            } catch (e: Exception) {
                throw DatabaseOpenException(e)
            } catch (e: UnsatisfiedLinkError) {
                throw DatabaseOpenException(e)
            }
        }
    }
}
