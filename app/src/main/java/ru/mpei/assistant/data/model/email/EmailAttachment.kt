package ru.mpei.assistant.data.model.email

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

@Immutable
@Serializable
data class EmailAttachment(
    val fileName: String,
    val sizeBytes: Long = 0L,
    val mimeType: String = "application/octet-stream",
    val partIndex: Int = 0
) {
    fun getFormattedSize(): String {
        if (sizeBytes <= 0) return ""
        return when {
            sizeBytes < 1024 -> "$sizeBytes Б"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} КБ"
            else -> {
                val mb = sizeBytes.toDouble() / (1024 * 1024)
                String.format(Locale.US, "%.1f МБ", mb)
            }
        }
    }

    val extension: String
        get() = fileName.substringAfterLast('.', "").lowercase()

    val isPdf: Boolean
        get() = extension == "pdf" || mimeType.contains("pdf")

    val isDoc: Boolean
        get() = extension in listOf("doc", "docx", "rtf", "odt") || mimeType.contains("word")

    val isXls: Boolean
        get() = extension in listOf("xls", "xlsx", "csv", "ods") || mimeType.contains("spreadsheet") || mimeType.contains("excel")

    val isPpt: Boolean
        get() = extension in listOf("ppt", "pptx", "odp") || mimeType.contains("presentation") || mimeType.contains("powerpoint")

    val isArchive: Boolean
        get() = extension in listOf("zip", "rar", "7z", "tar", "gz") || mimeType.contains("zip") || mimeType.contains("compressed")

    val isImage: Boolean
        get() = extension in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp") || mimeType.startsWith("image/")
}

object EmailAttachmentParser {
    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun toJson(list: List<EmailAttachment>): String {
        if (list.isEmpty()) return "[]"
        return try {
            jsonFormat.encodeToString(list)
        } catch (_: Exception) {
            "[]"
        }
    }

    fun fromJson(jsonStr: String?): List<EmailAttachment> {
        if (jsonStr.isNullOrBlank() || jsonStr == "[]") return emptyList()
        return try {
            jsonFormat.decodeFromString(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
