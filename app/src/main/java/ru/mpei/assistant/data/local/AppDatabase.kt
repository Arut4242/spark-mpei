package ru.mpei.assistant.data.local

import android.content.Context
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mpei_assistant.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
