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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.mpei.assistant.data.local.EmailDao
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.model.email.EmailMessage
import ru.mpei.assistant.data.network.OsepMailClient
import ru.mpei.assistant.security.KeystoreCrypto
import java.io.File

class EmailRepositorySecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeEmailDao : EmailDao {
        var clearAllCalled = false
        val emails = mutableListOf<EmailMessage>()

        override fun getAllEmails(): Flow<List<EmailMessage>> = flowOf(emails)
        override fun getUnreadCountFlow(): Flow<Int> = flowOf(emails.count { it.isUnread })
        override suspend fun getAllEmailIds(): List<Long> = emails.map { it.id }
        override suspend fun getAllEmailsList(): List<EmailMessage> = emails
        override suspend fun insertEmails(emails: List<EmailMessage>) {
            this.emails.removeAll { existing -> emails.any { it.id == existing.id } }
            this.emails.addAll(emails)
        }
        override suspend fun getBlankEmails(): List<EmailMessage> = emptyList()
        override suspend fun markAsRead(id: Long) {}
        override suspend fun clearAll() { clearAllCalled = true; emails.clear() }
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

    private lateinit var fakeEmailDao: FakeEmailDao
    private lateinit var sessionStorage: SessionStorage
    private lateinit var mailClient: OsepMailClient
    private lateinit var testCacheDir: File

    @Before
    fun setup() {
        fakeEmailDao = FakeEmailDao()
        sessionStorage = SessionStorage(FakeSharedPreferences())
        mailClient = OsepMailClient()
        testCacheDir = tempFolder.newFolder("cache")
    }

    @Test
    fun testEmailLogoutDeletesAttachmentsAndClearsDatabase() = runBlocking {
        // Создаем фиктивную директорию с вложениями
        val attachmentsDir = File(testCacheDir, "mail_attachments").apply { mkdirs() }
        val testFile = File(attachmentsDir, "123_lab_report.pdf").apply { writeText("Sensitive student data") }
        assertTrue(testFile.exists())

        sessionStorage.osepLogin = "student@mpei.ru"
        sessionStorage.osepPassword = "password123"

        val repository = EmailRepository(
            emailDao = fakeEmailDao,
            sessionStorage = sessionStorage,
            mailClient = mailClient
        )

        // Создаем mock context с testCacheDir
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getCacheDir(): File = testCacheDir
        }

        repository.logout(mockContext)

        // Проверяем вызовы очистки
        assertFalse(sessionStorage.isOsepAuthorized)
        assertTrue("emailDao.clearAll() must be called on logout", fakeEmailDao.clearAllCalled)

        // Проверяем полное рекурсивное удаление каталога вложений (F-05)
        assertFalse("mail_attachments dir must be deleted on logout", attachmentsDir.exists())
        assertFalse("Downloaded attachment must be deleted on logout", testFile.exists())
    }

    @Test
    fun testEmailBodyDecryptionInFlow() = runBlocking {
        val originalHtml = "<p>Конфиденциальное сообщение деканата</p>"
        val originalText = "Конфиденциальное сообщение деканата"
        val cipherHtml = KeystoreCrypto.encrypt(originalHtml)
        val cipherText = KeystoreCrypto.encrypt(originalText)

        assertNotEquals(originalHtml, cipherHtml)
        assertTrue(cipherHtml!!.startsWith("ENC:"))

        val encryptedDbEmail = EmailMessage(
            id = 1001L,
            sender = "dekanat@mpei.ru",
            senderName = "Деканат",
            senderEmail = "dekanat@mpei.ru",
            subject = "Приказ об отчислении/поощрении",
            snippet = "Текст приказа",
            bodyText = cipherText!!,
            bodyHtml = cipherHtml,
            dateMillis = 1700000000000L,
            isUnread = true
        )
        fakeEmailDao.emails.add(encryptedDbEmail)

        val repository = EmailRepository(
            emailDao = fakeEmailDao,
            sessionStorage = sessionStorage,
            mailClient = mailClient
        )

        val retrievedEmails = repository.getEmailsFlow().first()
        assertEquals(1, retrievedEmails.size)
        // В памяти возвращается полностью расшифрованное письмо (F-03)
        assertEquals(originalHtml, retrievedEmails[0].bodyHtml)
        assertEquals(originalText, retrievedEmails[0].bodyText)
    }
}
