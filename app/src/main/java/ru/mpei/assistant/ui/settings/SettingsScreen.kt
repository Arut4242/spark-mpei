package ru.mpei.assistant.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import ru.mpei.assistant.data.model.update.UpdateState
import ru.mpei.assistant.data.repository.UpdateRepository
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.repository.BarsRepository
import ru.mpei.assistant.data.repository.EmailRepository
import ru.mpei.assistant.ui.theme.AppleOrange
import ru.mpei.assistant.ui.theme.ApplePillShape
import ru.mpei.assistant.ui.theme.appleClickable
import ru.mpei.assistant.util.AppLogger
import ru.mpei.assistant.worker.BackgroundOptimizationHelper
import ru.mpei.assistant.worker.BarsSyncScheduler
import ru.mpei.assistant.worker.EmailCheckWorker

// Stable keys for LazyColumn items
internal const val KEY_HEADER_APPEARANCE = "header_appearance"
internal const val KEY_CARD_APPEARANCE = "card_appearance"
internal const val KEY_HEADER_NOTIFICATIONS = "header_notifications"
internal const val KEY_CARD_NOTIFICATIONS = "card_notifications"
internal const val KEY_HEADER_BARS = "header_bars"
internal const val KEY_CARD_BARS = "card_bars"
internal const val KEY_HEADER_OSEP = "header_osep"
internal const val KEY_CARD_OSEP = "card_osep"
internal const val KEY_HEADER_DIAGNOSTICS = "header_diagnostics"
internal const val KEY_CARD_DIAGNOSTICS = "card_diagnostics"
internal const val KEY_HEADER_ABOUT = "header_about"
internal const val KEY_CARD_ABOUT = "card_about"

// Stable contentTypes for Compose LazyLayout recycling
internal const val CONTENT_TYPE_HEADER = "header"
internal const val CONTENT_TYPE_SETTINGS_CARD = "settings_card"

