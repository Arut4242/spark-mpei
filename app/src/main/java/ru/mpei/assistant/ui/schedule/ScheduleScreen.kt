package ru.mpei.assistant.ui.schedule

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.model.schedule.Lesson
import ru.mpei.assistant.data.model.schedule.SearchEntity
import ru.mpei.assistant.data.repository.ScheduleRepository
import ru.mpei.assistant.ui.theme.AppleCardShape
import ru.mpei.assistant.ui.theme.ApplePillShape
import ru.mpei.assistant.ui.theme.appleClickable
import ru.mpei.assistant.ui.theme.appleCombinedClickable
import ru.mpei.assistant.ui.theme.detectTapAndDragAfterLongPress
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Locale
import kotlin.math.abs

private val DayChipShape = RoundedCornerShape(16.dp)
private val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
private const val BASE_WEEK_PAGE = 500

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

    // Pre-group lessons by date for O(1) page access
    val lessonsByDate = remember(lessons) {
        lessons.groupBy { it.date }.mapValues { (_, list) ->
            list.sortedBy { it.beginLesson ?: "" }
        }
    }

    // Initial smart day and week offset (e.g. on Sunday -> next Monday)
    val initialWeekAndDay = remember {
        val cal = Calendar.getInstance()
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

    // Week pager for 1:1 smooth swiping between weeks
    val weekPagerState = rememberPagerState(initialPage = BASE_WEEK_PAGE + initialWeekAndDay.first) { 1000 }
    val weekOffset by remember { derivedStateOf { weekPagerState.currentPage - BASE_WEEK_PAGE } }

    // Horizontal pager for swiping between Monday..Saturday (pages 0..5)
    val pagerState = rememberPagerState(initialPage = initialWeekAndDay.second) { 6 }

    // Synchronous derived states for day selection and today button visibility
    val selectedDayIndex by remember { derivedStateOf { pagerState.currentPage } }
    val showTodayButton by remember { derivedStateOf { weekOffset != 0 } }

    // When lessons are loaded from database, jump to the nearest upcoming lesson
    var hasAppliedUpcomingJump by remember { mutableStateOf(false) }
    LaunchedEffect(lessons) {
        if (!hasAppliedUpcomingJump && lessons.isNotEmpty()) {
            hasAppliedUpcomingJump = true
            val target = findUpcomingLessonTarget(lessons)
            if (target != null) {
                val (targetWeek, targetDay) = target
                weekPagerState.scrollToPage(BASE_WEEK_PAGE + targetWeek)
                pagerState.scrollToPage(targetDay)
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

    // Compute Monday of the selected week
    val currentMonday = remember(weekOffset) {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
        cal.add(Calendar.WEEK_OF_YEAR, weekOffset)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
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
    val todayCal = remember { Calendar.getInstance() }
    val todayString = remember { dateApiFormat.format(todayCal.time) }

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
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(ApplePillShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                                .appleClickable {
                                    val now = weekDays.getOrElse(pagerState.currentPage) { weekDays[0] }
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
                                            val baseMonday = Calendar.getInstance().apply {
                                                firstDayOfWeek = Calendar.MONDAY
                                                val dow = get(Calendar.DAY_OF_WEEK)
                                                val diff = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
                                                add(Calendar.DAY_OF_YEAR, -diff)
                                                set(Calendar.HOUR_OF_DAY, 0)
                                                set(Calendar.MINUTE, 0)
                                                set(Calendar.SECOND, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }
                                            val diffMillis = pickedMonday.timeInMillis - baseMonday.timeInMillis
                                            val diffWeeks = (diffMillis / (1000L * 60 * 60 * 24 * 7)).toInt()
                                            val dow = pickedCal.get(Calendar.DAY_OF_WEEK)
                                            val dayIdx = if (dow in 2..7) dow - 2 else 0
                                            scope.launch {
                                                weekPagerState.scrollToPage(BASE_WEEK_PAGE + diffWeeks)
                                                pagerState.scrollToPage(dayIdx)
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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

            if (currentGroupId == -1L) {
                // No group selected
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { showGroupDialog = true }) {
                        Text("Выбрать группу")
                    }
                }
            } else {
                // Week Switcher Panel (swipeable)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
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
                                val pageStartStr = remember(pageWeekDays) { monthTitleFormat.format(pageWeekDays.first().time) }
                                val pageEndStr = remember(pageWeekDays) { monthTitleFormat.format(pageWeekDays.last().time) }
                                val pageRangeTitle = "$pageStartStr — $pageEndStr"
                                val pageWeekNumber = remember(pageMonday) { getSemesterWeekNumber(pageMonday) }
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

                                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                                    // Week title & semester week number (centered, padded to avoid fixed buttons)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 44.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = pageRangeTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 3.dp)
                                                .clip(ApplePillShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                                .padding(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$pageWeekNumber неделя",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

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
                                                        if (page != weekPagerState.currentPage) {
                                                            weekPagerState.animateScrollToPage(page)
                                                        }
                                                        pagerState.animateScrollToPage(chip.index)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Fixed Left Arrow Button (never moves on swipe)
                            Box(
                                modifier = Modifier
                                    .padding(start = 12.dp, top = 4.dp)
                                    .align(Alignment.TopStart)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .appleClickable {
                                        scope.launch {
                                            weekPagerState.animateScrollToPage(weekPagerState.currentPage - 1)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Предыдущая неделя",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Fixed Right Arrow Button (never moves on swipe)
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp, top = 4.dp)
                                    .align(Alignment.TopEnd)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .appleClickable {
                                        scope.launch {
                                            weekPagerState.animateScrollToPage(weekPagerState.currentPage + 1)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Следующая неделя",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Quick "Today" jump button with AnimatedVisibility
                        AnimatedVisibility(
                            visible = showTodayButton,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clip(ApplePillShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .appleClickable {
                                        scope.launch {
                                            weekPagerState.animateScrollToPage(BASE_WEEK_PAGE)
                                            val currentDay = todayCal.get(Calendar.DAY_OF_WEEK)
                                            val targetIdx = if (currentDay in 2..7) currentDay - 2 else 0
                                            pagerState.animateScrollToPage(targetIdx)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Today, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Вернуться к сегодня", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

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

                // Swipeable horizontal pager across the 6 days of the week (Mon..Sat)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { pageIndex -> pageIndex }
                ) { pageIndex ->
                    val pageCal = weekDays.getOrElse(pageIndex) { weekDays[0] }
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
                                    .padding(24.dp),
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
                                contentPadding = PaddingValues(16.dp),
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

                                            LessonCard(
                                                lesson = lesson,
                                                isCurrent = isCurrent,
                                                remainingMinutes = remainingMin,
                                                currentProgress = currentProgress,
                                                breakProgress = breakProgress
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
