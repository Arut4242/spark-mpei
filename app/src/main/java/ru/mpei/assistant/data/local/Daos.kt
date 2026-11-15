package ru.mpei.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.mpei.assistant.data.model.absence.SubjectAbsence
import ru.mpei.assistant.data.model.bars.BarsSubject
import ru.mpei.assistant.data.model.email.EmailMessage
import ru.mpei.assistant.data.model.schedule.Lesson

@Dao
interface BarsDao {
    @Query("SELECT * FROM bars_subjects")
    fun getAllSubjects(): Flow<List<BarsSubject>>

    @Query("SELECT * FROM bars_subjects")
    suspend fun getAllSubjectsList(): List<BarsSubject>

    @Query("SELECT * FROM bars_subjects WHERE semester = :semester")
    fun getSubjectsBySemester(semester: String): Flow<List<BarsSubject>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subjects: List<BarsSubject>)

    @Query("DELETE FROM bars_subjects")
    suspend fun clearAll()
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_lessons WHERE groupId = :groupId ORDER BY date ASC, beginLesson ASC")
    fun getLessonsByGroup(groupId: Long): Flow<List<Lesson>>

    @Query("SELECT COUNT(*) FROM schedule_lessons WHERE groupId = :groupId")
    suspend fun getLessonsCount(groupId: Long): Int

    @Query("SELECT * FROM schedule_lessons WHERE groupId = :groupId AND date = :date ORDER BY beginLesson ASC")
    suspend fun getLessonsForDay(groupId: Long, date: String): List<Lesson>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<Lesson>)

    @Query("DELETE FROM schedule_lessons WHERE groupId = :groupId")
    suspend fun deleteLessonsForGroup(groupId: Long)

    @Transaction
    suspend fun replaceLessonsForGroup(groupId: Long, lessons: List<Lesson>) {
        deleteLessonsForGroup(groupId)
        insertLessons(lessons)
    }
}

@Dao
interface AbsenceDao {
    @Query("SELECT * FROM subject_absences ORDER BY subjectName ASC")
    fun getAllAbsences(): Flow<List<SubjectAbsence>>

    @Query("SELECT * FROM subject_absences")
    suspend fun getAllAbsencesList(): List<SubjectAbsence>

    @Query("SELECT * FROM subject_absences WHERE subjectName = :subjectName LIMIT 1")
    suspend fun getAbsenceForSubject(subjectName: String): SubjectAbsence?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(absence: SubjectAbsence)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(absences: List<SubjectAbsence>)

    @Query("DELETE FROM subject_absences WHERE subjectName = :subjectName")
    suspend fun delete(subjectName: String)

    @Query("DELETE FROM subject_absences")
    suspend fun clearAll()
}

@Dao
interface EmailDao {
    @Query("SELECT * FROM emails ORDER BY dateMillis DESC")
    fun getAllEmails(): Flow<List<EmailMessage>>

    @Query("SELECT COUNT(*) FROM emails WHERE isUnread = 1")
    fun getUnreadCountFlow(): Flow<Int>

    @Query("SELECT id FROM emails")
    suspend fun getAllEmailIds(): List<Long>

    @Query("SELECT * FROM emails")
    suspend fun getAllEmailsList(): List<EmailMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailMessage>)

    @Query("SELECT * FROM emails WHERE (bodyText = '' OR bodyText = ' ' OR snippet = '') AND (bodyHtml IS NULL OR bodyHtml = '') ORDER BY dateMillis DESC")
    suspend fun getBlankEmails(): List<EmailMessage>

    @Query("UPDATE emails SET isUnread = 0 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("DELETE FROM emails")
    suspend fun clearAll()
}
