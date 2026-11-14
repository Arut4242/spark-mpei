package ru.mpei.assistant.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.nio.file.Files

object SecurityPolicyChecks {
    @JvmStatic fun main(args: Array<String>) {
        runAll()
        println("URL, size-limit, filename and atomic-save regression checks passed")
    }

    fun runAll() {
        check(WebUrlPolicy.isBarsUrl("https://bars.mpei.ru/bars_web/"))
        check(WebUrlPolicy.isBarsUrl("https://BARS.MPEI.RU:443/path"))
        for (url in listOf("http://bars.mpei.ru/", "https://mpei.ru/", "https://sso.mpei.ru/",
            "https://bars.mpei.ru.attacker.com/", "https://bars-mpei.ru/", "https://bars.mpei.ru@evil.com/",
            "https://evil@bars.mpei.ru/", "https://bars.mpei.ru:8443/", "//bars.mpei.ru/",
            "https://bars.mpei.ru./", "https://bars.mpei.ru\\@evil.com/", "https://bars.mpei.ru/\n")) {
            check(!WebUrlPolicy.isBarsUrl(url)) { "Unexpectedly allowed: $url" }
        }
        for (url in listOf("intent://host/", "javascript:alert(1)", "file:///etc/passwd",
            "content://provider/x", "data:text/html,test", "blob:https://x/a", "mailto:a@b.com")) {
            check(WebUrlPolicy.externalWebUrl(url) == null)
        }
        check(WebUrlPolicy.externalWebUrl("https://example.com/path?q=1") != null)
        check(WebUrlPolicy.externalWebUrl("http://example.com/") != null)
        for (name in listOf("../x", "a/b", "a\\b", "..", ".", "", "a\u0000b", "a\u202eb", "x".repeat(201))) {
            check(runCatching { AttachmentPolicy.validateName(name) }.isFailure)
        }
        AttachmentPolicy.validateName("Отчёт по практике.pdf")

        val directory = Files.createTempDirectory("attachment-policy-").toFile()
        try {
            val first = AttachmentPolicy.cachedFile(directory, 1, "а.pdf")
            val second = AttachmentPolicy.cachedFile(directory, 1, "б.pdf")
            check(first != second && first.parentFile == directory.canonicalFile)
            val target = directory.resolve("complete.bin")
            AttachmentPolicy.saveAtomically(ByteArrayInputStream("complete".toByteArray()), target)
            check(target.readText() == "complete")
            val failing = object : InputStream() {
                override fun read(): Int = throw IOException("Network failure")
            }
            check(runCatching { AttachmentPolicy.saveAtomically(failing, target) }.isFailure)
            check(target.readText() == "complete")
            check(directory.listFiles()!!.none { it.extension == "part" })

            val overflow = ByteArrayInputStream(ByteArray(AttachmentPolicy.MAX_BYTES.toInt() + 1))
            val limited = object : java.io.OutputStream() {
                var size = 0L
                override fun write(value: Int) { size++ }
                override fun write(bytes: ByteArray, offset: Int, length: Int) { size += length }
            }
            check(runCatching { AttachmentPolicy.copyBounded(overflow, limited) }.isFailure)
            check(limited.size <= AttachmentPolicy.MAX_BYTES)
            val exact = ByteArrayInputStream(ByteArray(AttachmentPolicy.MAX_BYTES.toInt()))
            check(AttachmentPolicy.copyBounded(exact, limited) == AttachmentPolicy.MAX_BYTES)

            val tooLarge = directory.resolve("oversize.bin")
            check(runCatching {
                AttachmentPolicy.saveAtomically(ByteArrayInputStream(ByteArray(AttachmentPolicy.MAX_BYTES.toInt() + 1)), tooLarge)
            }.isFailure)
            check(!tooLarge.exists())
            check(directory.listFiles()!!.none { it.extension == "part" })

            val cancelled = directory.resolve("cancelled.bin")
            check(runCatching {
                AttachmentPolicy.saveAtomically(ByteArrayInputStream(byteArrayOf(1)), cancelled) { throw java.io.InterruptedIOException() }
            }.isFailure)
            check(!cancelled.exists())
            check(directory.listFiles()!!.none { it.extension == "part" })
        } finally { directory.deleteRecursively() }
    }
}
