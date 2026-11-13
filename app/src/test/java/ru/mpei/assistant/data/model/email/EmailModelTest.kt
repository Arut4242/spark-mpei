package ru.mpei.assistant.data.model.email

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mpei.assistant.ui.email.formatEmailDate
import ru.mpei.assistant.ui.email.getSenderInitials
import ru.mpei.assistant.ui.email.groupEmailsByDate
import java.util.Calendar

class EmailModelTest {

    @Test
    fun `test email message data class properties and defaults`() {
        val attachments = listOf(
            EmailAttachment(
                fileName = "Раздаточный_материал.pdf",
                sizeBytes = 2500000L,
                mimeType = "application/pdf",
                partIndex = 0
            ),
            EmailAttachment(
                fileName = "Задание3.docx",
                sizeBytes = 45000L,
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                partIndex = 1
            )
        )
        val json = EmailAttachmentParser.toJson(attachments)

        val email = EmailMessage(
            id = 1001L,
            sender = "Иванов Иван Иванович <ivanovii@mpei.ru>",
            senderName = "Иванов Иван Иванович",
            senderEmail = "ivanovii@mpei.ru",
            recipient = "У-01-24. Все абоненты;",
            subject = "МПС Раздаточный материал",
            snippet = "Здравствуйте, У-01-24. Высылаю раздаточный материал...",
            bodyText = "Здравствуйте, У-01-24. Высылаю раздаточный материал по пройденным темам.",
            bodyHtml = "<p>Здравствуйте, У-01-24.</p><p>Высылаю раздаточный материал.</p>",
            dateMillis = 1700000000000L,
            isUnread = true,
            hasAttachments = true,
            attachmentsJson = json,
            isUrgent = true
        )

        assertEquals(1001L, email.id)
        assertEquals("Иванов Иван Иванович", email.senderName)
        assertEquals("ivanovii@mpei.ru", email.senderEmail)
        assertEquals("У-01-24. Все абоненты;", email.recipient)
        assertEquals("МПС Раздаточный материал", email.subject)
        assertTrue(email.isUnread)
        assertTrue(email.hasAttachments)
        assertTrue(email.isUrgent)

        assertEquals(2, email.attachments.size)
        assertEquals("Раздаточный_материал.pdf", email.attachments[0].fileName)
        assertTrue(email.attachments[0].isPdf)
        assertFalse(email.attachments[0].isDoc)
        assertEquals("2.4 МБ", email.attachments[0].getFormattedSize())

        assertEquals("Задание3.docx", email.attachments[1].fileName)
        assertTrue(email.attachments[1].isDoc)
        assertEquals("43 КБ", email.attachments[1].getFormattedSize())

        val readEmail = email.copy(isUnread = false)
        assertFalse(readEmail.isUnread)
    }

    @Test
    fun `test sender initials generator`() {
        assertEquals("ИИ", getSenderInitials("Иванов Иван Иванович"))
        assertEquals("ПС", getSenderInitials("Петров Сергей"))
        assertEquals("НИ", getSenderInitials("Новостная информация"))
        assertEquals("ИВ", getSenderInitials("Иванов"))
        assertEquals("?", getSenderInitials(""))
    }

    @Test
    fun `test formatEmailDate for today returns hours and minutes`() {
        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 14)
        now.set(Calendar.MINUTE, 30)

