package ru.mpei.assistant.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.model.update.UpdateState
import ru.mpei.assistant.data.repository.AbsenceRepository
import ru.mpei.assistant.data.repository.BarsRepository
import ru.mpei.assistant.data.repository.EmailRepository
import ru.mpei.assistant.data.repository.ScheduleRepository
import ru.mpei.assistant.data.repository.UpdateRepository
import ru.mpei.assistant.ui.absences.AbsencesScreen
import ru.mpei.assistant.ui.bars.BarsScreen
import ru.mpei.assistant.ui.email.EmailScreen
import ru.mpei.assistant.ui.schedule.ScheduleScreen
import ru.mpei.assistant.ui.settings.SettingsScreen
import ru.mpei.assistant.ui.theme.AppleBlue
import ru.mpei.assistant.ui.update.UpdateDialog
import java.io.File

@Immutable
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Schedule : Screen("schedule", "Расписание", Icons.Default.DateRange)
    data object Bars : Screen("bars", "БАРС", Icons.Default.School)
    data object Email : Screen("email", "Почта", Icons.Default.Email)
    data object Absences : Screen("absences", "Пропуски", Icons.Default.EventBusy)
    data object Settings : Screen("settings", "Настройки", Icons.Default.Settings)
}

val NAVIGATION_ITEMS: List<Screen> = listOf(
    Screen.Schedule,
    Screen.Bars,
    Screen.Email,
    Screen.Absences,
    Screen.Settings
)

@Composable
fun MainScreen(
    scheduleRepository: ScheduleRepository,
    barsRepository: BarsRepository,
    absenceRepository: AbsenceRepository,
    emailRepository: EmailRepository,
    updateRepository: UpdateRepository,
    sessionStorage: SessionStorage,
    targetRoute: String? = null,
    onRouteHandled: () -> Unit = {},
    currentThemeMode: String = SessionStorage.THEME_SYSTEM,
    onThemeModeChanged: (String) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val unreadEmailsCount by emailRepository.getUnreadCountFlow().collectAsState(initial = 0)
    val scope = rememberCoroutineScope()

    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    // Автоматическая проверка обновлений при старте приложения
    LaunchedEffect(Unit) {
        if (sessionStorage.isAutoUpdateCheckEnabled) {
            delay(1500)
            val res = updateRepository.checkForUpdates(isAutoCheck = true)
            if (res.isSuccess) {
                val state = res.getOrThrow()
                if (state is UpdateState.UpdateAvailable) {
                    updateState = state
                }
            }
        }
    }

    val onNavigateToDestination = remember(navController) {
        { route: String ->
            if (navController.currentDestination?.route != route) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    LaunchedEffect(targetRoute) {
        if (!targetRoute.isNullOrBlank()) {
            onNavigateToDestination(targetRoute)
            onRouteHandled()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
                val currentDensity = LocalDensity.current
                val navDensity = remember(currentDensity) {
                    Density(
                        density = currentDensity.density,
                        fontScale = currentDensity.fontScale.coerceIn(0.85f, 1.0f)
                    )
                }
                CompositionLocalProvider(LocalDensity provides navDensity) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        NAVIGATION_ITEMS.forEach { screen ->
                            val selected = currentRoute == screen.route
                            val onClick = remember(screen, onNavigateToDestination) {
                                { onNavigateToDestination(screen.route) }
                            }
                            NavigationBarItem(
                                icon = {
                                    if (screen is Screen.Email && unreadEmailsCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = AppleBlue,
                                                    contentColor = Color.White
                                                ) {
                                                    Text("$unreadEmailsCount")
                                                }
                                            }
                                        ) {
                                            Icon(screen.icon, contentDescription = screen.title)
                                        }
                                    } else {
                                        Icon(screen.icon, contentDescription = screen.title)
                                    }
                                },
                                label = {
                                    Text(
                                        text = screen.title,
                                        fontSize = 10.sp,
                                        letterSpacing = (-0.2).sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                },
                                alwaysShowLabel = true,
                                selected = selected,
                                onClick = onClick,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Schedule.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable(Screen.Schedule.route) {
                ScheduleScreen(
                    scheduleRepository = scheduleRepository,
                    sessionStorage = sessionStorage
                )
            }
            composable(Screen.Bars.route) {
                BarsScreen(
                    barsRepository = barsRepository,
                    sessionStorage = sessionStorage
                )
            }
            composable(Screen.Email.route) {
                EmailScreen(
                    emailRepository = emailRepository
                )
            }
            composable(Screen.Absences.route) {
                AbsencesScreen(
                    absenceRepository = absenceRepository,
                    barsRepository = barsRepository
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    sessionStorage = sessionStorage,
                    barsRepository = barsRepository,
                    emailRepository = emailRepository,
                    updateRepository = updateRepository,
                    onShowUpdateDialog = { state -> updateState = state },
                    currentThemeMode = currentThemeMode,
                    onThemeModeChanged = onThemeModeChanged
                )
            }
        }
    }

    if (updateState !is UpdateState.Idle) {
        UpdateDialog(
            state = updateState,
            onDismiss = { updateState = UpdateState.Idle },
            onStartDownload = { url ->
                updateRepository.openDownloadInBrowser(url)
                updateState = UpdateState.Idle
            },
            onInstall = { path ->
                val file = File(path)
                updateRepository.installApk(file)
            },
            onRetry = {
                scope.launch {
                    updateState = UpdateState.Checking
                    val res = updateRepository.checkForUpdates(isAutoCheck = false)
                    if (res.isSuccess) {
                        updateState = res.getOrThrow()
                    } else {
                        updateState = UpdateState.Error(
                            res.exceptionOrNull()?.message ?: "Ошибка проверки"
                        )
                    }
                }
            }
        )
    }
}
