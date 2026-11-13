package ru.mpei.assistant.data.model.update

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
    val prerelease: Boolean = false,
    val draft: Boolean = false
)

@Immutable
@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url")
    val downloadUrl: String = "",
    val size: Long = 0L,
    @SerialName("content_type")
    val contentType: String? = null
)

@Immutable
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class NoUpdateAvailable(val currentVersion: String) : UpdateState
    data class UpdateAvailable(
        val newVersion: String,
        val currentVersion: String,
        val changelog: String,
        val downloadUrl: String,
        val apkSize: Long
    ) : UpdateState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateState
    data class ReadyToInstall(val apkPath: String) : UpdateState
    data class Error(val message: String) : UpdateState
}
