package ru.mpei.assistant.data.repository

import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.mpei.assistant.data.local.AbsenceDao
import ru.mpei.assistant.data.local.BarsDao
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.model.absence.SubjectAbsence
import ru.mpei.assistant.data.model.bars.BarsSubject
import ru.mpei.assistant.data.network.BarsWebClient
import ru.mpei.assistant.security.KeystoreCrypto

class BarsRepositorySecurityTest {

    private class FakeBarsDao : BarsDao {
        var clearAllCalled = false
        var saved = mutableListOf<BarsSubject>()

        override fun getAllSubjects(): Flow<List<BarsSubject>> = flowOf(saved)
        override fun getSubjectsBySemester(semester: String): Flow<List<BarsSubject>> = flowOf(saved)
        override suspend fun getAllSubjectsList(): List<BarsSubject> = saved
        override suspend fun insertAll(subjects: List<BarsSubject>) { saved.addAll(subjects) }
        override suspend fun clearAll() { clearAllCalled = true; saved.clear() }
    }

    private class FakeAbsenceDao : AbsenceDao {
        var clearAllCalled = false

        override fun getAllAbsences(): Flow<List<SubjectAbsence>> = flowOf(emptyList())
        override suspend fun getAllAbsencesList(): List<SubjectAbsence> = emptyList()
        override suspend fun getAbsenceForSubject(subjectName: String): SubjectAbsence? = null
        override suspend fun insertOrUpdate(absence: SubjectAbsence) {}
        override suspend fun insertAllIgnore(absences: List<SubjectAbsence>) {}
        override suspend fun delete(subjectName: String) {}
        override suspend fun clearAll() { clearAllCalled = true }
    }

    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = (map[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class Editor : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { temp[key ?: ""] = values; return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun remove(key: String?): SharedPreferences.Editor { temp[key ?: ""] = null; return this }
            override fun clear(): SharedPreferences.Editor { clear = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) map.clear()
                for ((k, v) in temp) {
                    if (v == null) map.remove(k) else map[k] = v
                }
            }
        }
    }

    private lateinit var fakeBarsDao: FakeBarsDao
    private lateinit var fakeAbsenceDao: FakeAbsenceDao
    private lateinit var sessionStorage: SessionStorage
    private lateinit var barsWebClient: BarsWebClient

    @Before
    fun setup() {
        fakeBarsDao = FakeBarsDao()
        fakeAbsenceDao = FakeAbsenceDao()
        sessionStorage = SessionStorage(FakeSharedPreferences())
        barsWebClient = BarsWebClient(sessionStorage)
    }

    @Test
    fun testBarsLogoutClearsDaosAndSession() = runBlocking {
        sessionStorage.barsCookies = "test_cookies"
        sessionStorage.barsStudentId = "12345678-1234-1234-1234-123456789abc"

        val repository = BarsRepository(
            barsWebClient = barsWebClient,
            barsDao = fakeBarsDao,
            absenceDao = fakeAbsenceDao,
            sessionStorage = sessionStorage
        )

        repository.logout()

        // Проверяем полную очистку сессии и локального академического кэша (F-04)
        assertFalse(repository.isAuthorized())
        assertTrue("barsDao.clearAll() must be called on logout", fakeBarsDao.clearAllCalled)
        assertTrue("absenceDao.clearAll() must be called on logout", fakeAbsenceDao.clearAllCalled)
    }

    @Test
    fun testOnNewLoginProfileSwitchClearsPreviousData() = runBlocking {
        sessionStorage.barsStudentId = "11111111-1111-1111-1111-111111111111"

        val repository = BarsRepository(
            barsWebClient = barsWebClient,
            barsDao = fakeBarsDao,
            absenceDao = fakeAbsenceDao,
            sessionStorage = sessionStorage
        )

        // Вход нового студента с другим studentID
        val newUrl = "https://bars.mpei.ru/bars_web/ST_Study/Main/Main?studentID=22222222-2222-2222-2222-222222222222"
        repository.onNewLogin(cookies = "new_cookies=123", studentUrl = newUrl)

        // Проверяем предварительную очистку кэша перед загрузкой профиля другого пользователя (F-04)
        assertTrue("barsDao.clearAll() must be called when studentID changes", fakeBarsDao.clearAllCalled)
        assertTrue("absenceDao.clearAll() must be called when studentID changes", fakeAbsenceDao.clearAllCalled)
        assertEquals("22222222-2222-2222-2222-222222222222", sessionStorage.barsStudentId)
    }

    @Test
    fun testGradesDecryptionInFlow() = runBlocking {
        val originalMeasures = """[{"name":"КМ-1","grade":"5","dateReceived":"12.10.2024"}]"""
        val cipherMeasures = KeystoreCrypto.encrypt(originalMeasures)

        assertNotEquals(originalMeasures, cipherMeasures)
        assertTrue(cipherMeasures!!.startsWith("ENC:"))

        val encryptedSubject = BarsSubject(
            id = "sub_1",
            name = "Высшая математика",
            teacher = "Иванов И.И.",
            controlType = "Экзамен",
            credits = "4",
            deadline = "15.01.2025",
            controlMeasuresJson = cipherMeasures,
            currentControlScore = "5.0",
            semester = "Осенний семестр 2024/2025"
        )
        fakeBarsDao.saved.add(encryptedSubject)

        val repository = BarsRepository(
            barsWebClient = barsWebClient,
            barsDao = fakeBarsDao,
            absenceDao = fakeAbsenceDao,
            sessionStorage = sessionStorage
        )

        val retrieved = repository.getSubjectsFlow().first()
        assertEquals(1, retrieved.size)
        // В Flow возвращаются расшифрованные данные контрольных мероприятий (F-03)
        assertEquals(originalMeasures, retrieved[0].controlMeasuresJson)
    }
}