// Hoisted static shape
private val SettingsCardShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionStorage: SessionStorage,
    barsRepository: BarsRepository,
    emailRepository: EmailRepository,
    updateRepository: UpdateRepository? = null,
    onShowUpdateDialog: (UpdateState) -> Unit = {},
    currentThemeMode: String = SessionStorage.THEME_SYSTEM,
    onThemeModeChanged: (String) -> Unit = {}
) {
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                actions = {
                    IconButton(onClick = { showDiagnosticsDialog = true }) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = "Диагностика и логи",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(
                key = KEY_HEADER_APPEARANCE,
                contentType = CONTENT_TYPE_HEADER
            ) {
                SettingsSectionHeader(
                    title = "Внешний вид",
                    modifier = Modifier.animateItem()
                )
            }

            item(
                key = KEY_CARD_APPEARANCE,
                contentType = CONTENT_TYPE_SETTINGS_CARD
            ) {
                AppearanceSettingsCard(
                    currentThemeMode = currentThemeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    modifier = Modifier.animateItem()
                )
            }

            item(
                key = KEY_HEADER_NOTIFICATIONS,
                contentType = CONTENT_TYPE_HEADER
            ) {
                SettingsSectionHeader(
                    title = "Уведомления",
                    modifier = Modifier
                        .animateItem()
                        .padding(top = 16.dp)
                )
            }

            item(
                key = KEY_CARD_NOTIFICATIONS,
                contentType = CONTENT_TYPE_SETTINGS_CARD
            ) {
                NotificationsSettingsCard(
                    sessionStorage = sessionStorage,
                    modifier = Modifier.animateItem()
                )
            }

            item(
                key = KEY_HEADER_BARS,
                contentType = CONTENT_TYPE_HEADER
            ) {
                SettingsSectionHeader(
                    title = "Аккаунт БАРС",
                    modifier = Modifier
                        .animateItem()
                        .padding(top = 16.dp)
                )
            }

            item(
                key = KEY_CARD_BARS,
                contentType = CONTENT_TYPE_SETTINGS_CARD
            ) {
                BarsSettingsCard(
                    barsRepository = barsRepository,
                    modifier = Modifier.animateItem()
                )
            }

            item(
                key = KEY_HEADER_OSEP,
                contentType = CONTENT_TYPE_HEADER
            ) {
                SettingsSectionHeader(
                    title = "Почта ОСЭП МЭИ",
                    modifier = Modifier
                        .animateItem()
                        .padding(top = 16.dp)
                )
            }

            item(
                key = KEY_CARD_OSEP,
                contentType = CONTENT_TYPE_SETTINGS_CARD
            ) {
                OsepSettingsCard(
                    emailRepository = emailRepository,
                    modifier = Modifier.animateItem()
                )
            }

            item(
                key = KEY_HEADER_DIAGNOSTICS,
                contentType = CONTENT_TYPE_HEADER
            ) {
                SettingsSectionHeader(
                    title = "Диагностика и логирование",
                    modifier = Modifier
                        .animateItem()
                        .padding(top = 16.dp)
                )
            }

            item(
                key = KEY_CARD_DIAGNOSTICS,
                contentType = CONTENT_TYPE_SETTINGS_CARD
            ) {
                DiagnosticsSettingsCard(
                    sessionStorage = sessionStorage,
                    onOpenDiagnostics = { showDiagnosticsDialog = true },
                    modifier = Modifier.animateItem()
                )
            }

            item(
                key = KEY_HEADER_ABOUT,
                contentType = CONTENT_TYPE_HEADER
            ) {
                SettingsSectionHeader(
                    title = "О приложении",
                    modifier = Modifier
                        .animateItem()
                        .padding(top = 16.dp)
                )
            }

            item(
                key = KEY_CARD_ABOUT,
                contentType = CONTENT_TYPE_SETTINGS_CARD
            ) {
                AboutSettingsCard(
                    sessionStorage = sessionStorage,
                    updateRepository = updateRepository,
                    onShowUpdateDialog = onShowUpdateDialog,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }

    if (showDiagnosticsDialog) {
        DiagnosticsDialog(
            sessionStorage = sessionStorage,
            onDismiss = { showDiagnosticsDialog = false }
        )
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
fun AppearanceSettingsCard(
    currentThemeMode: String,
    onThemeModeChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = SettingsCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Тема оформления",
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = when (currentThemeMode) {
                    SessionStorage.THEME_LIGHT -> "Всегда светлая тема оформления"
                    SessionStorage.THEME_DARK -> "Всегда тёмная тема оформления"
                    else -> "Следовать настройкам операционной системы"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // iOS-style Segmented Control
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val options = listOf(
                        Triple(SessionStorage.THEME_LIGHT, "Светлая", Icons.Default.LightMode),
                        Triple(SessionStorage.THEME_DARK, "Тёмная", Icons.Default.DarkMode),
                        Triple(SessionStorage.THEME_SYSTEM, "Авто", Icons.Default.BrightnessAuto)
                    )

                    options.forEach { (mode, label, icon) ->
                        val isSelected = currentThemeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .appleClickable {
                                    onThemeModeChanged(mode)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsSettingsCard(
    sessionStorage: SessionStorage,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(sessionStorage.isNotificationsEnabled) }
    var absenceNotificationsEnabled by remember { mutableStateOf(sessionStorage.isAbsenceNotificationsEnabled) }
    var emailNotificationsEnabled by remember { mutableStateOf(sessionStorage.isEmailNotificationsEnabled) }
    var showAutostartDialog by remember { mutableStateOf(false) }

    Card(
        shape = SettingsCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Уведомления об оценках",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Фоновая проверка новых оценок и баллов за КМ в БАРС",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { isChecked ->
                        notificationsEnabled = isChecked
                        sessionStorage.isNotificationsEnabled = isChecked
                        if (isChecked && !BackgroundOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                            showAutostartDialog = true
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Уведомления о пропусках",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Оповещать о новых пропусках в БАРС и риске потери автомата (6,25%)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = absenceNotificationsEnabled,
                    onCheckedChange = { isChecked ->
                        absenceNotificationsEnabled = isChecked
                        sessionStorage.isAbsenceNotificationsEnabled = isChecked
                        if (isChecked && !BackgroundOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                            showAutostartDialog = true
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Уведомления о почте ОСЭП",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Оповещать о новых письмах из деканата и от преподавателей",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = emailNotificationsEnabled,
                    onCheckedChange = { isChecked ->
                        emailNotificationsEnabled = isChecked
                        sessionStorage.isEmailNotificationsEnabled = isChecked
                        if (isChecked) {
                            EmailCheckWorker.start(context)
                            if (!BackgroundOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                                showAutostartDialog = true
                            }
                        } else {
                            EmailCheckWorker.cancel(context)
                        }
                    }
                )
            }

            if (notificationsEnabled || absenceNotificationsEnabled || emailNotificationsEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Фоновая работа и автозапуск",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Снять ограничения батареи для своевременных уведомлений",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { showAutostartDialog = true }
                    ) {
                        Text("Настроить")
                    }
                }
            }
        }
    }

    if (showAutostartDialog) {
        AutostartDialog(
            onDismiss = { showAutostartDialog = false }
        )
    }
}

@Composable
fun BarsSettingsCard(
    barsRepository: BarsRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isAuthorized by remember {
        mutableStateOf(barsRepository.isAuthorized())
    }

    Card(
        shape = SettingsCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isAuthorized) "Статус: Авторизован" else "Статус: Не авторизован",
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Все авторизационные данные хранятся исключительно в защищенном хранилище Android Keystore на вашем устройстве.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isAuthorized) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            barsRepository.logout()
                            isAuthorized = false
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Выйти из аккаунта")
                }
            }
        }
    }
}

@Composable
fun OsepSettingsCard(
    emailRepository: EmailRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAuthorized by remember { mutableStateOf(emailRepository.isAuthorized()) }
    val login = remember(isAuthorized) { emailRepository.getLogin() }

    Card(
        shape = SettingsCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isAuthorized) "Статус: Авторизован ($login)" else "Статус: Не авторизован",
                fontWeight = FontWeight.Medium,
                color = if (isAuthorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isAuthorized) {
                    "Сервер IMAP: mail.mpei.ru:993 (SSL)\nПисьма проверяются каждые 10 минут в фоновом режиме."
                } else {
                    "Войдите во вкладке «Почта» для автоматической проверки новых писем каждые 10 минут."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isAuthorized) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            emailRepository.logout(context)
                            isAuthorized = false
                            EmailCheckWorker.cancel(context)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Выйти из почты")
                }
            }
        }
    }
}

