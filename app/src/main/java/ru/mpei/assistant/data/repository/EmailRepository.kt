package ru.mpei.assistant.data.repository

import android.content.Context
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.mpei.assistant.data.local.EmailDao
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.model.email.EmailAttachment
import ru.mpei.assistant.data.model.email.EmailMessage
import ru.mpei.assistant.data.network.EmailPageResult
import ru.mpei.assistant.data.network.OsepMailClient
import ru.mpei.assistant.worker.NotificationHelper
import java.io.File

@Stable
class EmailRepository(
    private val emailDao: EmailDao,
    private val sessionStorage: SessionStorage,
    private val mailClient: OsepMailClient = OsepMailClient()
) {
    private val syncMutex = Mutex()

    private val _hasMoreOlder = MutableStateFlow(true)
    val hasMoreOlderFlow: StateFlow<Boolean> = _hasMoreOlder.asStateFlow()

    var oldestLoadedMessageNumber: Int? = null
        private set

    val hasMoreOlderEmails: Boolean
        get() = _hasMoreOlder.value

    fun getEmailsFlow(): Flow<List<EmailMessage>> = emailDao.getAllEmails()

    fun getUnreadCountFlow(): Flow<Int> = emailDao.getUnreadCountFlow()

    fun isAuthorized(): Boolean = sessionStorage.isOsepAuthorized

    fun getLogin(): String = sessionStorage.osepLogin ?: ""

    suspend fun login(login: String, pass: String): Result<Unit> {
        ru.mpei.assistant.util.AppLogger.i("EMAIL_REPO", "User login attempt for '$login'")
        val testRes = mailClient.testConnection(login, pass)
        if (testRes.isFailure) {
            val err = testRes.exceptionOrNull() ?: Exception("Ошибка подключения к серверу почты ОСЭП")
            ru.mpei.assistant.util.AppLogger.e("EMAIL_REPO", "Login failed for '$login'", err)
            return Result.failure(err)
        }

        sessionStorage.osepLogin = login.trim()
        sessionStorage.osepPassword = pass
        ru.mpei.assistant.util.AppLogger.i("EMAIL_REPO", "Login success, credentials saved in SessionStorage")
        refreshEmails(notifyOnNew = false)
        return Result.success(Unit)
    }

    suspend fun logout() {
        sessionStorage.clearOsepSession()
        emailDao.clearAll()
        oldestLoadedMessageNumber = null
        _hasMoreOlder.value = true
    }

    suspend fun markAsRead(id: Long) {
        // 1. Немедленно отмечаем в локальной базе данных Room
        emailDao.markAsRead(id)

        // 2. Асинхронно синхронизируем флаг SEEN на сервере IMAP
        val login = sessionStorage.osepLogin
        val pass = sessionStorage.osepPassword
        if (!login.isNullOrBlank() && !pass.isNullOrBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    mailClient.markMessageAsRead(login, pass, id)
                } catch (e: Exception) {
                    ru.mpei.assistant.util.AppLogger.w("EMAIL_REPO", "Background markMessageAsRead error: ${e.message}")
                }
            }
        }
    }

    suspend fun refreshEmails(
        notifyOnNew: Boolean = false,
        context: Context? = null,
        pageSize: Int = 25
    ): Result<List<EmailMessage>> = syncMutex.withLock {
        val login = sessionStorage.osepLogin
        val pass = sessionStorage.osepPassword
        if (login.isNullOrBlank() || pass.isNullOrBlank()) {
            return Result.failure(Exception("Пользователь не авторизован в ОСЭП"))
        }

        val knownIds = emailDao.getAllEmailIds().toSet()
        val cachedEmails = emailDao.getAllEmailsList().associateBy { it.id }
        val fetchRes = mailClient.fetchEmailsPage(
            login = login,
            pass = pass,
            beforeMessageNumber = null,
            pageSize = pageSize,
            cachedEmails = cachedEmails
        )
        if (fetchRes.isFailure) {
            val err = fetchRes.exceptionOrNull() ?: Exception("Не удалось загрузить письма")
            ru.mpei.assistant.util.AppLogger.e("EMAIL_REPO", "refreshEmails failed", err)
            return Result.failure(err)
        }

        val pageResult = fetchRes.getOrThrow()
        oldestLoadedMessageNumber = pageResult.oldestMessageNumber
        _hasMoreOlder.value = pageResult.hasMore

        val emails = pageResult.emails
        ru.mpei.assistant.util.AppLogger.i("EMAIL_REPO", "refreshEmails: received ${emails.size} emails from IMAP")
        if (emails.isNotEmpty()) {
            emailDao.insertEmails(emails)
            ru.mpei.assistant.util.AppLogger.i("EMAIL_REPO", "refreshEmails: saved ${emails.size} emails to database")

            // Если это фоновая проверка и уже были известные письма, уведомляем о новых
            if (notifyOnNew && context != null && knownIds.isNotEmpty()) {
                val newEmails = emails.filter { it.id !in knownIds && it.isUnread }
                for (newEmail in newEmails) {
                    NotificationHelper.showEmailNotification(
                        context = context,
                        id = (newEmail.id and 0x7FFFFFFF).toInt(),
                        sender = newEmail.senderName,
                        subject = newEmail.subject,
                        snippet = newEmail.snippet
                    )
                }
            }
        }

        return Result.success(emails)
    }

    suspend fun loadOlderEmails(pageSize: Int = 25): Result<Int> = syncMutex.withLock {
        val login = sessionStorage.osepLogin
        val pass = sessionStorage.osepPassword
        if (login.isNullOrBlank() || pass.isNullOrBlank()) {
            return Result.failure(Exception("Пользователь не авторизован в ОСЭП"))
        }
        if (!_hasMoreOlder.value) {
            return Result.success(0)
        }
        val beforeMsg = oldestLoadedMessageNumber ?: return Result.success(0)
        if (beforeMsg <= 1) {
            _hasMoreOlder.value = false
            return Result.success(0)
        }

        val cachedEmails = emailDao.getAllEmailsList().associateBy { it.id }
        val fetchRes = mailClient.fetchEmailsPage(
            login = login,
            pass = pass,
            beforeMessageNumber = beforeMsg,
            pageSize = pageSize,
            cachedEmails = cachedEmails
        )
        if (fetchRes.isFailure) {
            val err = fetchRes.exceptionOrNull() ?: Exception("Не удалось загрузить старые письма")
            ru.mpei.assistant.util.AppLogger.e("EMAIL_REPO", "loadOlderEmails failed", err)
            return Result.failure(err)
        }

        val pageResult = fetchRes.getOrThrow()
        oldestLoadedMessageNumber = pageResult.oldestMessageNumber
        _hasMoreOlder.value = pageResult.hasMore

        val emails = pageResult.emails
        ru.mpei.assistant.util.AppLogger.i("EMAIL_REPO", "loadOlderEmails: received ${emails.size} older emails from IMAP")
        if (emails.isNotEmpty()) {
            emailDao.insertEmails(emails)
        }

        return Result.success(emails.size)
    }

    suspend fun repairBlankEmails() {
        val login = sessionStorage.osepLogin ?: return
        val pass = sessionStorage.osepPassword ?: return
        try {
            val blankEmails = emailDao.getBlankEmails()
            if (blankEmails.isEmpty()) return
            ru.mpei.assistant.util.AppLogger.i("EMAIL_REPO", "Found ${blankEmails.size} blank cached emails, repairing up to 10...")
            for (blank in blankEmails.take(10)) {
                try {
                    val res = mailClient.fetchMessageByUid(login, pass, blank.id)
                    if (res.isSuccess) {
                        emailDao.insertEmails(listOf(res.getOrThrow()))
                        ru.mpei.assistant.util.AppLogger.i("EMAIL_REPO", "Successfully repaired blank email UID=${blank.id}")
                    }
                } catch (e: Exception) {
                    ru.mpei.assistant.util.AppLogger.w("EMAIL_REPO", "Failed repairing email UID=${blank.id}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.w("EMAIL_REPO", "repairBlankEmails warning: ${e.message}")
        }
    }

    suspend fun fetchEmailDetails(uid: Long): Result<EmailMessage> {
        val login = sessionStorage.osepLogin
        val pass = sessionStorage.osepPassword
        if (login.isNullOrBlank() || pass.isNullOrBlank()) {
            return Result.failure(Exception("Пользователь не авторизован в ОСЭП"))
        }

        val res = mailClient.fetchMessageByUid(login, pass, uid)
        if (res.isSuccess) {
            val email = res.getOrThrow()
            emailDao.insertEmails(listOf(email))
            return Result.success(email)
        }
        return res
    }

    fun getCachedAttachmentFile(context: Context, emailId: Long, fileName: String): File {
        val attachmentsDir = File(context.cacheDir, "mail_attachments")
        val sanitized = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return File(attachmentsDir, "${emailId}_$sanitized")
    }

    fun isAttachmentCached(context: Context, emailId: Long, fileName: String): Boolean {
        val file = getCachedAttachmentFile(context, emailId, fileName)
        return file.exists() && file.length() > 0
    }

    suspend fun downloadAttachment(
        context: Context,
        emailId: Long,
        attachment: EmailAttachment
    ): Result<File> {
        val targetFile = getCachedAttachmentFile(context, emailId, attachment.fileName)
        if (targetFile.exists() && targetFile.length() > 0) {
            return Result.success(targetFile)
        }

        val login = sessionStorage.osepLogin
        val pass = sessionStorage.osepPassword
        if (login.isNullOrBlank() || pass.isNullOrBlank()) {
            return Result.failure(Exception("Пользователь не авторизован в ОСЭП"))
        }

        return mailClient.downloadAttachment(
            login = login,
            pass = pass,
            messageUid = emailId,
            fileName = attachment.fileName,
            partIndex = attachment.partIndex,
            targetFile = targetFile
        )
    }
}
