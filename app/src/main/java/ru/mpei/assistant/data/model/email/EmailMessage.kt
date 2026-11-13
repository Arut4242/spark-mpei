package ru.mpei.assistant.data.model.email

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "emails")
data class EmailMessage(
    @PrimaryKey val id: Long,
    val sender: String,
    val senderName: String,
    val senderEmail: String,
    val recipient: String = "",
    val subject: String,
    val snippet: String,
    val bodyText: String,
    val bodyHtml: String? = null,
    val dateMillis: Long,
    val isUnread: Boolean,
    val hasAttachments: Boolean = false,
    val attachmentsJson: String = "[]",
    val isUrgent: Boolean = false
) {
    @get:Ignore
    val attachments: List<EmailAttachment>
        get() = EmailAttachmentParser.fromJson(attachmentsJson)
}