        val formatted = formatEmailDate(now.timeInMillis)
        assertEquals("14:30", formatted)
    }

    @Test
    fun `test formatEmailDate for different year returns dd-MM-yy`() {
        val past = Calendar.getInstance()
        past.set(Calendar.YEAR, 2022)
        past.set(Calendar.MONTH, Calendar.JANUARY)
        past.set(Calendar.DAY_OF_MONTH, 15)

        val formatted = formatEmailDate(past.timeInMillis)
        assertEquals("15.01.22", formatted)
    }

    @Test
    fun `test formatEmailDate for earlier date in same year returns day and month`() {
        val earlier = Calendar.getInstance()
        if (earlier.get(Calendar.DAY_OF_YEAR) > 10) {
            earlier.add(Calendar.DAY_OF_YEAR, -5)
        } else {
            earlier.add(Calendar.DAY_OF_YEAR, 5)
        }

        val formatted = formatEmailDate(earlier.timeInMillis)
        val dayString = earlier.get(Calendar.DAY_OF_MONTH).toString()
        assertTrue(formatted.startsWith(dayString))
    }

    @Test
    fun `test group emails by date puts today email into TODAY group`() {
        val nowEmail = EmailMessage(
            id = 1L,
            sender = "test",
            senderName = "test",
            senderEmail = "test@mpei.ru",
            subject = "test",
            snippet = "test",
            bodyText = "test",
            dateMillis = System.currentTimeMillis(),
            isUnread = true
        )

        val pastEmail = EmailMessage(
            id = 2L,
            sender = "test",
            senderName = "test",
            senderEmail = "test@mpei.ru",
            subject = "test",
            snippet = "test",
            bodyText = "test",
            dateMillis = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L),
            isUnread = false
        )

        val grouped = groupEmailsByDate(listOf(nowEmail, pastEmail))
        assertTrue(grouped.containsKey("СЕГОДНЯ"))
        assertTrue(grouped.containsKey("РАНЕЕ"))
        assertEquals(1, grouped["СЕГОДНЯ"]?.size)
        assertEquals(1, grouped["РАНЕЕ"]?.size)
    }

    @Test
    fun `test html table fallback extraction for email with blank bodyText`() {
        val htmlTable = """
            <html><body>
            <table>
                <tr><th>Дисциплина</th><th>Группа</th><th>Дата</th></tr>
                <tr><td>Аналоговые измерительные устройства</td><td>С-12-24</td><td>12.11.2026</td></tr>
            </table>
            <p>От старост - Обязательно персональный ответ.</p>
            </body></html>
        """.trimIndent()

        val parsedText = ru.mpei.assistant.data.network.cleanHtmlToPlainText(htmlTable)
        assertTrue(parsedText.contains("Аналоговые измерительные устройства"))
        assertTrue(parsedText.contains("С-12-24"))
        assertTrue(parsedText.contains("От старост"))
        assertTrue(parsedText.contains("|"))

        val snippet = parsedText.trim().replace("\\s+".toRegex(), " ").take(140)
        assertFalse(snippet.isBlank())
        assertTrue(snippet.contains("Аналоговые измерительные устройства"))
    }

    @Test
    fun `test extractBody on nested multipart with blank plain text and html table`() {
        val session = javax.mail.Session.getInstance(java.util.Properties())
        val msg = javax.mail.internet.MimeMessage(session)

        val htmlContent = "<html><body><table><tr><td>Дисциплина</td><td>С-12-24</td></tr></table></body></html>"

        val multiAlt = javax.mail.internet.MimeMultipart("alternative")
        val textPart = javax.mail.internet.MimeBodyPart().apply {
            setText("\r\n  \n", "utf-8")
        }
        val htmlPart = javax.mail.internet.MimeBodyPart().apply {
            setContent(htmlContent, "text/html; charset=utf-8")
        }
        multiAlt.addBodyPart(textPart)
        multiAlt.addBodyPart(htmlPart)

        val multiRelated = javax.mail.internet.MimeMultipart("related")
        val altWrapper = javax.mail.internet.MimeBodyPart().apply {
            setContent(multiAlt)
        }
        multiRelated.addBodyPart(altWrapper)

        msg.setContent(multiRelated)
        msg.saveChanges()

        val client = ru.mpei.assistant.data.network.OsepMailClient()
        val (bodyText, bodyHtml) = client.extractBody(msg)

        assertFalse("bodyText must not be blank", bodyText.isBlank())
        assertTrue("bodyText must contain table content", bodyText.contains("Дисциплина"))
        assertTrue("bodyText must contain group", bodyText.contains("С-12-24"))
        assertEquals(htmlContent, bodyHtml)
    }

    @Test
    fun `test email page result properties`() {
        val pageResult = ru.mpei.assistant.data.network.EmailPageResult(
            emails = emptyList(),
            oldestMessageNumber = 26,
            hasMore = true,
            totalMessages = 50
        )
        assertEquals(26, pageResult.oldestMessageNumber)
        assertTrue(pageResult.hasMore)
        assertEquals(50, pageResult.totalMessages)
    }
}
