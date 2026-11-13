package ru.mpei.assistant.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.widget.Toast
import ru.mpei.assistant.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object AppLogger {

    private const val TAG_DEFAULT = "SparkApp"
    private const val MAX_MEMORY_LOGS = 1500
    private const val MAX_LOG_FILE_SIZE = 512 * 1024L // 512 КБ лимит файла логов с ротацией
    private val memoryLogs = ConcurrentLinkedQueue<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private var logFile: File? = null

    fun init(context: Context) {
        try {
            val logsDir = File(context.filesDir, "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            logFile = File(logsDir, "spark_debug.log")
            i(TAG_DEFAULT, "=== Spark v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) launched ===")
            i(TAG_DEFAULT, "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android SDK ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            val vpn = isVpnActive(context)
            if (vpn) {
                w(TAG_DEFAULT, "⚠️ ВНИМАНИЕ: На устройстве активен VPN! Серверы МЭИ (bars.mpei.ru, mail.mpei.ru) могут отклонять запросы!")
            } else {
                i(TAG_DEFAULT, "Сеть: VPN не обнаружен (прямое подключение)")
            }
        } catch (e: Exception) {
            Log.e(TAG_DEFAULT, "Failed to init log file", e)
        }
    }

    fun d(tag: String, message: String) = log("DEBUG", tag, message)
    fun i(tag: String, message: String) = log("INFO", tag, message)
    fun w(tag: String, message: String) = log("WARN", tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) {
            "$message\nException: ${throwable.javaClass.simpleName}: ${throwable.message}\n${Log.getStackTraceString(throwable)}"
        } else message
        log("ERROR", tag, fullMsg)
    }

    /**
     * Автоматическое маскирование персональных и конфиденциальных данных в логах:
     * - Email-адреса (ivanovii@mpei.ru -> ***@mpei.ru)
     * - Логины пользователей и параметры авторизации
     * - ФИО преподавателей и студентов (Иванов И.И. -> И*** И.И., Иванов Иван Иванович -> И*** [ФИО])
     * - UUID / studentID
     * - Сессионные куки и пароли
     */
    fun sanitize(message: String): String {
        var text = message

        // 1. Сессионные куки, токены и пароли
        text = text.replace(Regex("""(?i)(\.ASPXAUTH|password|passwd|pass|token|secret)\s*[:=]\s*([^;&\s,]+)""")) { mr ->
            "${mr.groupValues[1]}=[СКРЫТО]"
        }

        // 2. Email: user@mpei.ru -> ***@mpei.ru
        text = text.replace(Regex("""\b([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+\.[A-Za-z]{2,})\b""")) { mr ->
            "***@" + mr.groupValues[2]
        }

        // 3. Логины после as/for/login/user (слово целиком)
        text = text.replace(Regex("""(?i)\b(as|for|login|user|username)\b\s*(=|:|\s)\s*(['"]?)([A-Za-z0-9_.-]{3,})\3""")) { mr ->
            val keyword = mr.groupValues[1]
            val sep = mr.groupValues[2]
            val quote = mr.groupValues[3]
            val login = mr.groupValues[4]
            if (login.equals("true", ignoreCase = true) ||
                login.equals("false", ignoreCase = true) ||
                login.equals("null", ignoreCase = true) ||
                login.equals("inbox", ignoreCase = true) ||
                login.equals("user", ignoreCase = true)
            ) {
                mr.value
            } else {
                "$keyword$sep$quote***$quote"
            }
        }
        text = text.replace(Regex("""(?i)\bmpei\\([A-Za-z0-9_.-]+)""")) {
            "mpei\\***"
        }

        // 4. ФИО с инициалами (например, "Иванов И.И." или "Иванов И. П.")
        text = text.replace(Regex("""(?<=[^А-Яа-яЁёA-Za-z0-9]|^)([А-ЯЁ][а-яё]{2,})\s+([А-ЯЁ])\.\s*([А-ЯЁ])\.""")) { mr ->
            val firstLetter = mr.groupValues[1].first()
            "$firstLetter*** ${mr.groupValues[2]}.${mr.groupValues[3]}."
        }

        // 5. Полные ФИО (Иванов Иван Иванович)
        text = text.replace(Regex("""(?<=[^А-Яа-яЁёA-Za-z0-9]|^)([А-ЯЁ][а-яё]{2,})\s+([А-ЯЁ][а-яё]{2,})\s+([А-ЯЁ][а-яё]{2,})(?=[^А-Яа-яЁёA-Za-z0-9]|$)""")) { mr ->
            val firstLetter = mr.groupValues[1].first()
            "$firstLetter*** [ФИО]"
        }

        // 6. UUID / GUID: 1f061364-9447-ee11-80da-005056be401c -> ***-401c
        text = text.replace(Regex("""\b([a-fA-F0-9]{8})-([a-fA-F0-9]{4})-([a-fA-F0-9]{4})-([a-fA-F0-9]{4})-([a-fA-F0-9]{8})([a-fA-F0-9]{4})\b""")) { mr ->
            "***-${mr.groupValues[6]}"
        }

        // 7. studentId (числовые или нестандартные): studentId=12345 -> studentId=[СКРЫТО]
        text = text.replace(Regex("""(?i)\b(studentId|student_id|studId)\s*[:=]\s*(['"]?)([A-Za-z0-9_-]{1,35})\2""")) { mr ->
            val v = mr.groupValues[3]
            if (v.startsWith("***-")) mr.value else "${mr.groupValues[1]}=[СКРЫТО]"
        }

        // 8. URL query параметры (https://bars.mpei.ru/...?studentID=... -> https://bars.mpei.ru/...?[ПАРАМЕТРЫ_СКРЫТЫ])
        text = text.replace(Regex("""(https?://[^\s?#]+)\?([^\s#]+)""")) { mr ->
            "${mr.groupValues[1]}?[ПАРАМЕТРЫ_СКРЫТЫ]"
        }

        return text
    }

    private fun log(level: String, tag: String, message: String) {
        val sanitized = sanitize(message)
        val time = timeFormat.format(Date())
        val entry = "[$time][$level][$tag] $sanitized"

        when (level) {
            "DEBUG" -> Log.d(tag, sanitized)
            "INFO" -> Log.i(tag, sanitized)
            "WARN" -> Log.w(tag, sanitized)
            "ERROR" -> Log.e(tag, sanitized)
        }

        memoryLogs.add(entry)
        while (memoryLogs.size > MAX_MEMORY_LOGS) {
            memoryLogs.poll()
        }

        logFile?.let { file ->
            try {
                // Ротация при превышении лимита размера файла
                if (file.exists() && file.length() > MAX_LOG_FILE_SIZE) {
                    val oldFile = File(file.parentFile, "${file.name}.old")
                    if (oldFile.exists()) oldFile.delete()
                    file.renameTo(oldFile)
                }
                FileWriter(file, true).use { writer ->
                    writer.write(entry + "\n")
                }
            } catch (_: Exception) {}
        }
    }

    fun getLogsList(): List<String> = memoryLogs.toList()

    fun getFullLogText(context: Context? = null): String {
        val sb = StringBuilder()
        sb.appendLine("==========================================")
        sb.appendLine("       SPARK MPEI SYSTEM DIAGNOSTICS      ")
        sb.appendLine("==========================================")
        sb.appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("App Version: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        if (context != null) {
            val vpn = isVpnActive(context)
            sb.appendLine("VPN Active: ${if (vpn) "ДА (⚠️ МОЖЕТ БЛОКИРОВАТЬ МЭИ)" else "НЕТ"}")
        }
        sb.appendLine("==========================================")
        sb.appendLine()
        for (log in memoryLogs) {
            sb.appendLine(sanitize(log))
        }
        return sb.toString()
    }

    fun clear() {
        memoryLogs.clear()
        try {
            logFile?.delete()
            logFile?.createNewFile()
        } catch (_: Exception) {}
    }

    fun copyToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Spark Logs", getFullLogText(context))
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Логи скопированы в буфер обмена!", Toast.LENGTH_SHORT).show()
    }

    fun shareLogs(context: Context) {
        val text = getFullLogText(context)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Логи Spark MPEI (${Build.MODEL})")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Отправить логи разработчику")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun isVpnActive(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (_: Exception) {
            false
        }
    }
}
