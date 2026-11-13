package ru.mpei.assistant.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.mpei.assistant.BuildConfig
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.model.update.GitHubRelease
import ru.mpei.assistant.data.model.update.UpdateState
import ru.mpei.assistant.util.AppLogger
import java.io.File
import java.util.concurrent.TimeUnit

class UpdateRepository(
    private val context: Context,
    private val sessionStorage: SessionStorage,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Сравнивает семантические версии (например, "1.0.5" > "1.0.4").
     * Игнорирует префиксы "v", "V" и суффиксы после дефиса.
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

        val remoteBase = cleanRemote.split("-")[0]
        val currentBase = cleanCurrent.split("-")[0]

        val remoteParts = remoteBase.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentBase.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        // Если базовые версии одинаковы (например, 1.0.5), но удаленная содержит hotfix, а текущая нет
        if (cleanRemote.contains("hotfix", ignoreCase = true) && !cleanCurrent.contains("hotfix", ignoreCase = true)) {
            return true
        }

        return false
    }

    /**
     * Проверяет наличие обновлений на GitHub Releases.
     */
    suspend fun checkForUpdates(
        customRepo: String? = null,
        isAutoCheck: Boolean = false
    ): Result<UpdateState> = withContext(Dispatchers.IO) {
        val repo = (customRepo ?: sessionStorage.githubRepo).trim()
        if (repo.isBlank() || !repo.contains("/")) {
            val msg = "Укажите репозиторий GitHub в формате 'owner/repo'"
            AppLogger.w("UPDATE_REPO", msg)
            return@withContext Result.success(UpdateState.Error(msg))
        }

        try {
            AppLogger.i("UPDATE_REPO", "Checking updates for repo '$repo' (autoCheck=$isAutoCheck)...")
            val url = "https://api.github.com/repos/$repo/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Spark-MPEI-App")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                val errorMsg = when (response.code) {
                    404 -> "Релиз или репозиторий '$repo' не найден на GitHub."
                    403 -> "Превышен лимит запросов к GitHub API. Попробуйте позже."
                    else -> "Ошибка сервера GitHub: HTTP ${response.code}"
                }
                AppLogger.w("UPDATE_REPO", "$errorMsg ($errorBody)")
                return@withContext Result.success(UpdateState.Error(errorMsg))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Пустой ответ от GitHub"))
            val release = json.decodeFromString<GitHubRelease>(body)

            sessionStorage.lastUpdateCheckTime = System.currentTimeMillis()

            val remoteVersion = release.tagName.ifBlank { release.name ?: "" }
            val currentVersion = BuildConfig.VERSION_NAME

            AppLogger.i("UPDATE_REPO", "Latest release tag='$remoteVersion', current='$currentVersion'")

            if (isNewerVersion(remoteVersion, currentVersion)) {
                // Ищем APK файл среди ассетов релиза
                val apkAsset = release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                }

                if (apkAsset == null) {
                    AppLogger.w("UPDATE_REPO", "New release $remoteVersion found, but no .apk asset attached")
                    return@withContext Result.success(
                        UpdateState.Error("Новая версия $remoteVersion опубликована, но в релизе ещё нет файла APK.")
                    )
                }

                val changelog = release.body.orEmpty().trim().ifBlank { "Список изменений не указан разработчиком." }

                Result.success(
                    UpdateState.UpdateAvailable(
                        newVersion = remoteVersion.removePrefix("v"),
                        currentVersion = currentVersion,
                        changelog = changelog,
                        downloadUrl = apkAsset.downloadUrl,
                        apkSize = apkAsset.size
                    )
                )
            } else {
                Result.success(UpdateState.NoUpdateAvailable(currentVersion = currentVersion))
            }
        } catch (e: Exception) {
            AppLogger.e("UPDATE_REPO", "Check for updates failed", e)
            Result.failure(e)
        }
    }

    /**
     * Скачивает APK обновления во внутренний кэш приложения.
     */
    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("UPDATE_REPO", "Starting download APK from: $downloadUrl")
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "spark_update.apk")
            if (apkFile.exists()) apkFile.delete()

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Spark-MPEI-App")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Ошибка загрузки файла: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Пустое тело ответа при скачивании APK"))
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        val progress = if (totalBytes > 0) {
                            (totalBytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        onProgress(progress, totalBytesRead, totalBytes)
                    }
                    output.flush()
                }
            }

            AppLogger.i("UPDATE_REPO", "APK downloaded successfully. Size: ${apkFile.length()} bytes")
            Result.success(apkFile)
        } catch (e: Exception) {
            AppLogger.e("UPDATE_REPO", "Download APK failed", e)
            Result.failure(e)
        }
    }

    /**
     * Открывает прямую ссылку на скачивание или страницу релиза в браузере.
     */
    fun openDownloadInBrowser(downloadUrl: String): Result<Unit> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e("UPDATE_REPO", "Launch browser download failed", e)
            Result.failure(e)
        }
    }

    /**
     * Запускает системный установщик Android для скачанного APK с fallback в браузер.
     */
    fun installApk(apkFile: File, fallbackUrl: String? = null): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(Exception("Файл обновления не найден на устройстве"))
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.w("UPDATE_REPO", "Direct APK install failed: ${e.message}")
            if (!fallbackUrl.isNullOrBlank()) {
                openDownloadInBrowser(fallbackUrl)
            } else {
                Result.failure(e)
            }
        }
    }
}
