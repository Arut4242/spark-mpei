package ru.mpei.assistant.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.mpei.assistant.data.model.schedule.SearchEntity

@Stable
class SessionStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "bars_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for devices where keystore might throw an issue
        context.getSharedPreferences("bars_secure_prefs_fallback", Context.MODE_PRIVATE)
    }

    var barsCookies: String?
        get() = sharedPreferences.getString(KEY_BARS_COOKIES, null)
        set(value) = sharedPreferences.edit().putString(KEY_BARS_COOKIES, value).apply()

    var barsStudentUrl: String?
        get() = sharedPreferences.getString(KEY_BARS_STUDENT_URL, null)
        set(value) = sharedPreferences.edit().putString(KEY_BARS_STUDENT_URL, value).apply()

    var barsStudentId: String?
        get() = sharedPreferences.getString(KEY_BARS_STUDENT_ID, null)
            ?: barsStudentUrl?.let { url ->
                Regex("""studentID=([a-fA-F0-9\-]{36})""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
            }
        set(value) = sharedPreferences.edit().putString(KEY_BARS_STUDENT_ID, value).apply()

    var selectedGroupId: Long
        get() = sharedPreferences.getLong(KEY_SELECTED_GROUP_ID, -1L)
        set(value) = sharedPreferences.edit().putLong(KEY_SELECTED_GROUP_ID, value).apply()

    var selectedGroupName: String?
        get() = sharedPreferences.getString(KEY_SELECTED_GROUP_NAME, null)
        set(value) = sharedPreferences.edit().putString(KEY_SELECTED_GROUP_NAME, value).apply()

    var selectedScheduleType: String
        get() = sharedPreferences.getString(KEY_SELECTED_SCHEDULE_TYPE, "group") ?: "group"
        set(value) = sharedPreferences.edit().putString(KEY_SELECTED_SCHEDULE_TYPE, value).apply()

    var isNotificationsEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

    var isAbsenceNotificationsEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_ABSENCE_NOTIFICATIONS_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_ABSENCE_NOTIFICATIONS_ENABLED, value).apply()

    var hasShownAutostartPrompt: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTOSTART_PROMPTED, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AUTOSTART_PROMPTED, value).apply()

    var reminderMinutesBefore: Int
        get() = sharedPreferences.getInt(KEY_REMINDER_MINUTES, 15)
        set(value) = sharedPreferences.edit().putInt(KEY_REMINDER_MINUTES, value).apply()

    var lastScheduleSyncTime: Long
        get() = sharedPreferences.getLong(KEY_LAST_SCHEDULE_SYNC, 0L)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_SCHEDULE_SYNC, value).apply()

    var syncIntervalMinutes: Long
        get() = sharedPreferences.getLong(KEY_SYNC_INTERVAL_MINUTES, 30L)
        set(value) = sharedPreferences.edit().putLong(KEY_SYNC_INTERVAL_MINUTES, value).apply()

    var appThemeMode: String
        get() = sharedPreferences.getString(KEY_APP_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = sharedPreferences.edit().putString(KEY_APP_THEME_MODE, value).apply()

    var osepLogin: String?
        get() = sharedPreferences.getString(KEY_OSEP_LOGIN, null)
        set(value) = sharedPreferences.edit().putString(KEY_OSEP_LOGIN, value).apply()

    var osepPassword: String?
        get() = sharedPreferences.getString(KEY_OSEP_PASSWORD, null)
        set(value) = sharedPreferences.edit().putString(KEY_OSEP_PASSWORD, value).apply()

    var isEmailNotificationsEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_EMAIL_NOTIFICATIONS_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_EMAIL_NOTIFICATIONS_ENABLED, value).apply()

    var isAutoUpdateCheckEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_UPDATE_CHECK_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AUTO_UPDATE_CHECK_ENABLED, value).apply()

    var githubRepo: String
        get() = sharedPreferences.getString(KEY_GITHUB_REPO, null)?.takeIf { it == DEFAULT_GITHUB_REPO } ?: DEFAULT_GITHUB_REPO
        set(value) = sharedPreferences.edit().putString(KEY_GITHUB_REPO, value).apply()

    var lastUpdateCheckTime: Long
        get() = sharedPreferences.getLong(KEY_LAST_UPDATE_CHECK_TIME, 0L)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_UPDATE_CHECK_TIME, value).apply()

    private val jsonHelper = Json { ignoreUnknownKeys = true }

    private val _favoriteSchedulesFlow = MutableStateFlow<List<SearchEntity>>(loadFavoriteSchedules())
    val favoriteSchedulesFlow: StateFlow<List<SearchEntity>> = _favoriteSchedulesFlow.asStateFlow()

    private fun loadFavoriteSchedules(): List<SearchEntity> {
        val json = sharedPreferences.getString(KEY_FAVORITE_SCHEDULES, null)
        if (json != null) {
            try {
                return jsonHelper.decodeFromString<List<SearchEntity>>(json)
            } catch (e: Exception) {
                // fall through
            }
        }
        val currentId = selectedGroupId
        val currentName = selectedGroupName
        if (currentId != -1L && !currentName.isNullOrBlank()) {
            val initial = listOf(SearchEntity(id = currentId, label = currentName, type = selectedScheduleType))
            try {
                val initJson = jsonHelper.encodeToString(initial)
                sharedPreferences.edit().putString(KEY_FAVORITE_SCHEDULES, initJson).apply()
            } catch (e: Exception) {}
            return initial
        }
        return emptyList()
    }

    var favoriteSchedules: List<SearchEntity>
        get() = _favoriteSchedulesFlow.value
        set(value) {
            try {
                val json = jsonHelper.encodeToString(value)
                sharedPreferences.edit().putString(KEY_FAVORITE_SCHEDULES, json).apply()
            } catch (e: Exception) {
                // Ignore serialization error
            }
            _favoriteSchedulesFlow.value = value
        }

    fun isScheduleFavorite(id: Long, type: String? = null): Boolean {
        return _favoriteSchedulesFlow.value.any { it.id == id && (type == null || it.type == type) }
    }

    fun toggleFavoriteSchedule(entity: SearchEntity): Boolean {
        val current = _favoriteSchedulesFlow.value.toMutableList()
        val effectiveType = entity.type ?: "group"
        val normalized = entity.copy(type = effectiveType)
        val index = current.indexOfFirst { it.id == normalized.id && (it.type ?: "group") == effectiveType }
        val isAdded: Boolean
        if (index >= 0) {
            current.removeAt(index)
            isAdded = false
        } else {
            current.add(0, normalized)
            isAdded = true
        }
        favoriteSchedules = current
        return isAdded
    }

    fun addFavoriteSchedule(entity: SearchEntity) {
        val current = _favoriteSchedulesFlow.value.toMutableList()
        val effectiveType = entity.type ?: "group"
        val normalized = entity.copy(type = effectiveType)
        current.removeAll { it.id == normalized.id && (it.type ?: "group") == effectiveType }
        current.add(0, normalized)
        favoriteSchedules = current
    }

    fun removeFavoriteSchedule(id: Long, type: String? = null) {
        val current = _favoriteSchedulesFlow.value.filterNot {
            it.id == id && (type == null || (it.type ?: "group") == type)
        }
        favoriteSchedules = current
    }

    var isFavoritesBarExpanded: Boolean
        get() = sharedPreferences.getBoolean(KEY_FAVORITES_BAR_EXPANDED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_FAVORITES_BAR_EXPANDED, value).apply()

    val isOsepAuthorized: Boolean
        get() = !osepLogin.isNullOrBlank() && !osepPassword.isNullOrBlank()

    fun clearSession() {
        sharedPreferences.edit()
            .remove(KEY_BARS_COOKIES)
            .remove(KEY_BARS_STUDENT_URL)
            .remove(KEY_BARS_STUDENT_ID)
            .apply()
    }

    fun clearOsepSession() {
        sharedPreferences.edit()
            .remove(KEY_OSEP_LOGIN)
            .remove(KEY_OSEP_PASSWORD)
            .apply()
    }

    companion object {
        private const val KEY_BARS_COOKIES = "key_bars_cookies"
        private const val KEY_BARS_STUDENT_URL = "key_bars_student_url"
        private const val KEY_BARS_STUDENT_ID = "key_bars_student_id"
        private const val KEY_SELECTED_GROUP_ID = "key_selected_group_id"
        private const val KEY_SELECTED_GROUP_NAME = "key_selected_group_name"
        private const val KEY_SELECTED_SCHEDULE_TYPE = "key_selected_schedule_type"
        private const val KEY_FAVORITE_SCHEDULES = "key_favorite_schedules"
        private const val KEY_FAVORITES_BAR_EXPANDED = "key_favorites_bar_expanded"
        private const val KEY_NOTIFICATIONS_ENABLED = "key_notifications_enabled"
        private const val KEY_ABSENCE_NOTIFICATIONS_ENABLED = "key_absence_notifications_enabled"
        private const val KEY_AUTOSTART_PROMPTED = "key_autostart_prompted"
        private const val KEY_REMINDER_MINUTES = "key_reminder_minutes"
        private const val KEY_LAST_SCHEDULE_SYNC = "key_last_schedule_sync"
        private const val KEY_SYNC_INTERVAL_MINUTES = "key_sync_interval_minutes"
        private const val KEY_OSEP_LOGIN = "key_osep_login"
        private const val KEY_OSEP_PASSWORD = "key_osep_password"
        private const val KEY_EMAIL_NOTIFICATIONS_ENABLED = "key_email_notifications_enabled"
        private const val KEY_AUTO_UPDATE_CHECK_ENABLED = "key_auto_update_check_enabled"
        private const val KEY_GITHUB_REPO = "key_github_repo"
        private const val KEY_LAST_UPDATE_CHECK_TIME = "key_last_update_check_time"
        private const val KEY_APP_THEME_MODE = "key_app_theme_mode"
        const val THEME_SYSTEM = "SYSTEM"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"
        const val DEFAULT_GITHUB_REPO = "Arut4242/spark-mpei"
    }
}
