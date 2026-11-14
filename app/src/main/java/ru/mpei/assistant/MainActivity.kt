package ru.mpei.assistant

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import ru.mpei.assistant.data.local.AppDatabase
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.network.BarsWebClient
import ru.mpei.assistant.data.network.MpeiScheduleApi
import ru.mpei.assistant.data.repository.AbsenceRepository
import ru.mpei.assistant.data.repository.BarsRepository
import ru.mpei.assistant.data.repository.EmailRepository
import ru.mpei.assistant.data.repository.ScheduleRepository
import ru.mpei.assistant.data.repository.UpdateRepository
import ru.mpei.assistant.ui.navigation.MainScreen
import ru.mpei.assistant.ui.theme.MpeiTheme

class MainActivity : ComponentActivity() {

    private val targetRoute = mutableStateOf<String?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Permission granted or denied handled gracefully
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetRoute.value = intent?.getStringExtra("navigate_to")

        // Initialize logging and diagnostics
        ru.mpei.assistant.util.AppLogger.init(applicationContext)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent { MaterialTheme { Surface { Text("Открытие защищённой базы…") } } }
        lifecycleScope.launch {
        val database = try {
            withContext(Dispatchers.IO) { AppDatabase.getInstance(applicationContext) }
        } catch (e: AppDatabase.Companion.DatabaseOpenException) {
            setContent {
                MaterialTheme {
                    Surface {
                        Column {
                            Text("Не удалось открыть защищённую базу. Данные не удалены.")
                            Button(onClick = { recreate() }) { Text("Повторить") }
                        }
                    }
                }
            }
            return@launch
        }
        val sessionStorage = SessionStorage(this)
        val scheduleApi = MpeiScheduleApi.create()
        val barsClient = BarsWebClient(sessionStorage)

        val scheduleRepository = ScheduleRepository(
            api = scheduleApi,
            scheduleDao = database.scheduleDao(),
            sessionStorage = sessionStorage
        )

        val barsRepository = BarsRepository(
            barsWebClient = barsClient,
            barsDao = database.barsDao(),
            absenceDao = database.absenceDao(),
            sessionStorage = sessionStorage
        )

        val absenceRepository = AbsenceRepository(
            absenceDao = database.absenceDao(),
            barsDao = database.barsDao(),
            scheduleDao = database.scheduleDao(),
            barsWebClient = barsClient,
            cacheDir = cacheDir
        )

        val emailRepository = EmailRepository(
            emailDao = database.emailDao(),
            sessionStorage = sessionStorage,
            appContext = applicationContext
        )

        val updateRepository = UpdateRepository(
            context = applicationContext,
            sessionStorage = sessionStorage
        )

        ru.mpei.assistant.util.AppLogger.i("STARTUP", "Current session: groupId=${sessionStorage.selectedGroupId} (${sessionStorage.selectedGroupName}), barsAuth=${!sessionStorage.barsCookies.isNullOrBlank()}, osepAuth=${sessionStorage.isOsepAuthorized}")

        setContent {
            var currentThemeMode by remember { mutableStateOf(sessionStorage.appThemeMode) }
           MpeiTheme(
                    themeMode = currentThemeMode,
                    window = this@MainActivity.window
                ) {
                MainScreen(
                    scheduleRepository = scheduleRepository,
                    barsRepository = barsRepository,
                    absenceRepository = absenceRepository,
                    emailRepository = emailRepository,
                    updateRepository = updateRepository,
                    sessionStorage = sessionStorage,
                    targetRoute = targetRoute.value,
                    onRouteHandled = { targetRoute.value = null },
                    currentThemeMode = currentThemeMode,
                    onThemeModeChanged = { newMode ->
                        sessionStorage.appThemeMode = newMode
                        currentThemeMode = newMode
                    }
                )
            }
        }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("navigate_to")?.let { route ->
            targetRoute.value = route
        }
    }
}
