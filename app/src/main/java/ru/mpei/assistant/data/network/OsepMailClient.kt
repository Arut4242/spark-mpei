package ru.mpei.assistant.data.network

import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import ru.mpei.assistant.data.model.email.EmailAttachment
import ru.mpei.assistant.data.model.email.EmailAttachmentParser
import ru.mpei.assistant.data.model.email.EmailMessage
import java.io.File
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeUtility

data class EmailPageResult(
    val emails: List<EmailMessage>,
    val oldestMessageNumber: Int,
    val hasMore: Boolean,
    val totalMessages: Int
)

class OsepMailClient {

    private val mimeMultipartAny = "multipart" + "/" + "*"
    private val mimeTextAny = "text" + "/" + "*"

    private val imapHost = "mail.mpei.ru"
    private val imapPort = 993

    private fun createSession(): Session {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", imapHost)
            put("mail.imaps.port", "$imapPort")
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.timeout", "30000")
            put("mail.imaps.connectiontimeout", "30000")
            put("mail.imaps.ssl.trust", imapHost)
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.mime.decodefilename", "true")
            put("mail.mime.decodeparameters", "true")
            put("mail.mime.multipart.allowempty", "true")
        }
        return Session.getInstance(props)
    }

    private fun connectStore(session: Session, login: String, pass: String): IMAPStore {
        val store = session.getStore("imaps") as IMAPStore
        val cleanLogin = login.trim()
        ru.mpei.assistant.util.AppLogger.i("OSEP_MAIL", "Connecting to IMAP $imapHost:$imapPort...")

        try {
            store.connect(imapHost, imapPort, cleanLogin, pass)
            ru.mpei.assistant.util.AppLogger.i("OSEP_MAIL", "IMAP connection successful")
            return store
        } catch (e: AuthenticationFailedException) {
            ru.mpei.assistant.util.AppLogger.w("OSEP_MAIL", "Authentication failed, retrying with domain prefix...")
            // Если авторизация по чистому логину не прошла, пробуем с доменным префиксом mpei\логин
            if (!cleanLogin.contains("\\") && !cleanLogin.contains("@")) {
                try {
                    store.connect(imapHost, imapPort, "mpei\\$cleanLogin", pass)
                    ru.mpei.assistant.util.AppLogger.i("OSEP_MAIL", "IMAP connection successful with domain prefix")
                    return store
                } catch (e2: Exception) {
                    ru.mpei.assistant.util.AppLogger.e("OSEP_MAIL", "Secondary auth failed with domain prefix", e2)
                }
            }
            ru.mpei.assistant.util.AppLogger.e("OSEP_MAIL", "IMAP AuthenticationFailedException", e)
            throw e
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.e("OSEP_MAIL", "IMAP connection exception ($imapHost:$imapPort)", e)
            throw e
        }
    }

    suspend fun testConnection(login: String, pass: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            ru.mpei.assistant.util.AppLogger.i("OSEP_MAIL", "testConnection started")
            val session = createSession()
            val store = connectStore(session, login, pass)
            val inbox = store.getFolder("INBOX")
            val exists = inbox.exists()
            store.close()
            ru.mpei.assistant.util.AppLogger.i("OSEP_MAIL", "testConnection success: INBOX exists=$exists")
            Result.success(exists)
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.e("OSEP_MAIL", "testConnection failed", e)
            Result.failure(e)
        }
    }

    suspend fun fetchEmailsPage(
        login: String,
        pass: String,
        beforeMessageNumber: Int? = null,
        pageSize: Int = 25,
        cachedEmails: Map<Long, EmailMessage> = emptyMap()
    ): Result<EmailPageResult> = withContext(Dispatchers.IO) {
        var store: IMAPStore? = null
        var inbox: Folder? = null
        try {
            ru.mpei.assistant.util.AppLogger.i("OSEP_MAIL", "fetchEmailsPage started, beforeMsg=$beforeMessageNumber, pageSize=$pageSize")
            val session = createSession()
            store = connectStore(session, login, pass)
            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)

            val imapFolder = inbox as? IMAPFolder
            val totalMessages = inbox.messageCount
            ru.mpei.assistant.util.AppLogger.i("OSEP_MAIL", "INBOX opened, totalMessages=$totalMessages")
            if (totalMessages == 0) {
                return@withContext Result.success(
                    EmailPageResult(
                        emails = emptyList(),
                        oldestMessageNumber = 1,
                        hasMore = false,
                        totalMessages = 0
                    )
                )
            }

            val end = if (beforeMessageNumber == null) {
                totalMessages
            } else {
                (beforeMessageNumber - 1).coerceAtMost(totalMessages)
            }

            if (end < 1) {
                return@withContext Result.success(
                    EmailPageResult(
                        emails = emptyList(),
                        oldestMessageNumber = 1,
                        hasMore = false,
                        totalMessages = totalMessages
                    )
                )
            }

            val start = (end - pageSize + 1).coerceAtLeast(1)
            val messages = inbox.getMessages(start, end)

            // Batch-fetch headers/envelopes and flags in a single IMAP command
            try {
                val fp = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                    add(FetchProfile.Item.FLAGS)
                    add(FetchProfile.Item.CONTENT_INFO)
                    add(UIDFolder.FetchProfileItem.UID)
                }
                inbox.fetch(messages, fp)
                ru.mpei.assistant.util.AppLogger.i("OSEP_MAIL", "Batch fetched envelope for ${messages.size} messages ($start..$end)")
            } catch (e: Exception) {
                ru.mpei.assistant.util.AppLogger.w("OSEP_MAIL", "Batch fetch envelope non-critical warning: ${e.message}")
            }

            val emailList = mutableListOf<EmailMessage>()
            // Parse from newest to oldest within this batch
            for (i in messages.indices.reversed()) {
                val msg = messages[i]
                try {
                    val uid = imapFolder?.getUID(msg) ?: msg.messageNumber.toLong()
                    val cached = cachedEmails[uid]
                    if (cached != null) {
                        // Если письмо уже прочитано на устройстве — не сбрасываем в непрочитанные
                        val isUnread = if (!cached.isUnread) {
                            false
                        } else {
                            try { !msg.isSet(Flags.Flag.SEEN) } catch (_: Exception) { cached.isUnread }
                        }
                        emailList.add(cached.copy(isUnread = isUnread))
                    } else {
                        emailList.add(parseMessage(msg, uid, isLightweight = true))
                    }
                } catch (e: Exception) {
                    ru.mpei.assistant.util.AppLogger.e("OSEP_MAIL", "Error parsing message #$i", e)
                }
            }

            val hasMore = start > 1
            ru.mpei.assistant.util.AppLogger.i("OSEP_MAIL", "fetchEmailsPage complete: ${emailList.size} emails, oldestMsg=$start, hasMore=$hasMore")
            Result.success(
                EmailPageResult(
                    emails = emailList,
                    oldestMessageNumber = start,
                    hasMore = hasMore,
                    totalMessages = totalMessages
                )
            )
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.e("OSEP_MAIL", "fetchEmailsPage failed critically", e)
            Result.failure(e)
        } finally {
            try { inbox?.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    suspend fun fetchInbox(
        login: String,
        pass: String,
        limit: Int = 25,
        cachedEmails: Map<Long, EmailMessage> = emptyMap()
    ): Result<List<EmailMessage>> {
        return fetchEmailsPage(
            login = login,
            pass = pass,
            beforeMessageNumber = null,
            pageSize = limit,
            cachedEmails = cachedEmails
        ).map { it.emails }
    }

    suspend fun markMessageAsRead(login: String, pass: String, uid: Long): Result<Unit> = withContext(Dispatchers.IO) {
        var store: IMAPStore? = null
        var inbox: Folder? = null
        try {
            val session = createSession()
            store = connectStore(session, login, pass)
            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            val imapFolder = inbox as? IMAPFolder ?: throw IllegalStateException("Папка не поддерживает IMAP")

            val msg = imapFolder.getMessageByUID(uid)
            if (msg != null) {
                msg.setFlag(Flags.Flag.SEEN, true)
                ru.mpei.assistant.util.AppLogger.i("OSEP_MAIL", "Marked message UID=$uid as SEEN on server")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.w("OSEP_MAIL", "Failed to mark message UID=$uid as SEEN: ${e.message}")
            Result.failure(e)
        } finally {
            try { inbox?.close(true) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    suspend fun fetchMessageByUid(login: String, pass: String, uid: Long): Result<EmailMessage> = withContext(Dispatchers.IO) {
        var store: IMAPStore? = null
        var inbox: Folder? = null
        try {
            val session = createSession()
            store = connectStore(session, login, pass)
            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val imapFolder = inbox as? IMAPFolder ?: throw IllegalStateException("Папка не поддерживает IMAP")

            val msg = imapFolder.getMessageByUID(uid)
                ?: throw IllegalArgumentException("Письмо UID=$uid не найдено на сервере")

            val email = parseMessage(msg, uid, isLightweight = false)
            Result.success(email)
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.e("OSEP_MAIL", "fetchMessageByUid failed for UID=$uid", e)
            Result.failure(e)
        } finally {
            try { inbox?.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    private fun parseMessage(msg: Message, uid: Long, isLightweight: Boolean = false): EmailMessage {
        val rawSubject = try { msg.subject } catch (_: Exception) { null } ?: "(Без темы)"
        val cleanSubject = try {
            MimeUtility.decodeText(rawSubject)
        } catch (_: Exception) {
            rawSubject
        }

        val fromArray = try { msg.from } catch (_: Exception) { null }
        val firstAddr = fromArray?.firstOrNull() as? InternetAddress
        val rawSenderName = try { firstAddr?.personal } catch (_: Exception) { null }
        val cleanSenderName = if (!rawSenderName.isNullOrBlank()) {
            try { MimeUtility.decodeText(rawSenderName) } catch (_: Exception) { rawSenderName }
        } else {
            try { firstAddr?.address } catch (_: Exception) { null } ?: "Неизвестный"
        }
        val senderEmail = try { firstAddr?.address } catch (_: Exception) { null } ?: ""
        val fullSender = if (cleanSenderName != senderEmail) "$cleanSenderName <$senderEmail>" else senderEmail

        // Recipient
        val toRecipients = try {
            msg.getRecipients(Message.RecipientType.TO)
        } catch (_: Exception) { null }
        val cleanRecipient = if (!toRecipients.isNullOrEmpty()) {
            toRecipients.joinToString("; ") { addr ->
                if (addr is InternetAddress) {
                    val name = try { addr.personal } catch (_: Exception) { null }
                    val decodedName = if (!name.isNullOrBlank()) {
                        try { MimeUtility.decodeText(name) } catch (_: Exception) { name }
                    } else ""
                    if (decodedName.isNotBlank()) decodedName else (try { addr.address } catch (_: Exception) { "" } ?: "")
                } else {
                    addr.toString()
                }
            }
        } else ""

        // Urgency
        val importance = try { msg.getHeader("Importance")?.firstOrNull() } catch (_: Exception) { null }
        val priority = try { msg.getHeader("X-Priority")?.firstOrNull() } catch (_: Exception) { null }
        val isUrgent = importance?.contains("high", ignoreCase = true) == true ||
                       priority?.contains("1") == true || priority?.contains("2") == true

        val dateMillis = try {
            (msg.receivedDate ?: msg.sentDate)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
        val isUnread = try { !msg.isSet(Flags.Flag.SEEN) } catch (_: Exception) { false }

        if (isLightweight) {
            val snippet = extractSnippet(msg)
            val rawContentType = try { msg.contentType } catch (_: Exception) { "" } ?: ""
            val hasAttachments = rawContentType.contains("multipart/mixed", ignoreCase = true) ||
                    rawContentType.contains("application/", ignoreCase = true)
            return EmailMessage(
                id = uid,
                sender = fullSender,
                senderName = cleanSenderName,
                senderEmail = senderEmail,
                recipient = cleanRecipient,
                subject = cleanSubject,
                snippet = snippet,
                bodyText = "",
                bodyHtml = null,
                dateMillis = dateMillis,
                isUnread = isUnread,
                hasAttachments = hasAttachments,
                attachmentsJson = "[]",
                isUrgent = isUrgent
            )
        }

        val (rawBodyText, bodyHtml) = try {
            extractBody(msg)
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.w("OSEP_MAIL", "Failed to extract body for msg UID=$uid: ${e.message}")
            Pair("(Не удалось прочитать содержимое)", null)
        }

        // Если обычный текст пустой, но есть HTML — генерируем понятный текст с таблицами и переносами
        val bodyText = if (rawBodyText.isBlank() && !bodyHtml.isNullOrBlank()) {
            cleanHtmlToPlainText(bodyHtml)
        } else {
            rawBodyText
        }

        val effectiveText = when {
            bodyText.isNotBlank() && (bodyHtml.isNullOrBlank() || bodyText.length >= 30) -> bodyText
            !bodyHtml.isNullOrBlank() -> {
                val fromHtml = cleanHtmlToPlainText(bodyHtml)
                if (fromHtml.isNotBlank()) fromHtml else bodyText
            }
            else -> bodyText
        }
        val snippet = effectiveText
            .trim()
            .replace(Regex("""^[\|\s]+"""), "")
            .replace("\\s+".toRegex(), " ")
            .take(140)

        // Attachments extraction
        val attachments = mutableListOf<EmailAttachment>()
        val indexTracker = AtomicInteger(0)
        try {
            extractAttachments(msg, attachments, indexTracker)
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.w("OSEP_MAIL", "Failed to extract attachments for msg UID=$uid: ${e.message}")
        }
        val hasAttachments = attachments.isNotEmpty() || (try { hasAttachments(msg) } catch (_: Exception) { false })
        val attachmentsJson = EmailAttachmentParser.toJson(attachments)

        return EmailMessage(
            id = uid,
            sender = fullSender,
            senderName = cleanSenderName,
            senderEmail = senderEmail,
            recipient = cleanRecipient,
            subject = cleanSubject,
            snippet = snippet,
            bodyText = bodyText,
            bodyHtml = bodyHtml,
            dateMillis = dateMillis,
            isUnread = isUnread,
            hasAttachments = hasAttachments,
            attachmentsJson = attachmentsJson,
            isUrgent = isUrgent
        )
    }

    suspend fun downloadAttachment(
        login: String,
        pass: String,
        messageUid: Long,
        fileName: String,
        partIndex: Int,
        targetFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        var store: IMAPStore? = null
        var inbox: Folder? = null
        try {
            val session = createSession()
            store = connectStore(session, login, pass)
            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val imapFolder = inbox as? IMAPFolder ?: throw IllegalStateException("Папка не поддерживает IMAP")

            val msg = imapFolder.getMessageByUID(messageUid)
                ?: throw IllegalArgumentException("Письмо UID=$messageUid не найдено на сервере")

            val part = findAttachmentPart(msg, fileName, partIndex)
                ?: throw IllegalArgumentException("Вложение '$fileName' не найдено")

            targetFile.parentFile?.mkdirs()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            part.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { inbox?.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
    }

    private fun findAttachmentPart(
        part: Part,
        targetFileName: String,
        targetIndex: Int,
        currentIndex: AtomicInteger = AtomicInteger(0)
    ): Part? {
        if (part.isMimeType(mimeMultipartAny)) {
            val mp = part.content as? Multipart ?: return null
            for (i in 0 until mp.count) {
                val bp = mp.getBodyPart(i)
                val found = findAttachmentPart(bp, targetFileName, targetIndex, currentIndex)
                if (found != null) return found
            }
        } else {
            val disposition = try { part.disposition } catch (_: Exception) { null }
            val isAttachment = Part.ATTACHMENT.equals(disposition, ignoreCase = true)
            val cleanName = resolveFileName(part)

            if (isAttachment || (!cleanName.isNullOrBlank() && !part.isMimeType("text/plain") && !part.isMimeType("text/html"))) {
                val thisIndex = currentIndex.getAndIncrement()
                if (cleanName.equals(targetFileName, ignoreCase = true) || thisIndex == targetIndex) {
                    return part
                }
            }
        }
        return null
    }

    private fun extractAttachments(
        part: Part,
        result: MutableList<EmailAttachment>,
        indexTracker: AtomicInteger
    ) {
        try {
            if (part.isMimeType(mimeMultipartAny)) {
                val mp = part.content as? Multipart ?: return
                for (i in 0 until mp.count) {
                    val bp = mp.getBodyPart(i)
                    extractAttachments(bp, result, indexTracker)
                }
            } else {
                val disposition = try { part.disposition } catch (_: Exception) { null }
                val isAttachment = Part.ATTACHMENT.equals(disposition, ignoreCase = true)
                val isInline = Part.INLINE.equals(disposition, ignoreCase = true)
                val cleanName = resolveFileName(part)

                val isNonBody = !part.isMimeType("text/plain") && !part.isMimeType("text/html")
                if (isAttachment || (isInline && !cleanName.isNullOrBlank()) || (!cleanName.isNullOrBlank() && isNonBody) || (isAttachment && isNonBody)) {
                    val finalName = cleanName ?: "Вложение_${indexTracker.get() + 1}${guessExtension(part)}"
                    val rawContentType = part.contentType ?: "application/octet-stream"
                    val cleanMime = rawContentType.substringBefore(";").trim().lowercase()
                    val size = part.size.toLong().coerceAtLeast(0L)

                    result.add(
                        EmailAttachment(
                            fileName = finalName,
                            sizeBytes = size,
                            mimeType = cleanMime,
                            partIndex = indexTracker.getAndIncrement()
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }

    private fun resolveFileName(part: Part): String? {
        var name: String? = null
        try {
            name = part.fileName
        } catch (_: Exception) {}

        if (name.isNullOrBlank()) {
            try {
                val dispositionHeaders = part.getHeader("Content-Disposition")
                if (!dispositionHeaders.isNullOrEmpty()) {
                    for (h in dispositionHeaders) {
                        name = parseFilenameFromHeader(h)
                        if (!name.isNullOrBlank()) break
                    }
                }
            } catch (_: Exception) {}
        }

        if (name.isNullOrBlank()) {
            try {
                val typeHeaders = part.getHeader("Content-Type")
                if (!typeHeaders.isNullOrEmpty()) {
                    for (h in typeHeaders) {
                        name = parseFilenameFromHeader(h)
                        if (!name.isNullOrBlank()) break
                    }
                }
            } catch (_: Exception) {}
        }

        if (name.isNullOrBlank()) return null

        name = try {
            MimeUtility.decodeText(name)
        } catch (_: Exception) {
            name
        }

        if (name.contains("%")) {
            name = try {
                URLDecoder.decode(name, "UTF-8")
            } catch (_: Exception) {
                name
            }
        }

        return name.trim().replace("\"", "").replace("'", "").replace("\r", "").replace("\n", "")
    }

    private fun parseFilenameFromHeader(header: String): String? {
        val patterns = listOf(
            """filename\*=(?:[uU][tT][fF]-8''|[^']*'')?([^;\r\n]+)""".toRegex(),
            """filename\s*=\s*["']?([^;"'\r\n]+)["']?""".toRegex(RegexOption.IGNORE_CASE),
            """name\*=(?:[uU][tT][fF]-8''|[^']*'')?([^;\r\n]+)""".toRegex(),
            """name\s*=\s*["']?([^;"'\r\n]+)["']?""".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(header)
            if (match != null && match.groupValues.size > 1) {
                val raw = match.groupValues[1].trim()
                if (raw.isNotBlank()) return raw
            }
        }
        return null
    }

    private fun guessExtension(part: Part): String {
        val type = try { part.contentType?.lowercase() ?: "" } catch (_: Exception) { "" }
        return when {
            type.contains("pdf") -> ".pdf"
            type.contains("word") || type.contains("docx") -> ".docx"
            type.contains("msword") -> ".doc"
            type.contains("excel") || type.contains("spreadsheet") || type.contains("xlsx") -> ".xlsx"
            type.contains("powerpoint") || type.contains("presentation") -> ".pptx"
            type.contains("zip") -> ".zip"
            type.contains("jpeg") || type.contains("jpg") -> ".jpg"
            type.contains("png") -> ".png"
            type.contains("text/plain") -> ".txt"
            else -> ""
        }
    }

    private fun hasAttachments(part: Part): Boolean {
        return try {
            if (part.isMimeType(mimeMultipartAny)) {
                val mp = part.content as? Multipart ?: return false
                for (i in 0 until mp.count) {
                    val bp = mp.getBodyPart(i)
                    if (hasAttachments(bp)) return true
                }
                false
            } else {
                val name = resolveFileName(part)
                val isAtt = Part.ATTACHMENT.equals(part.disposition, ignoreCase = true)
                isAtt || (!name.isNullOrBlank() && !part.isMimeType("text/plain") && !part.isMimeType("text/html"))
            }
        } catch (_: Exception) {
            false
        }
    }

    internal fun extractBody(part: Part): Pair<String, String?> {
        // 1. Single-part text/plain
        if (part.isMimeType("text/plain")) {
            val content = readPartString(part)
            return Pair(content, null)
        }

        // 2. Single-part text/html
        if (part.isMimeType("text/html")) {
            val html = readPartString(part)
            val plain = cleanHtmlToPlainText(html)
            return Pair(plain, html)
        }

        // 3. Forwarded / encapsulated email (message/rfc822)
        if (part.isMimeType("message/rfc822")) {
            val nested = try { part.content as? Part } catch (_: Exception) { null }
            if (nested != null) {
                return extractBody(nested)
            }
        }

        // 4. Multipart structures (alternative, related, mixed, etc.)
        if (part.isMimeType(mimeMultipartAny)) {
            val mp = try {
                part.content as? Multipart
            } catch (e: Exception) {
                ru.mpei.assistant.util.AppLogger.w("OSEP_MAIL", "Failed to parse multipart content: ${e.message}")
                null
            }

            if (mp != null) {
                var plainText = ""
                var htmlText: String? = null

                for (i in 0 until mp.count) {
                    val bp = try { mp.getBodyPart(i) } catch (_: Exception) { null } ?: continue
                    val disposition = try { bp.disposition } catch (_: Exception) { null }
                    val isAttachment = Part.ATTACHMENT.equals(disposition, ignoreCase = true)

                    // Skip parts explicitly marked as attachments unless they are text parts
                    if (isAttachment && !bp.isMimeType("text/plain") && !bp.isMimeType("text/html")) {
                        continue
                    }

                    if (bp.isMimeType("text/plain")) {
                        val text = readPartString(bp)
                        if (text.isNotBlank() && plainText.isBlank()) {
                            plainText = text
                        }
                    } else if (bp.isMimeType("text/html")) {
                        val html = readPartString(bp)
                        if (html.isNotBlank() && htmlText.isNullOrBlank()) {
                            htmlText = html
                        }
                    } else if (bp.isMimeType(mimeMultipartAny) || bp.isMimeType("message/rfc822")) {
                        val sub = extractBody(bp)
                        if (plainText.isBlank() && sub.first.isNotBlank()) {
                            plainText = sub.first
                        }
                        if (htmlText.isNullOrBlank() && !sub.second.isNullOrBlank()) {
                            htmlText = sub.second
                        }
                    }
                }

                // If plain text is blank/whitespace but html is present, extract clean text from HTML
                if (plainText.isBlank() && !htmlText.isNullOrBlank()) {
                    plainText = cleanHtmlToPlainText(htmlText)
                }

                return Pair(plainText, htmlText)
            }
        }

        // 5. Fallback for text MIME types
        if (part.isMimeType(mimeTextAny)) {
            val text = readPartString(part)
            return Pair(text, null)
        }

        return Pair("", null)
    }

    internal fun readPartString(part: Part): String {
        // 1. Try part.content first (standard JavaMail decoding)
        try {
            val content = part.content
            if (content is String) {
                return content
            }
            if (content is java.io.InputStream) {
                val charset = extractCharset(part)
                val text = content.bufferedReader(charset).use { it.readText() }
                if (text.isNotBlank()) return text
            }
            if (content is ByteArray) {
                val charset = extractCharset(part)
                val text = String(content, charset)
                if (text.isNotBlank()) return text
            }
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.w("OSEP_MAIL", "part.content reading failed: ${e.message}")
        }

        // 2. Fallback to raw inputStream with charset detection (handles UnsupportedEncodingException)
        try {
            val charset = extractCharset(part)
            val text = part.inputStream.bufferedReader(charset).use { it.readText() }
            if (text.isNotBlank()) return text
        } catch (e: Exception) {
            ru.mpei.assistant.util.AppLogger.w("OSEP_MAIL", "part.inputStream reading failed: ${e.message}")
        }

        // 3. Fallback with Windows-1251 (common Russian Exchange charset)
        try {
            val text = part.inputStream.bufferedReader(Charset.forName("windows-1251")).use { it.readText() }
            if (text.isNotBlank()) return text
        } catch (_: Exception) {}

        return ""
    }

    private fun extractCharset(part: Part): Charset {
        try {
            val rawContentType = part.contentType
            if (!rawContentType.isNullOrBlank()) {
                val ct = try { javax.mail.internet.ContentType(rawContentType) } catch (_: Exception) { null }
                val charsetName = ct?.getParameter("charset")
                    ?: Regex("""charset\s*=\s*["']?([^;"'\s]+)""", RegexOption.IGNORE_CASE)
                        .find(rawContentType)?.groupValues?.getOrNull(1)
                if (!charsetName.isNullOrBlank()) {
                    val clean = charsetName.trim().removeSurrounding("\"").removeSurrounding("'").trimEnd(';', ' ')
                    return try {
                        Charset.forName(clean)
                    } catch (_: Exception) {
                        try {
                            Charset.forName(clean.replace("-", ""))
                        } catch (_: Exception) {
                            Charsets.UTF_8
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return Charsets.UTF_8
    }

    private fun extractSnippet(msg: Message): String {
        return try {
            if (msg.isMimeType("text/plain")) {
                val s = readPartString(msg)
                s.trim().replace("\\s+".toRegex(), " ").take(140)
            } else if (msg.isMimeType("text/html")) {
                val s = readPartString(msg)
                cleanHtmlToPlainText(s).take(140)
            } else if (msg.isMimeType(mimeMultipartAny)) {
                val mp = try { msg.content as? Multipart } catch (_: Exception) { null } ?: return ""
                if (mp.count > 0) {
                    val firstPart = try { mp.getBodyPart(0) } catch (_: Exception) { null }
                    if (firstPart != null) {
                        if (firstPart.isMimeType("text/plain")) {
                            readPartString(firstPart).trim().replace("\\s+".toRegex(), " ").take(140)
                        } else if (firstPart.isMimeType("text/html")) {
                            cleanHtmlToPlainText(readPartString(firstPart)).take(140)
                        } else if (firstPart.isMimeType(mimeMultipartAny)) {
                            val subMp = try { firstPart.content as? Multipart } catch (_: Exception) { null }
                            if (subMp != null && subMp.count > 0) {
                                val subPart = try { subMp.getBodyPart(0) } catch (_: Exception) { null }
                                if (subPart != null) {
                                    if (subPart.isMimeType("text/plain")) {
                                        readPartString(subPart).trim().replace("\\s+".toRegex(), " ").take(140)
                                    } else if (subPart.isMimeType("text/html")) {
                                        cleanHtmlToPlainText(readPartString(subPart)).take(140)
                                    } else ""
                                } else ""
                            } else ""
                        } else ""
                    } else ""
                } else ""
            } else ""
        } catch (_: Exception) {
            ""
        }
    }
}

/**
 * Преобразует HTML-письмо в аккуратный читаемый плоский текст с сохранением строк таблиц,
 * разделителей колонок и списков.
 */
fun cleanHtmlToPlainText(html: String): String {
    if (html.isBlank()) return ""
    return try {
        val doc = Jsoup.parse(html)
        doc.select("br").after("\\n")
        doc.select("p, div, tr, h1, h2, h3, h4, h5, h6").before("\\n")
        doc.select("td, th").forEach { cell ->
            if (cell.text().isNotBlank()) {
                cell.after(" | ")
            }
        }
        doc.select("li").before("\\n• ")
        val raw = doc.text().replace("\\n", "\n")
        raw.lines()
            .map { it.trim().trim('|', ' ') }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    } catch (_: Exception) {
        try { Jsoup.parse(html).text().trim() } catch (_: Exception) { "" }
    }
}
