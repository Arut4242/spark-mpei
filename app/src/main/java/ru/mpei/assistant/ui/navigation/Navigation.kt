package ru.mpei.assistant.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mpei.assistant.ui.theme.appleClickable
import ru.mpei.assistant.ui.theme.isAppInDarkTheme
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
import ru.mpei.assistant.ui.headman.HeadmanScreen
import ru.mpei.assistant.ui.schedule.ScheduleScreen
import ru.mpei.assistant.ui.settings.SettingsScreen
import ru.mpei.assistant.ui.theme.AppleBlue
import ru.mpei.assistant.ui.theme.AppleBottomBarShape
import ru.mpei.assistant.ui.update.UpdateDialog
import java.io.File

@Immutable
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Schedule : Screen("schedule", "Расписание", Icons.Default.DateRange)
    data object Bars : Screen("bars", "БАРС", Icons.Default.School)
    data object Headman : Screen("headman", "Староста", Icons.Default.Groups)
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
    val headmanGroup by barsRepository.headman.group.collectAsState()
    val barsAccountRevision by sessionStorage.barsAccountRevision.collectAsState()
    val navigationItems = remember(headmanGroup) {
        NAVIGATION_ITEMS.toMutableList().apply {
            if (headmanGroup != null) add(indexOf(Screen.Bars) + 1, Screen.Headman)
        }
    }

    LaunchedEffect(barsAccountRevision) {
        barsRepository.headman.reset()
        if (barsRepository.isAuthorized()) barsRepository.headman.discover()
    }
    LaunchedEffect(currentRoute) {
        if (currentRoute == Screen.Bars.route && barsRepository.isAuthorized() && headmanGroup == null) {
            barsRepository.headman.discover()
        }
    }
    LaunchedEffect(headmanGroup, currentRoute) {
        if (headmanGroup == null && currentRoute == Screen.Headman.route) {
            navController.navigate(Screen.Bars.route) {
                popUpTo(Screen.Headman.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Schedule.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(animationSpec = tween(150))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(100))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(150))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(100))
            }
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
            composable(Screen.Headman.route) {
                if (headmanGroup != null) {
                    HeadmanScreen(barsRepository.headman)
                }
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

        val isDark = isAppInDarkTheme()
        val fontSize = if (navigationItems.size > 5) 9.sp else 10.sp
        val letterSpacing = if (navigationItems.size > 5) (-0.4).sp else (-0.2).sp
        val haptic = LocalHapticFeedback.current

        val selectedIndex = remember(currentRoute, navigationItems) {
            val idx = navigationItems.indexOfFirst { it.route == currentRoute }
            if (idx >= 0) idx else 0
        }

        val animatedIndex by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = 380f
            ),
            label = "dockPillPosition"
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = if (isDark) Color(0xF21C1C1E) else Color(0xF2F2F2F7),
                shadowElevation = 8.dp,
                border = BorderStroke(
                    0.5.dp,
                    if (isDark) Color(0x33FFFFFF) else Color(0x24000000)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    val itemCount = navigationItems.size
                    val tabWidth = maxWidth / itemCount

                    // Smooth Gliding Pill Indicator that moves with physical spring physics
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(tabWidth)
                            .graphicsLayer {
                                translationX = (tabWidth * animatedIndex).toPx()
                            }
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDark) Color(0xFF323236) else Color(0xFFDCDCE2)
                            )
                    )

                    // Tab Items in Row
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navigationItems.forEach { screen ->
                            val selected = currentRoute == screen.route
                            val onClick = remember(screen, onNavigateToDestination) {
                                {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigateToDestination(screen.route)
                                }
                            }

                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.12f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "iconScale_${screen.route}"
                            )

                            val iconTranslationY by animateFloatAsState(
                                targetValue = if (selected) -1.5f else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "iconOffsetY_${screen.route}"
                            )

                            val contentColor by animateColorAsState(
                                targetValue = if (selected) AppleBlue else Color(0xFF8E8E93),
                                animationSpec = tween(durationMillis = 200),
                                label = "contentColor_${screen.route}"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 2.dp, vertical = 2.dp)
                                    .clip(CircleShape)
                                    .appleClickable(pressedScale = 0.94f) { onClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (screen is Screen.Email && unreadEmailsCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = AppleBlue,
                                                    contentColor = Color.White
                                                ) {
                                                    Text("$unreadEmailsCount", fontSize = 9.sp)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = screen.icon,
                                                contentDescription = screen.title,
                                                tint = contentColor,
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .graphicsLayer {
                                                        scaleX = iconScale
                                                        scaleY = iconScale
                                                        translationY = iconTranslationY
                                                    }
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.title,
                                            tint = contentColor,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer {
                                                    scaleX = iconScale
                                                    scaleY = iconScale
                                                    translationY = iconTranslationY
                                                }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = screen.title,
                                        fontSize = fontSize,
                                        letterSpacing = letterSpacing,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    var showPrivacyConsent by remember { mutableStateOf(!sessionStorage.hasAcceptedPrivacyPolicy) }
    if (showPrivacyConsent) {
        ru.mpei.assistant.ui.common.WelcomePrivacyDialog(
            onAccept = {
                sessionStorage.hasAcceptedPrivacyPolicy = true
                showPrivacyConsent = false
            }
        )
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