@Composable
fun AboutSettingsCard(
    sessionStorage: SessionStorage,
    updateRepository: UpdateRepository?,
    onShowUpdateDialog: (UpdateState) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var autoCheckEnabled by remember { mutableStateOf(sessionStorage.isAutoUpdateCheckEnabled) }

    Card(
        shape = SettingsCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Spark", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Версия ${ru.mpei.assistant.BuildConfig.VERSION_NAME} (код ${ru.mpei.assistant.BuildConfig.VERSION_CODE}) • Для студентов НИУ «МЭИ»",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Приложение разработано специально для студентов НИУ «МЭИ». Полностью автономно, не использует сторонних серверов и работает по схеме Local-First.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Switch: Auto update check
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Автопроверка при запуске",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Искать новые версии на GitHub Releases при старте",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoCheckEnabled,
                    onCheckedChange = { checked ->
                        autoCheckEnabled = checked
                        sessionStorage.isAutoUpdateCheckEnabled = checked
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Button: Check for updates
            Button(
                onClick = {
                    if (updateRepository == null) return@Button
                    scope.launch {
                        isChecking = true
                        val res = updateRepository.checkForUpdates(isAutoCheck = false)
                        isChecking = false
                        if (res.isSuccess) {
                            val state = res.getOrThrow()
                            if (state is UpdateState.NoUpdateAvailable) {
                                Toast.makeText(
                                    context,
                                    "У вас установлена последняя версия (${state.currentVersion})",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onShowUpdateDialog(state)
                            }
                        } else {
                            val err = res.exceptionOrNull()?.message ?: "Не удалось проверить обновления"
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isChecking,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Проверка...")
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Проверить обновления")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            var showPrivacyDialog by remember { mutableStateOf(false) }
            if (showPrivacyDialog) {
                ru.mpei.assistant.ui.common.FullPrivacyDialog(onDismiss = { showPrivacyDialog = false })
            }

            OutlinedButton(
                onClick = { showPrivacyDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Политика конфиденциальности")
            }
        }
    }
}

@Composable
fun DiagnosticsSettingsCard(
    sessionStorage: SessionStorage,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isVpn = remember { AppLogger.isVpnActive(context) }

    Card(
        shape = SettingsCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Журнал и отладка", fontWeight = FontWeight.Bold)
            Text(
                text = "Позволяет отследить ошибки авторизации, сетевые запросы к БАРС и почте, а также выявить блокировку VPN.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isVpn) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFEBEE))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "⚠️ Внимание: на телефоне включен VPN! Серверы МЭИ могут блокировать запросы БАРС и почты.",
                        color = Color(0xFFC62828),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { AppLogger.shareLogs(context) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Поделиться", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { AppLogger.copyToClipboard(context) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Копировать", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FilledTonalButton(
                onClick = onOpenDiagnostics,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Открыть консоль логов", fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/Qwerty28369")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Связь с разработчиком: @Qwerty28369", fontSize = 13.sp)
            }
        }
    }
}
