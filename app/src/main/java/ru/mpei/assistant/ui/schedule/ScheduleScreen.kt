package ru.mpei.assistant.ui.schedule

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import ru.mpei.assistant.worker.LessonReminderScheduler
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.model.schedule.Lesson
import ru.mpei.assistant.data.model.schedule.LessonNote
import ru.mpei.assistant.data.model.schedule.SearchEntity
import ru.mpei.assistant.data.repository.ScheduleRepository
import androidx.compose.ui.graphics.RectangleShape
import ru.mpei.assistant.ui.theme.AppleBlue
import ru.mpei.assistant.ui.theme.AppleCardShape
import ru.mpei.assistant.ui.theme.ApplePillShape
import ru.mpei.assistant.ui.theme.AppleRed
import ru.mpei.assistant.ui.theme.AppleSnackbarHost
import ru.mpei.assistant.ui.theme.AppleTopMenuShape
import ru.mpei.assistant.ui.theme.appleClickable
import ru.mpei.assistant.ui.theme.appleCombinedClickable
import ru.mpei.assistant.ui.theme.detectTapAndDragAfterLongPress
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val DayChipShape = RoundedCornerShape(16.dp)
private val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
private const val BASE_WEEK_PAGE = 500
private const val BASE_DAY_PAGE = BASE_WEEK_PAGE * 6
private const val TOTAL_DAY_PAGES = 1000 * 6

@Immutable
data class DayChipUiModel(
    val index: Int,
    val dayName: String,
    val dayNumber: String,
    val isToday: Boolean
)

