package ru.mpei.assistant.data.repository

import android.content.Context
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.mpei.assistant.data.local.EmailDao
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.model.email.EmailAttachment
import ru.mpei.assistant.data.model.email.EmailMessage
import ru.mpei.assistant.data.network.EmailPageResult
import ru.mpei.assistant.data.network.OsepMailClient
import ru.mpei.assistant.security.KeystoreCrypto
import ru.mpei.assistant.worker.NotificationHelper
import java.io.File

@Stable
class EmailRepository(
    private val emailDao: EmailDao,
    private val sessionStorage: SessionStorage,
    private val mailClient: OsepMailClient = OsepMailClient(),
    private val appContext: Context? = null
) {
    private val syncMutex = Mutex()

    private val _hasMoreOlder = MutableStateFlow(true)
    val hasMoreOlderFlow: StateFlow<Boolean> = _hasMoreOlder.asStateFlow()

    var oldestLoadedMessageNumber: Int? = null
        private set

    val hasMoreOlderEmails: Boolean
        get() = _hasMoreOlder.value

    /**
     * Шифрует чувствительные поля письма перед сохранением в локальную БД SQLite
     */
    private fun encryptEmail(email: EmailMessage): EmailMessage {
        val encBodyText = if (email.bodyText.isNotEmpty()) {
            KeystoreCrypto.encrypt(email.bodyText) ?: email.bodyText
        } else email.bodyText
        val encBodyHtml = if (!email.bodyHtml.isNullOrEmpty()) {
            KeystoreCrypto.encrypt(email.bodyHtml)
        } else email.bodyHtml
        return email.copy(
            bodyText = encBodyText,
            bodyHtml = encBodyHtml
        )
    }

    /**
     * Расшифровывает тело письма при извлечении из базы данных
     */
    private fun decryptEmail(email: EmailMessage): EmailMessage {
        val decBodyText = KeystoreCrypto.decrypt(email.bodyText) ?: email.bodyText
        val decBodyHtml = KeystoreCrypto.decrypt(email.bodyHtml)
        return email.copy(
            bodyText = decBodyText,
            bodyHtml = decBodyHtml
        )
    }

    fun getEmailsFlow(): Flow<List<EmailMessage>> = emailDao.getAllEmails().map { list ->
        list.map { decryptEmail(it) }
    }

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

    /**
     * Выход из почтового аккаунта:
     * 1. Удаление учётных данных из защищённого хранилища.
     * 2. Полное удаление всех кэшированных писем из Room.
     * 3. Рекурсивное удаление всех скачанных вложений из кэша (F-05).
     */
    suspend fun logout(context: Context? = null) {
        ru.mpei.assistant.util.AppLogger.i("EMAIL_REPO", "logout initiated")
        sessionStorage.clearOsepSession()
        try {
            emailDao.clearAll()
            ru.mpei.assistant.util.AppLogger.i("EMAIL_REPO", "emailDao cleared")
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.e("EMAIL_REPO", "Failed to clear emailDao", e)
        }
        oldestLoadedMessageNumber = null
        _hasMoreOlder.value = true

        val ctx = context ?: appContext
        if (ctx != null) {
            try {
                val attachmentsDir = File(ctx.cacheDir, "mail_attachments")
                if (attachmentsDir.exists()) {
                    val deleted = attachmentsDir.deleteRecursively()
                    ru.mpei.assistant.util.AppLogger.i("EMAIL_REPO", "mail_attachments directory deleted on logout: $deleted")
                }
            } catch (e: Exception) {
                ru.mpei.assistant.util.AppLogger.e("EMAIL_REPO", "Failed to delete mail_attachments on logout", e)
            }
        }
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
        val cachedEmails = emailDao.getAllEmailsList().map { decryptEmail(it) }.associateBy { it.id }
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
            emailDao.insertEmails(emails.map { encryptEmail(it) })
            ru.mpei.assistant.util.AppLogger.i("EMAIL_REPO", "refreshEmails: saved ${emails.size} encrypted emails to database")

            // Минимизация кэша: удаляем тела устаревших писем (F-03)
            pruneOldEmailBodies(keepCount = 50)

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

        val cachedEmails = emailDao.getAllEmailsList().map { decryptEmail(it) }.associateBy { it.id }
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
            emailDao.insertEmails(emails.map { encryptEmail(it) })
            pruneOldEmailBodies(keepCount = 50)
        }

        return Result.success(emails.size)
    }

    /**
     * Минимизация кэша писем (F-03): тела старых писем обнуляются в БД,
     * оставляя метаданные и тему в списке, при этом содержимое можно догрузить по нажатию.
     */
    private suspend fun pruneOldEmailBodies(keepCount: Int = 50) {
        try {
            val all = emailDao.getAllEmailsList()
            if (all.size > keepCount) {
                val toPrune = all.sortedByDescending { it.dateMillis }.drop(keepCount)
                val pruned = toPrune.filter { it.bodyText.isNotEmpty() || !it.bodyHtml.isNullOrEmpty() }
                    .map {
                        it.copy(
                            bodyText = "",
                            bodyHtml = null
                        )
                    }
                if (pruned.isNotEmpty()) {
                    emailDao.insertEmails(pruned)
                    ru.mpei.assistant.util.AppLogger.i("EMAIL_REPO", "Pruned bodies for ${pruned.size} old emails to minimize sensitive storage")
                }
            }
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.w("EMAIL_REPO", "Failed pruning old bodies: ${e.message}")
        }
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
                        emailDao.insertEmails(listOf(encryptEmail(res.getOrThrow())))
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
            emailDao.insertEmails(listOf(encryptEmail(email)))
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
