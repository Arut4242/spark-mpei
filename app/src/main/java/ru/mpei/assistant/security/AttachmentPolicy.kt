package ru.mpei.assistant.security

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object AttachmentPolicy {
    const val MAX_BYTES = 25L * 1024 * 1024

    fun validateName(name: String) {
        require(name.isNotBlank() && name != "." && name != ".." && name.length <= 200) {
            "Некорректное имя вложения"
        }
        require(name.none { it == '/' || it == '\\' || it.isISOControl() ||
            it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' }) {
            "Недопустимые символы в имени вложения"
        }
    }

    fun cachedFile(directory: File, emailId: Long, name: String): File {
        validateName(name)
        val hash = MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val extension = name.substringAfterLast('.', "").takeIf {
            it.isNotEmpty() && it.length <= 10 && it.all { c -> c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' }
        }?.let { ".$it" } ?: ""
        val parent = directory.canonicalFile
        val file = File(parent, "${emailId}_$hash$extension").canonicalFile
        check(file.parentFile == parent) { "Attachment path escapes the private directory" }
        return file
    }

    fun copyBounded(input: InputStream, output: OutputStream, checkCancelled: () -> Unit = {}): Long {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            checkCancelled()
            // Read at most the remaining budget plus one byte to detect overflow.
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), MAX_BYTES - total + 1).toInt())
            if (read == -1) return total
            if (read == 0) continue
            if (read.toLong() > MAX_BYTES - total) throw IOException("Вложение превышает лимит 25 МиБ")
            output.write(buffer, 0, read)
            total += read
        }
    }

    fun saveAtomically(input: InputStream, target: File, checkCancelled: () -> Unit = {}) {
        val directory = checkNotNull(target.parentFile)
        check(directory.isDirectory || directory.mkdirs()) { "Не удалось создать каталог вложений" }
        val partial = File.createTempFile("attachment-", ".part", directory)
        try {
            partial.outputStream().use { output ->
                copyBounded(input, output, checkCancelled)
                output.fd.sync()
            }
            checkCancelled()
            Files.move(partial.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            partial.delete()
        }
    }
}