fun getSemesterWeekNumber(mondayCal: Calendar): Int {
    val year = mondayCal.get(Calendar.YEAR)
    val month = mondayCal.get(Calendar.MONTH) // 0-based: 0=Jan..11=Dec

    val semesterStartMonday = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        if (month >= Calendar.AUGUST) {
            set(year, Calendar.SEPTEMBER, 1, 0, 0, 0)
        } else if (month <= Calendar.JANUARY) {
            set(year - 1, Calendar.SEPTEMBER, 1, 0, 0, 0)
        } else {
            set(year, Calendar.FEBRUARY, 1, 0, 0, 0)
        }
        set(Calendar.MILLISECOND, 0)
        val dow = get(Calendar.DAY_OF_WEEK)
        val diff = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        add(Calendar.DAY_OF_YEAR, -diff)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val diffMillis = mondayCal.timeInMillis - semesterStartMonday.timeInMillis
    val diffWeeks = Math.round(diffMillis.toDouble() / (1000.0 * 60 * 60 * 24 * 7)).toInt()
    return (diffWeeks + 1).coerceAtLeast(1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    scheduleRepository: ScheduleRepository,
    sessionStorage: SessionStorage
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentGroupId by remember { mutableLongStateOf(sessionStorage.selectedGroupId) }
    var currentGroupName by remember { mutableStateOf(sessionStorage.selectedGroupName) }
    var showGroupDialog by remember { mutableStateOf(currentGroupId == -1L) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val favorites by sessionStorage.favoriteSchedulesFlow.collectAsState()
    var isFavoritesBarExpanded by remember { mutableStateOf(sessionStorage.isFavoritesBarExpanded) }
    var itemToDeleteFromFavorites by remember { mutableStateOf<SearchEntity?>(null) }

    var localFavorites by remember { mutableStateOf(favorites) }
    var draggedItem by remember { mutableStateOf<SearchEntity?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }
    val itemWidths = remember { mutableMapOf<String, Float>() }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var topBarsHeightPx by remember { mutableIntStateOf(0) }
    val topBarsHeightDp = remember(topBarsHeightPx) {
        if (topBarsHeightPx > 0) with(density) { topBarsHeightPx.toDp() } else 200.dp
    }

    LaunchedEffect(favorites) {
        if (draggedItem == null) {
            localFavorites = favorites
        }
    }

    val selectScheduleTarget: (SearchEntity) -> Unit = remember(scheduleRepository, sessionStorage) {
        { item ->
            val selectedType = item.type ?: "group"
            currentGroupId = item.id
            currentGroupName = item.label
            sessionStorage.selectedGroupId = item.id
            sessionStorage.selectedGroupName = item.label
            sessionStorage.selectedScheduleType = selectedType
            showGroupDialog = false

            scope.launch {
                val hasCache = scheduleRepository.hasCachedLessons(item.id)
                if (!hasCache) {
                    isLoading = true
                } else {
                    isRefreshing = true
                }
                val res = scheduleRepository.refreshSchedule(item.id, force = !hasCache, type = selectedType)
                isLoading = false
                isRefreshing = false
                if (res.isFailure) {
                    val err = res.exceptionOrNull()?.message ?: "Не удалось загрузить"
                    snackbarHostState.showSnackbar("Ошибка: $err")
                } else {
                    snackbarHostState.showSnackbar("Расписание загружено!")
                }
            }
        }
    }

    val lessons by remember(scheduleRepository, currentGroupId) {
        scheduleRepository.getLessonsFlow(currentGroupId)
    }.collectAsState(initial = emptyList())

    val notes by remember(scheduleRepository, currentGroupId) {
        scheduleRepository.getNotesFlow(currentGroupId)
    }.collectAsState(initial = emptyList())

    val notesMap = remember(notes) {
        notes.associateBy { "${it.date}_${it.discipline}_${it.beginLesson}" }
    }

    var editingLessonForNote by remember { mutableStateOf<Lesson?>(null) }

    // Pre-group lessons by date for O(1) page access
    val lessonsByDate = remember(lessons) {
        lessons.groupBy { it.date }.mapValues { (_, list) ->
            list.sortedBy { it.beginLesson ?: "" }
        }
    }

    val todayCal = remember { Calendar.getInstance() }
    val todayDow = remember(todayCal) { todayCal.get(Calendar.DAY_OF_WEEK) }
    val todayDayIndex = remember(todayDow) { if (todayDow in 2..7) todayDow - 2 else -1 }

    // Initial smart day and week offset (e.g. on Sunday -> next Monday)
    val initialWeekAndDay = remember(todayCal) {
        val cal = todayCal
        val dow = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sun, 2 = Mon, ..., 7 = Sat
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        when {
            dow == Calendar.SUNDAY -> Pair(1, 0)
            dow == Calendar.SATURDAY && hour >= 17 -> Pair(1, 0)
            dow in 2..6 && hour >= 19 -> {
                val nextDay = dow - 2 + 1
                if (nextDay <= 5) Pair(0, nextDay) else Pair(1, 0)
            }
            dow in 2..7 -> Pair(0, dow - 2)
            else -> Pair(0, 0)
        }
    }

    val upcomingTarget by remember(lessons, initialWeekAndDay) {
        derivedStateOf {
            findUpcomingLessonTarget(lessons) ?: initialWeekAndDay
        }
    }

    val initialDayPage = remember(initialWeekAndDay) {
        BASE_DAY_PAGE + initialWeekAndDay.first * 6 + initialWeekAndDay.second
    }

    // Horizontal pager for smooth continuous swiping across days and weeks
    val pagerState = rememberPagerState(initialPage = initialDayPage) { TOTAL_DAY_PAGES }

    // Week pager for the top week switcher panel
    val weekPagerState = rememberPagerState(initialPage = BASE_WEEK_PAGE + initialWeekAndDay.first) { 1000 }

    // Synchronous derived states for day selection, week offset, and today button visibility
    val currentDayOffset by remember { derivedStateOf { pagerState.currentPage - BASE_DAY_PAGE } }
    val currentWeekOffset by remember { derivedStateOf { Math.floorDiv(currentDayOffset, 6) } }
    val selectedDayIndex by remember { derivedStateOf { Math.floorMod(currentDayOffset, 6) } }
    val weekOffset = currentWeekOffset

    // Synchronize top week switcher when schedule pager swipes across week boundaries (e.g. Sat -> Mon or Mon -> Sat)
    LaunchedEffect(currentWeekOffset) {
        val targetWeekPage = BASE_WEEK_PAGE + currentWeekOffset
        if (weekPagerState.currentPage != targetWeekPage) {
            weekPagerState.animateScrollToPage(targetWeekPage)
        }
    }

    // Synchronize schedule pager when user directly swipes the top week switcher panel
    LaunchedEffect(weekPagerState.currentPage) {
        val targetWeekOffset = weekPagerState.currentPage - BASE_WEEK_PAGE
        val targetDayPage = BASE_DAY_PAGE + targetWeekOffset * 6 + selectedDayIndex
        if (weekPagerState.isScrollInProgress && pagerState.currentPage != targetDayPage) {
            pagerState.scrollToPage(targetDayPage)
        }
    }

    val showTodayButton by remember {
        derivedStateOf {
            val target = upcomingTarget
            val currentWeek = currentWeekOffset
            val currentDay = selectedDayIndex

            // If currently viewing the nearest upcoming lesson target, never show button
            if (currentWeek == target.first && currentDay == target.second) {
                return@derivedStateOf false
            }

            // If today is a weekday in current week and currently viewing today, never show button
            if (currentWeek == 0 && todayDayIndex != -1 && currentDay == todayDayIndex) {
                return@derivedStateOf false
            }

            // If nearest classes are in another week (e.g. next week because today is weekend/Sunday),
            // do not show button while browsing that target week
            if (target.first != 0 && currentWeek == target.first) {
                return@derivedStateOf false
            }

            // Show return button when navigated away from the active target and today
            if (target.first != 0) {
                currentWeek != target.first && currentWeek != 0
            } else {
                currentWeek != 0
            }
        }
    }

    // When lessons are loaded from database, jump to the nearest upcoming lesson
    var hasAppliedUpcomingJump by remember { mutableStateOf(false) }
    LaunchedEffect(lessons) {
        if (!hasAppliedUpcomingJump && lessons.isNotEmpty()) {
            hasAppliedUpcomingJump = true
            val target = findUpcomingLessonTarget(lessons)
            if (target != null) {
                val (targetWeek, targetDay) = target
                val targetDayPage = BASE_DAY_PAGE + targetWeek * 6 + targetDay
                weekPagerState.scrollToPage(BASE_WEEK_PAGE + targetWeek)
                pagerState.scrollToPage(targetDayPage)
            }
        }
    }

    // Auto-update schedule silently in the background
    LaunchedEffect(currentGroupId) {
        if (currentGroupId != -1L) {
            val hasCache = scheduleRepository.hasCachedLessons(currentGroupId)
            if (!hasCache) {
                isLoading = true
            } else {
                isRefreshing = true
            }
            scheduleRepository.refreshSchedule(currentGroupId, force = !hasCache, type = sessionStorage.selectedScheduleType)
            isLoading = false
            isRefreshing = false
        }
    }

    // Formatters
    val dateApiFormat = remember { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()) }
    val dayNumberFormat = remember { SimpleDateFormat("d", Locale.getDefault()) }
    val monthTitleFormat = remember { SimpleDateFormat("d MMMM", Locale("ru")) }

    val baseMonday = remember {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal
    }

    // Compute Monday of the selected week
    val currentMonday = remember(weekOffset, baseMonday) {
        val cal = baseMonday.clone() as Calendar
        cal.add(Calendar.WEEK_OF_YEAR, weekOffset)
        cal
    }

    // Compute dates for Mon..Sat of this week
    val weekDays = remember(currentMonday) {
        (0..5).map { dayIdx ->
            val dayCal = currentMonday.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_YEAR, dayIdx)
            dayCal
        }
    }

    // Check today date and current minute of day
    val todayString = remember(todayCal) { dateApiFormat.format(todayCal.time) }

    var currentMinuteOfDay by remember {
        val cal = Calendar.getInstance()
        mutableIntStateOf(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            val cal = Calendar.getInstance()
            val min = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            if (currentMinuteOfDay != min) {
                currentMinuteOfDay = min
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(bottom = 4.dp)
            ) {
                TopAppBar(
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(ApplePillShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                shape = ApplePillShape
                            )
                            .appleClickable { showGroupDialog = true }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        val scheduleTypeIcon = when (sessionStorage.selectedScheduleType) {
                            "person" -> Icons.Default.Person
                            "auditorium" -> Icons.Default.LocationOn
                            else -> Icons.Default.School
                        }
                        Icon(
                            imageVector = scheduleTypeIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentGroupName ?: "Выбрать расписание",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Сменить расписание",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                },
                actions = {
                    if (currentGroupId != -1L) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFavoritesBarExpanded) Color(0xFFFFB300).copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    shape = CircleShape
                                )
                                .appleClickable {
                                    if (favorites.isEmpty() && currentGroupId != -1L && !currentGroupName.isNullOrBlank()) {
                                        val currentEntity = SearchEntity(
                                            id = currentGroupId,
                                            label = currentGroupName ?: "",
                                            type = sessionStorage.selectedScheduleType
                                        )
                                        sessionStorage.addFavoriteSchedule(currentEntity)
                                        isFavoritesBarExpanded = true
                                        sessionStorage.isFavoritesBarExpanded = true
                                        scope.launch {
                                            snackbarHostState.showSnackbar("«$currentGroupName» добавлено в избранное")
                                        }
                                    } else {
                                        val newState = !isFavoritesBarExpanded
                                        isFavoritesBarExpanded = newState
                                        sessionStorage.isFavoritesBarExpanded = newState
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFavoritesBarExpanded) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (isFavoritesBarExpanded) "Скрыть строку избранного" else "Показать строку избранного",
                                tint = if (isFavoritesBarExpanded) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    shape = CircleShape
                                )
                                .appleClickable {
                                    val now = (baseMonday.clone() as Calendar).apply {
                                        add(Calendar.WEEK_OF_YEAR, currentWeekOffset)
                                        add(Calendar.DAY_OF_YEAR, selectedDayIndex)
                                    }
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            val pickedCal = Calendar.getInstance().apply {
                                                set(year, month, dayOfMonth, 0, 0, 0)
                                            }
                                            val pickedMonday = (pickedCal.clone() as Calendar).apply {
                                                firstDayOfWeek = Calendar.MONDAY
                                                val dow = get(Calendar.DAY_OF_WEEK)
                                                val diff = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
                                                add(Calendar.DAY_OF_YEAR, -diff)
                                            }
                                            val diffMillis = pickedMonday.timeInMillis - baseMonday.timeInMillis
                                            val diffWeeks = (diffMillis / (1000L * 60 * 60 * 24 * 7)).toInt()
                                            val dow = pickedCal.get(Calendar.DAY_OF_WEEK)
                                            val dayIdx = if (dow in 2..7) dow - 2 else 0
                                            scope.launch {
                                                val targetPage = BASE_DAY_PAGE + diffWeeks * 6 + dayIdx
                                                weekPagerState.scrollToPage(BASE_WEEK_PAGE + diffWeeks)
                                                pagerState.scrollToPage(targetPage)
                                            }
                                        },
                                        now.get(Calendar.YEAR),
                                        now.get(Calendar.MONTH),
                                        now.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "Выбрать дату",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            )

            // Quick favorites horizontal switcher bar
            AnimatedVisibility(
                visible = isFavoritesBarExpanded && (favorites.isNotEmpty() || currentGroupId != -1L),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(localFavorites, key = { "${it.type}_${it.id}" }) { fav ->
                        val favType = fav.type ?: "group"
                        val isSelected = fav.id == currentGroupId && favType == sessionStorage.selectedScheduleType
                        val isBeingDragged = draggedItem?.id == fav.id && (draggedItem?.type ?: "group") == favType
                        val favKey = "${fav.type}_${fav.id}"
                        val favIcon = when (favType) {
                            "person" -> Icons.Default.Person
                            "auditorium" -> Icons.Default.LocationOn
                            else -> Icons.Default.School
                        }
                        Row(
                            modifier = Modifier
                                .zIndex(if (isBeingDragged) 10f else 1f)
                                .graphicsLayer {
                                    if (isBeingDragged) {
                                        translationX = dragOffsetX
                                        scaleX = 1.08f
                                        scaleY = 1.08f
                                        shadowElevation = 8.dp.toPx()
                                    }
                                }
                                .animateItem()
                                .onSizeChanged { size ->
                                    itemWidths[favKey] = size.width.toFloat()
                                }
                                .clip(ApplePillShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isBeingDragged) 0.85f else 0.5f)
                                )
                                .pointerInput(favKey) {
                                    detectTapAndDragAfterLongPress(
                                        onTap = {
                                            if (draggedItem == null && !isSelected) {
                                                selectScheduleTarget(fav)
                                            }
                                        },
                                        onDragStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggedItem = fav
                                            dragOffsetX = 0f
                                            totalDragDistance = 0f
                                        },
                                        onDrag = { _, dragAmount ->
                                            dragOffsetX += dragAmount.x
                                            totalDragDistance += abs(dragAmount.x)

                                            val currentIdx = localFavorites.indexOfFirst { it.id == fav.id && (it.type ?: "group") == favType }
                                            if (currentIdx != -1) {
                                                val spacingPx = with(density) { 8.dp.toPx() }
                                                // Dragging right
                                                if (dragOffsetX > 0 && currentIdx < localFavorites.size - 1) {
                                                    val nextFav = localFavorites[currentIdx + 1]
                                                    val nextKey = "${nextFav.type}_${nextFav.id}"
                                                    val nextWidth = itemWidths[nextKey] ?: 120f
                                                    val threshold = nextWidth * 0.5f + spacingPx
                                                    if (dragOffsetX > threshold) {
                                                        val mutable = localFavorites.toMutableList()
                                                        Collections.swap(mutable, currentIdx, currentIdx + 1)
                                                        localFavorites = mutable
                                                        dragOffsetX -= (nextWidth + spacingPx)
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                                // Dragging left
                                                else if (dragOffsetX < 0 && currentIdx > 0) {
                                                    val prevFav = localFavorites[currentIdx - 1]
                                                    val prevKey = "${prevFav.type}_${prevFav.id}"
                                                    val prevWidth = itemWidths[prevKey] ?: 120f
                                                    val threshold = -(prevWidth * 0.5f + spacingPx)
                                                    if (dragOffsetX < threshold) {
                                                        val mutable = localFavorites.toMutableList()
                                                        Collections.swap(mutable, currentIdx, currentIdx - 1)
                                                        localFavorites = mutable
                                                        dragOffsetX += (prevWidth + spacingPx)
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (totalDragDistance < 8f) {
                                                itemToDeleteFromFavorites = fav
                                            } else {
                                                sessionStorage.favoriteSchedules = localFavorites
                                            }
                                            draggedItem = null
                                            dragOffsetX = 0f
                                        },
                                        onDragCancel = {
                                            draggedItem = null
                                            dragOffsetX = 0f
                                            localFavorites = favorites
                                        }
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = favIcon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = fav.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    val isCurrentInFavorites = favorites.any { it.id == currentGroupId && (it.type ?: "group") == sessionStorage.selectedScheduleType }
                    if (!isCurrentInFavorites && currentGroupId != -1L && !currentGroupName.isNullOrBlank()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .clip(ApplePillShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        shape = ApplePillShape
                                    )
                                    .appleClickable {
                                        val entity = SearchEntity(
                                            id = currentGroupId,
                                            label = currentGroupName ?: "",
                                            type = sessionStorage.selectedScheduleType
                                        )
                                        sessionStorage.addFavoriteSchedule(entity)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("«$currentGroupName» добавлено в избранное")
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Добавить в избранное",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "В избранное",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isRefreshing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }
    },
    snackbarHost = { AppleSnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (currentGroupId == -1L) {
            // No group selected
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { showGroupDialog = true }) {
                    Text("Выбрать группу")
                }
            }
        } else {
            var datesPanelHeightPx by remember { mutableIntStateOf(0) }
            var floatingControlsHeightPx by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            val datesPanelHeightDp = remember(datesPanelHeightPx, density) {
                if (datesPanelHeightPx > 0) with(density) { datesPanelHeightPx.toDp() }
                else 100.dp
            }
            val floatingControlsHeightDp = remember(floatingControlsHeightPx, density) {
                if (floatingControlsHeightPx > 0) with(density) { floatingControlsHeightPx.toDp() }
                else if (showTodayButton) 138.dp else 104.dp
            }
            // Clip schedule at the vertical middle of the dates panel so items never emerge from the top
            val clipOffsetDp = remember(datesPanelHeightDp) { 4.dp + datesPanelHeightDp / 2 }
            val topContentPadding = remember(floatingControlsHeightDp, clipOffsetDp) {
                if (floatingControlsHeightDp > clipOffsetDp) floatingControlsHeightDp - clipOffsetDp + 8.dp else 8.dp
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                // Swipeable schedule list clipped at the middle of the dates panel
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = clipOffsetDp)
                        .clipToBounds()
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { pageIndex -> pageIndex },
                        beyondViewportPageCount = 1
                    ) { pageIndex ->
                    val dayOffset = pageIndex - BASE_DAY_PAGE
                    val pageWeekOffset = Math.floorDiv(dayOffset, 6)
                    val pageDayIndex = Math.floorMod(dayOffset, 6)
                    val pageCal = remember(pageIndex, baseMonday) {
                        val cal = baseMonday.clone() as Calendar
                        cal.add(Calendar.WEEK_OF_YEAR, pageWeekOffset)
                        cal.add(Calendar.DAY_OF_YEAR, pageDayIndex)
                        cal
                    }
                    val pageDateString = remember(pageCal) { dateApiFormat.format(pageCal.time) }
                    val pageLessons = remember(lessonsByDate, pageDateString) {
                        lessonsByDate[pageDateString] ?: emptyList()
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isLoading && lessons.isEmpty()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (pageLessons.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 24.dp, end = 24.dp, top = topContentPadding + 16.dp, bottom = 100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    shape = AppleCardShape,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "В этот день пар нет 🎉",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = pageDateString,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (lessons.isEmpty()) {
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(ApplePillShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                                    .appleClickable {
                                                        scope.launch {
                                                            isRefreshing = true
                                                            scheduleRepository.refreshSchedule(
                                                                currentGroupId,
                                                                force = true,
                                                                type = sessionStorage.selectedScheduleType
                                                            )
                                                            isRefreshing = false
                                                        }
                                                    }
                                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                                            ) {
                                                Text(
                                                    "Загрузить расписание семестра",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val scheduleItems = remember(pageLessons) {
                                insertLunchIntoSchedule(pageLessons)
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topContentPadding, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(
                                    items = scheduleItems,
                                    key = { item ->
                                        when (item) {
                                            is ScheduleScreenItem.LessonItem -> item.lesson.dbId
                                            is ScheduleScreenItem.LunchItem -> "lunch_${pageDateString}"
                                        }
                                    },
                                    contentType = { item ->
                                        when (item) {
                                            is ScheduleScreenItem.LessonItem -> "lesson"
                                            is ScheduleScreenItem.LunchItem -> "lunch"
                                        }
                                    }
                                ) { item ->
                                    val isToday = pageDateString == todayString
                                    when (item) {
                                        is ScheduleScreenItem.LessonItem -> {
                                            val lesson = item.lesson
                                            val beginMin = remember(lesson.beginLesson) { parseTimeToMinutes(lesson.beginLesson) }
                                            val endMin = remember(lesson.endLesson) { parseTimeToMinutes(lesson.endLesson) }
                                            val isCurrent = isToday && beginMin != null && endMin != null && currentMinuteOfDay in beginMin until endMin
                                            val remainingMin = if (isCurrent) (endMin - currentMinuteOfDay) else 0
                                            val breakProgress = if (isCurrent) {
                                                calculateLessonBreakProgress(beginMin, endMin, currentMinuteOfDay)
                                            } else null
                                            val currentProgress = breakProgress?.totalProgress ?: 0f

                                            val noteKey = "${lesson.date}_${lesson.discipline}_${lesson.beginLesson}"
                                            val currentNote = notesMap[noteKey]

                                            LessonCard(
                                                lesson = lesson,
                                                isCurrent = isCurrent,
                                                remainingMinutes = remainingMin,
                                                currentProgress = currentProgress,
                                                breakProgress = breakProgress,
                                                noteText = currentNote?.text,
                                                reminderMinutesBefore = currentNote?.reminderMinutesBefore,
                                                reminderTimeMillis = currentNote?.reminderTimeMillis,
                                                onNoteClick = { editingLessonForNote = lesson }
                                            )
                                        }
                                        is ScheduleScreenItem.LunchItem -> {
                                            val lunchProgress = calculateLunchProgress(currentMinuteOfDay, isToday)
                                            LunchCard(
                                                isCurrent = lunchProgress.isCurrent,
                                                remainingMinutes = lunchProgress.remainingMinutes,
                                                currentProgress = lunchProgress.progress
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Controls Overlay (Dates panel + return to today button)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .onSizeChanged { size ->
                            floatingControlsHeightPx = size.height
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Week Switcher Panel - Standalone rounded panel (island)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .onSizeChanged { size ->
                                datesPanelHeightPx = size.height
                            },
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp,
                        border = BorderStroke(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        HorizontalPager(
                            state = weekPagerState,
                            modifier = Modifier.fillMaxWidth(),
                            key = { it }
                        ) { page ->
                            val pageOffset = page - BASE_WEEK_PAGE
                            val pageMonday = remember(pageOffset) {
                                val cal = Calendar.getInstance()
                                cal.firstDayOfWeek = Calendar.MONDAY
                                val dow = cal.get(Calendar.DAY_OF_WEEK)
                                val diff = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
                                cal.add(Calendar.DAY_OF_YEAR, -diff)
                                cal.add(Calendar.WEEK_OF_YEAR, pageOffset)
                                cal.set(Calendar.HOUR_OF_DAY, 0)
                                cal.set(Calendar.MINUTE, 0)
                                cal.set(Calendar.SECOND, 0)
                                cal.set(Calendar.MILLISECOND, 0)
                                cal
                            }
                            val pageWeekDays = remember(pageMonday) {
                                (0..5).map { dayIdx ->
                                    val dayCal = pageMonday.clone() as Calendar
                                    dayCal.add(Calendar.DAY_OF_YEAR, dayIdx)
                                    dayCal
                                }
                            }
                            val pageWeekNumber = remember(pageMonday) { getSemesterWeekNumber(pageMonday) }
                            val pageMonthName = remember(pageMonday) {
                                SimpleDateFormat("LLLL", Locale("ru")).format(pageMonday.time)
                                    .replaceFirstChar { it.uppercase() }
                            }
                            val pageDayChips = remember(pageWeekDays, todayString) {
                                pageWeekDays.mapIndexed { index, dayCal ->
                                    DayChipUiModel(
                                        index = index,
                                        dayName = DAY_NAMES.getOrElse(index) { "" },
                                        dayNumber = dayNumberFormat.format(dayCal.time),
                                        isToday = dateApiFormat.format(dayCal.time) == todayString
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Week badge - centered, sleek, no arrows, no "число - число"
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 6.dp)
                                        .clip(ApplePillShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 12.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "$pageMonthName · $pageWeekNumber неделя",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    pageDayChips.forEach { chip ->
                                        DayChipItem(
                                            chip = chip,
                                            isSelected = (page == weekPagerState.currentPage) && (chip.index == selectedDayIndex),
                                            onClick = {
                                                scope.launch {
                                                    val pageOffset = page - BASE_WEEK_PAGE
                                                    val targetDayPage = BASE_DAY_PAGE + pageOffset * 6 + chip.index
                                                    if (page != weekPagerState.currentPage) {
                                                        weekPagerState.animateScrollToPage(page)
                                                    }
                                                    pagerState.animateScrollToPage(targetDayPage)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Quick "Today" jump button (Standalone smaller capsule with subtle transparency)
                    AnimatedVisibility(
                        visible = showTodayButton,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AppleBlue.copy(alpha = 0.85f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)),
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .padding(top = 2.dp, bottom = 4.dp)
                                .appleClickable {
                                    scope.launch {
                                        val target = upcomingTarget
                                        val (targetWeek, targetDay) = if (todayDayIndex != -1) {
                                            Pair(0, todayDayIndex)
                                        } else {
                                            target
                                        }
                                        val targetDayPage = BASE_DAY_PAGE + targetWeek * 6 + targetDay
                                        weekPagerState.animateScrollToPage(BASE_WEEK_PAGE + targetWeek)
                                        pagerState.animateScrollToPage(targetDayPage)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Today,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (todayDayIndex != -1) "Вернуться к сегодня" else "К расписанию",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGroupDialog) {
        GroupSelectDialog(
            scheduleRepository = scheduleRepository,
            sessionStorage = sessionStorage,
            initialType = sessionStorage.selectedScheduleType,
            onDismiss = { showGroupDialog = false },
            onGroupSelected = selectScheduleTarget
        )
    }

    if (itemToDeleteFromFavorites != null) {
        val target = itemToDeleteFromFavorites!!
        AlertDialog(
            onDismissRequest = { itemToDeleteFromFavorites = null },
            title = { Text("Удалить из избранного?") },
            text = { Text("Удалить «${target.label}» из списка быстрого доступа?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionStorage.removeFavoriteSchedule(target.id, target.type)
                        itemToDeleteFromFavorites = null
                        scope.launch {
                            snackbarHostState.showSnackbar("«${target.label}» удалено из избранного")
                        }
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeleteFromFavorites = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (editingLessonForNote != null) {
        val targetLesson = editingLessonForNote!!
        val key = "${targetLesson.date}_${targetLesson.discipline}_${targetLesson.beginLesson}"
        val existingNote = notesMap[key]
        LessonNoteDialog(
            lesson = targetLesson,
            initialText = existingNote?.text ?: "",
            initialReminderMinutes = existingNote?.reminderMinutesBefore,
            initialReminderTimeMillis = existingNote?.reminderTimeMillis,
            onDismiss = { editingLessonForNote = null },
            onSave = { newText, reminderMinutes, reminderMillis ->
                scope.launch {
                    val finalReminderMillis = reminderMillis ?: if (reminderMinutes != null && reminderMinutes > 0) {
                        LessonReminderScheduler.calculateReminderTimeMillis(
                            targetLesson.date, targetLesson.beginLesson, reminderMinutes
                        )
                    } else null

                    if (newText.isBlank() && reminderMinutes == null && finalReminderMillis == null) {
                        if (existingNote != null) {
                            scheduleRepository.deleteNote(
                                groupId = currentGroupId,
                                date = targetLesson.date,
                                discipline = targetLesson.discipline,
                                beginLesson = targetLesson.beginLesson
                            )
                            LessonReminderScheduler.cancelReminder(
                                context = context,
                                groupId = currentGroupId,
                                date = targetLesson.date,
                                beginLesson = targetLesson.beginLesson
                            )
                            snackbarHostState.showSnackbar("Заметка удалена")
                        }
                    } else {
                        val note = LessonNote(
                            id = existingNote?.id ?: 0,
                            groupId = currentGroupId,
                            date = targetLesson.date,
                            discipline = targetLesson.discipline,
                            beginLesson = targetLesson.beginLesson,
                            text = newText,
                            reminderMinutesBefore = reminderMinutes,
                            reminderTimeMillis = finalReminderMillis
                        )
                        scheduleRepository.saveNote(note)

                        if (finalReminderMillis != null && finalReminderMillis > System.currentTimeMillis()) {
                            LessonReminderScheduler.scheduleReminder(
                                context = context,
                                note = note,
                                discipline = targetLesson.discipline,
                                auditorium = targetLesson.auditorium
                            )
                            val cal = Calendar.getInstance().apply { timeInMillis = finalReminderMillis }
                            val timeStr = SimpleDateFormat("d MMMM в HH:mm", Locale("ru")).format(cal.time)
                            snackbarHostState.showSnackbar("Заметка сохранена, напоминание на $timeStr")
                        } else {
                            LessonReminderScheduler.cancelReminder(
                                context = context,
                                groupId = currentGroupId,
                                date = targetLesson.date,
                                beginLesson = targetLesson.beginLesson
                            )
                            snackbarHostState.showSnackbar("Заметка сохранена")
                        }
                    }
                }
                editingLessonForNote = null
            },
            onDelete = {
                scope.launch {
                    scheduleRepository.deleteNote(
                        groupId = currentGroupId,
                        date = targetLesson.date,
                        discipline = targetLesson.discipline,
                        beginLesson = targetLesson.beginLesson
                    )
                    LessonReminderScheduler.cancelReminder(
                        context = context,
                        groupId = currentGroupId,
                        date = targetLesson.date,
                        beginLesson = targetLesson.beginLesson
                    )
                    snackbarHostState.showSnackbar("Заметка удалена")
                }
                editingLessonForNote = null
            }
        )
    }
}

internal fun parseCustomDateTime(dateStr: String, timeStr: String, defaultYear: Int): Long? {
    val cleanDate = dateStr.trim().replace('/', '.').replace('-', '.')
    val cleanTime = timeStr.trim().replace('.', ':')

    val timeParts = cleanTime.split(":")
    if (timeParts.size != 2) return null
    val hour = timeParts[0].toIntOrNull() ?: return null
    val minute = timeParts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null

    val dateParts = cleanDate.split(".")
    val (day, month, year) = when (dateParts.size) {
        2 -> {
            val d = dateParts[0].toIntOrNull() ?: return null
            val m = dateParts[1].toIntOrNull() ?: return null
            Triple(d, m, defaultYear)
        }
        3 -> {
            if (dateParts[0].length == 4) {
                val y = dateParts[0].toIntOrNull() ?: return null
                val m = dateParts[1].toIntOrNull() ?: return null
                val d = dateParts[2].toIntOrNull() ?: return null
                Triple(d, m, y)
            } else {
                val d = dateParts[0].toIntOrNull() ?: return null
                val m = dateParts[1].toIntOrNull() ?: return null
                val y = dateParts[2].toIntOrNull() ?: return null
                Triple(d, m, y)
            }
        }
        else -> return null
    }

    if (month !in 1..12 || day !in 1..31) return null

    return try {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cal.timeInMillis
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun LessonNoteDialog(
    lesson: Lesson,
    initialText: String,
    initialReminderMinutes: Int? = null,
    initialReminderTimeMillis: Long? = null,
    onDismiss: () -> Unit,
    onSave: (String, Int?, Long?) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialText) }

    val defaultDate = remember(lesson.date) {
        try {
            val parts = lesson.date.replace('-', '.').split('.')
            if (parts.size == 3) {
                if (parts[0].length == 4) {
                    "${parts[2]}.${parts[1]}.${parts[0]}"
                } else {
                    "${parts[0]}.${parts[1]}.${parts[2]}"
                }
            } else {
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
            }
        } catch (e: Exception) {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        }
    }

    val defaultTime = remember(lesson.beginLesson) {
        if (lesson.beginLesson.isNotBlank()) {
            try {
                val parts = lesson.beginLesson.split(":")
                if (parts.size == 2) {
                    val h = parts[0].toInt()
                    val m = parts[1].toInt()
                    var totalMin = h * 60 + m - 15
                    if (totalMin < 0) totalMin = 0
                    String.format(Locale.getDefault(), "%02d:%02d", totalMin / 60, totalMin % 60)
                } else lesson.beginLesson
            } catch (e: Exception) {
                lesson.beginLesson
            }
        } else "09:00"
    }

    val isInitialCustom = initialReminderMinutes == -1 || (initialReminderMinutes == null && initialReminderTimeMillis != null)

    var selectedReminderMinutes by remember {
        mutableStateOf<Int?>(
            if (isInitialCustom) -1 else initialReminderMinutes
        )
    }

    var customDateText by remember {
        mutableStateOf(
            if (initialReminderTimeMillis != null) {
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(initialReminderTimeMillis))
            } else defaultDate
        )
    }

    var customTimeText by remember {
        mutableStateOf(
            if (initialReminderTimeMillis != null) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(initialReminderTimeMillis))
            } else defaultTime
        )
    }

    val defYear = remember(lesson.date) {
        try {
            lesson.date.replace('-', '.').split('.').firstOrNull { it.length == 4 }?.toInt()
                ?: Calendar.getInstance().get(Calendar.YEAR)
        } catch (e: Exception) {
            Calendar.getInstance().get(Calendar.YEAR)
        }
    }

    val customParsedMillis = remember(customDateText, customTimeText, defYear) {
        parseCustomDateTime(customDateText, customTimeText, defYear)
    }

    val hasExistingNote = initialText.isNotBlank() || initialReminderMinutes != null || initialReminderTimeMillis != null

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Amber icon with note pencil
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB300).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (hasExistingNote) "Заметка к занятию" else "Новая заметка",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = lesson.discipline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )

                if (lesson.beginLesson.isNotBlank() && lesson.endLesson.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${lesson.beginLesson} – ${lesson.endLesson}" +
                                if (!lesson.auditorium.isNullOrBlank()) " • ${lesson.auditorium}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            "Например: подготовить отчёт, принести калькулятор, сдать задание...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Reminder section header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (selectedReminderMinutes != null) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = if (selectedReminderMinutes != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Напоминание",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Reminder options row (chips): "Выкл", "15 мин", "1 час", "2 часа", "Своё..."
                val reminderOptions = remember {
                    listOf(
                        Pair(null, "Выкл"),
                        Pair(15, "15 мин"),
                        Pair(60, "1 час"),
                        Pair(120, "2 часа"),
                        Pair(-1, "Своё...")
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(reminderOptions) { (option, label) ->
                        val isSelected = selectedReminderMinutes == option
                        Surface(
                            shape = ApplePillShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                width = 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.appleClickable {
                                if (option != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                selectedReminderMinutes = option
                            }
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Interactive Custom Date & Time input area when "Своё..." is selected
                AnimatedVisibility(
                    visible = selectedReminderMinutes == -1,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Напишите или выберите дату и время:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Date input
                                OutlinedTextField(
                                    value = customDateText,
                                    onValueChange = { customDateText = it },
                                    label = { Text("Дата", fontSize = 10.sp) },
                                    placeholder = { Text("ДД.ММ.ГГГГ", fontSize = 11.sp) },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 13.sp),
                                    modifier = Modifier.weight(1.15f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                    ),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                val cal = Calendar.getInstance().apply {
                                                    customParsedMillis?.let { timeInMillis = it }
                                                }
                                                DatePickerDialog(
                                                    context,
                                                    { _, year, month, dayOfMonth ->
                                                        customDateText = String.format(Locale.getDefault(), "%02d.%02d.%04d", dayOfMonth, month + 1, year)
                                                    },
                                                    cal.get(Calendar.YEAR),
                                                    cal.get(Calendar.MONTH),
                                                    cal.get(Calendar.DAY_OF_MONTH)
                                                ).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = "Выбрать дату",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )

                                // Time input
                                OutlinedTextField(
                                    value = customTimeText,
                                    onValueChange = { customTimeText = it },
                                    label = { Text("Время", fontSize = 10.sp) },
                                    placeholder = { Text("ЧЧ:ММ", fontSize = 11.sp) },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 13.sp),
                                    modifier = Modifier.weight(0.85f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                    ),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                val cal = Calendar.getInstance().apply {
                                                    customParsedMillis?.let { timeInMillis = it }
                                                }
                                                TimePickerDialog(
                                                    context,
                                                    { _, hourOfDay, minute ->
                                                        customTimeText = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                                                    },
                                                    cal.get(Calendar.HOUR_OF_DAY),
                                                    cal.get(Calendar.MINUTE),
                                                    true
                                                ).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = "Выбрать время",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Status info banner
                val reminderStatus = remember(selectedReminderMinutes, lesson.date, lesson.beginLesson, customParsedMillis) {
                    when (selectedReminderMinutes) {
                        null -> null
                        -1 -> {
                            if (customParsedMillis == null) {
                                Pair(false, "Укажите дату (ДД.ММ.ГГГГ) и время (ЧЧ:ММ)")
                            } else if (customParsedMillis <= System.currentTimeMillis()) {
                                Pair(false, "Время для этого напоминания уже прошло")
                            } else {
                                val cal = Calendar.getInstance().apply { timeInMillis = customParsedMillis }
                                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
                                val dateStr = SimpleDateFormat("d MMMM", Locale("ru")).format(cal.time)
                                Pair(true, "Уведомление придёт $dateStr в $timeStr")
                            }
                        }
                        else -> {
                            val triggerMillis = LessonReminderScheduler.calculateReminderTimeMillis(
                                lesson.date, lesson.beginLesson, selectedReminderMinutes!!
                            )
                            if (triggerMillis != null) {
                                if (triggerMillis <= System.currentTimeMillis()) {
                                    Pair(false, "Время для этого напоминания уже прошло")
                                } else {
                                    val cal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
                                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
                                    val dateStr = SimpleDateFormat("d MMMM", Locale("ru")).format(cal.time)
                                    Pair(true, "Уведомление придёт $dateStr в $timeStr")
                                }
                            } else null
                        }
                    }
                }

                if (reminderStatus != null) {
                    val (isSuccess, statusMsg) = reminderStatus
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSuccess) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.NotificationsActive else Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusMsg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Action Row - Perfectly aligned in ONE row!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (hasExistingNote) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = AppleRed),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Удалить",
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Удалить",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(
                                text = "Отмена",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        val isCustomInvalid = selectedReminderMinutes == -1 && (reminderStatus?.first != true)

                        Button(
                            onClick = {
                                val (finalMinutes, finalMillis) = when (selectedReminderMinutes) {
                                    null -> Pair(null, null)
                                    -1 -> Pair(-1, customParsedMillis)
                                    else -> {
                                        val m = LessonReminderScheduler.calculateReminderTimeMillis(
                                            lesson.date, lesson.beginLesson, selectedReminderMinutes!!
                                        )
                                        Pair(selectedReminderMinutes, m)
                                    }
                                }
                                onSave(text.trim(), finalMinutes, finalMillis)
                            },
                            enabled = !isCustomInvalid,
                            shape = ApplePillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(
                                text = "Готово",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}



private fun findUpcomingLessonTarget(lessons: List<Lesson>): Pair<Int, Int>? {
    val now = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val todayDateStr = dateFormat.format(now.time)
    val nowTimeStr = timeFormat.format(now.time)

    // Upcoming lesson: future date OR today's lesson that hasn't finished yet
    val upcoming = lessons
        .filter { lesson ->
            lesson.date > todayDateStr ||
            (lesson.date == todayDateStr && (lesson.endLesson.ifBlank { "23:59" }) >= nowTimeStr)
        }
        .minWithOrNull(compareBy({ it.date }, { it.beginLesson })) ?: return null

    return try {
        val lessonCal = Calendar.getInstance().apply {
            time = dateFormat.parse(upcoming.date) ?: return null
            firstDayOfWeek = Calendar.MONDAY
        }
        val dow = lessonCal.get(Calendar.DAY_OF_WEEK)
        val dayIndex = if (dow in 2..7) dow - 2 else 0

        val todayMonday = (now.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.MONDAY
            val d = get(Calendar.DAY_OF_WEEK)
            val diff = if (d == Calendar.SUNDAY) 6 else d - Calendar.MONDAY
            add(Calendar.DAY_OF_YEAR, -diff)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val lessonMonday = (lessonCal.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.MONDAY
            val d = get(Calendar.DAY_OF_WEEK)
            val diff = if (d == Calendar.SUNDAY) 6 else d - Calendar.MONDAY
            add(Calendar.DAY_OF_YEAR, -diff)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffWeeks = ((lessonMonday.timeInMillis - todayMonday.timeInMillis) / (1000L * 60 * 60 * 24 * 7)).toInt()
        Pair(diffWeeks, dayIndex)
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun DayChipItem(
    chip: DayChipUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val animatedBgColor by animateColorAsState(
        targetValue = if (isSelected) primaryColor else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dayChipBg"
    )
    val textColor = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
    val numberColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
    val dotColor = if (isSelected) Color.White else primaryColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(DayChipShape)
            .background(animatedBgColor)
            .appleClickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = chip.dayName,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = chip.dayNumber,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = numberColor
        )
        Spacer(modifier = Modifier.height(3.dp))
        if (chip.isToday) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        } else {
            Spacer(modifier = Modifier.size(4.dp))
        }
    }
}
