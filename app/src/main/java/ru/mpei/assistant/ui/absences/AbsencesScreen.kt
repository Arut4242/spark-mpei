package ru.mpei.assistant.ui.absences

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import ru.mpei.assistant.ui.theme.isAppInDarkTheme
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ru.mpei.assistant.data.model.absence.SubjectAbsence
import ru.mpei.assistant.data.repository.AbsenceRepository
import ru.mpei.assistant.data.repository.BarsRepository
import ru.mpei.assistant.ui.theme.MpeiGreenBg
import ru.mpei.assistant.ui.theme.MpeiGreenBgDark
import ru.mpei.assistant.ui.theme.MpeiGreenSuccess
import ru.mpei.assistant.ui.theme.MpeiGreenSuccessDark
import ru.mpei.assistant.ui.theme.MpeiRedDebt
import ru.mpei.assistant.ui.theme.MpeiRedDebtBg
import ru.mpei.assistant.ui.theme.MpeiRedDebtBgDark
import ru.mpei.assistant.ui.theme.MpeiRedDebtDark
import java.util.Locale

// Hoisted static values to eliminate GC pressure
private val CardCornerShape = RoundedCornerShape(16.dp)
private val BadgeCornerShape = RoundedCornerShape(8.dp)
private val StatBoxCornerShape = RoundedCornerShape(12.dp)

private val WarnBgLight = Color(0xFFFFF3E0)
private val WarnBgDark = Color(0xFF3E2710)
private val WarnTextLight = Color(0xFFE65100)
private val WarnTextDark = Color(0xFFFFB74D)

fun formatPairs(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod100 in 11..14 -> "$count пар"
        mod10 == 1 -> "$count пара"
        mod10 in 2..4 -> "$count пары"
        else -> "$count пар"
    }
}

fun formatPercent(percent: Float): String {
    return String.format(Locale.US, "%.1f", percent).replace('.', ',')
}

fun isSubjectCumulativeCredit(
    absence: SubjectAbsence,
    barsControlTypeMap: Map<String, String>
): Boolean {
    if (absence.isCumulativeCredit) return true
    val nameLower = absence.subjectName.trim().lowercase()
    val controlType = barsControlTypeMap[nameLower] ?: ""
    if (controlType.contains("совокупност", ignoreCase = true)) return true

    for ((key, value) in barsControlTypeMap) {
        if ((nameLower.contains(key) || key.contains(nameLower)) &&
            value.contains("совокупност", ignoreCase = true)
        ) {
            return true
        }
    }
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsencesScreen(
    absenceRepository: AbsenceRepository,
    barsRepository: BarsRepository? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val absences by absenceRepository.getAbsencesFlow().collectAsState(initial = emptyList())
    val barsSubjects by (barsRepository?.getSubjectsFlow() ?: flowOf(emptyList())).collectAsState(initial = emptyList())

    val barsControlTypeMap = remember(barsSubjects) {
        barsSubjects.associate { it.name.trim().lowercase() to it.controlType }
    }

    var showRuleInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (absenceRepository.isAuthorizedInBars()) {
            absenceRepository.syncWithBarsAndSchedule()
        }
    }

    // Statistics (only for regular exam/credit subjects with automat condition)
    val examSubjects = remember(absences, barsControlTypeMap) {
        absences.filterNot { isSubjectCumulativeCredit(it, barsControlTypeMap) }
    }
    val totalExamSubjects = examSubjects.size
    val isBorderline24 = { item: SubjectAbsence -> item.totalSemesterPairs == 24 && item.unexcusedPairs == 2 }
    val greenCount = examSubjects.count { it.isAutomatAllowedByAbsences && it.remainingAllowedPairs > 0 && !isBorderline24(it) }
    val warningCount = examSubjects.count { (it.isAutomatAllowedByAbsences && it.remainingAllowedPairs == 0 && it.unexcusedPairs > 0) || isBorderline24(it) }
    val redCount = examSubjects.count { !it.isAutomatAllowedByAbsences && !isBorderline24(it) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Пропуски и автомат") },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val res = absenceRepository.syncWithBarsAndSchedule()
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Данные о пропусках обновлены из БАРС")
                                } else {
                                    val msg = res.exceptionOrNull()?.message ?: "Ошибка синхронизации"
                                    snackbarHostState.showSnackbar("Ошибка: $msg")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Синхронизировать с БАРС")
                    }
                    IconButton(onClick = { showRuleInfoDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Правило 6,25% МЭИ")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Rule Banner Card
            item(key = "rule_banner", contentType = "Banner") {
                RuleBannerCard(
                    totalSubjects = totalExamSubjects,
                    greenCount = greenCount,
                    warningCount = warningCount,
                    redCount = redCount,
                    onInfoClick = { showRuleInfoDialog = true }
                )
            }

            if (absences.isEmpty()) {
                item(key = "empty_state", contentType = "Empty") {
                    EmptyAbsencesCard(
                        onSyncClick = {
                            scope.launch {
                                val res = absenceRepository.syncWithBarsAndSchedule()
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Данные о пропусках обновлены из БАРС")
                                } else {
                                    val msg = res.exceptionOrNull()?.message ?: "Ошибка синхронизации"
                                    snackbarHostState.showSnackbar("Ошибка: $msg")
                                }
                            }
                        }
                    )
                }
            } else {
                items(
                    items = absences,
                    key = { it.subjectName },
                    contentType = { "SubjectAbsenceCard" }
                ) { item ->
                    val isCumulative = isSubjectCumulativeCredit(item, barsControlTypeMap)
                    SubjectAbsenceCard(
                        absence = item,
                        isCumulativeCredit = isCumulative
                    )
                }
            }
        }
    }

    // Dialog: MPEI 6.25% Rule Info
    if (showRuleInfoDialog) {
        MpeiRuleDialog(onDismiss = { showRuleInfoDialog = false })
    }
}

@Composable
private fun RuleBannerCard(
    totalSubjects: Int,
    greenCount: Int,
    warningCount: Int,
    redCount: Int,
    onInfoClick: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val bgCard = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                 else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)

    Card(
        shape = CardCornerShape,
        colors = CardDefaults.cardColors(containerColor = bgCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Правило 6,25% НИУ «МЭИ»",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onInfoClick, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Подробнее",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Для допуска к автомату разрешено пропустить не более 6,25% пар без уважительной причины (например, 1 пару при 16 в семестре, 2 при 32). Пропуски по справкам не учитываются. Информация берется напрямую из БАРС.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            if (totalSubjects > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BannerStatusPill(
                        count = greenCount,
                        label = "Автомат OK",
                        color = if (isDark) MpeiGreenSuccessDark else MpeiGreenSuccess
                    )
                    BannerStatusPill(
                        count = warningCount,
                        label = "На грани",
                        color = if (isDark) WarnTextDark else WarnTextLight
                    )
                    BannerStatusPill(
                        count = redCount,
                        label = "Блокировка",
                        color = if (isDark) MpeiRedDebtDark else MpeiRedDebt
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerStatusPill(
    count: Int,
    label: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SubjectAbsenceCard(
    absence: SubjectAbsence,
    isCumulativeCredit: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val isSpecial24 = !isCumulativeCredit && absence.isBorderline24PairsCase
    var showSpecialDialog by remember { mutableStateOf(false) }

    val isOverLimit = !absence.isAutomatAllowedByAbsences && !isSpecial24
    val isBorderline = (absence.isAutomatAllowedByAbsences && absence.remainingAllowedPairs == 0 && absence.unexcusedPairs > 0) || isSpecial24

    val statusBadgeBg = when {
        isCumulativeCredit -> if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                              else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isSpecial24 -> if (isDark) WarnBgDark else WarnBgLight
        isOverLimit -> if (isDark) MpeiRedDebtBgDark else MpeiRedDebtBg
        isBorderline -> if (isDark) WarnBgDark else WarnBgLight
        else -> if (isDark) MpeiGreenBgDark else MpeiGreenBg
    }

    val statusTextColor = when {
        isCumulativeCredit -> MaterialTheme.colorScheme.primary
        isSpecial24 -> if (isDark) WarnTextDark else WarnTextLight
        isOverLimit -> if (isDark) MpeiRedDebtDark else MpeiRedDebt
        isBorderline -> if (isDark) WarnTextDark else WarnTextLight
        else -> if (isDark) MpeiGreenSuccessDark else MpeiGreenSuccess
    }

    val progressColor by animateColorAsState(
        targetValue = when {
            isSpecial24 -> if (isDark) WarnTextDark else WarnTextLight
            isOverLimit -> if (isDark) MpeiRedDebtDark else MpeiRedDebt
            isBorderline -> if (isDark) WarnTextDark else WarnTextLight
            else -> if (isDark) MpeiGreenSuccessDark else MpeiGreenSuccess
        },
        animationSpec = tween(300),
        label = "progressColor"
    )

    // Fraction calculation for progress bar (6.25% is full bar)
    val progressFraction = if (absence.maxAllowedUnexcusedPairs > 0) {
        (absence.unexcusedPairs.toFloat() / absence.maxAllowedUnexcusedPairs.toFloat()).coerceIn(0f, 1f)
    } else if (absence.unexcusedPairs > 0) 1f else 0f

    Card(
        shape = CardCornerShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Subject name
            Text(
                text = absence.subjectName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status Pill
            Row(
                modifier = Modifier
                    .clip(BadgeCornerShape)
                    .background(statusBadgeBg)
                    .then(if (isSpecial24) Modifier.clickable { showSpecialDialog = true } else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when {
                    isCumulativeCredit -> Icons.Default.Info
                    isSpecial24 -> Icons.Default.Warning
                    isOverLimit -> Icons.Default.Warning
                    isBorderline -> Icons.Default.Warning
                    else -> Icons.Default.CheckCircle
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusTextColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val formattedPercent = formatPercent(absence.unexcusedPercent)
                val statusText = when {
                    isCumulativeCredit -> "Предмет с зачётом по совокупности"
                    isSpecial24 -> "Возможен автомат (${formattedPercent}% > 6,25%)"
                    isOverLimit -> "Лимит превышен (${formattedPercent}% > 6,25%) • автомат заблокирован"
                    isBorderline -> "На грани (${formattedPercent}% из 6,25%) • 0 пропусков осталось"
                    else -> "Автомат доступен • осталось пропусков: ${formatPairs(absence.remainingAllowedPairs)}"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = statusTextColor
                )
            }

            if (!isCumulativeCredit) {
                Spacer(modifier = Modifier.height(10.dp))

                // Progress bar (only for regular subjects with automat condition)
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Read-only stat boxes reflecting BARS attendance journal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AbsenceStatBox(
                    modifier = Modifier.weight(1f),
                    title = "Без причины",
                    subtitle = if (isCumulativeCredit) "неуважительная"
                               else if (isSpecial24) "лимит 1,5 пары"
                               else "макс. ${formatPairs(absence.maxAllowedUnexcusedPairs)}",
                    count = absence.unexcusedPairs,
                    accentColor = if (isCumulativeCredit) {
                        if (absence.unexcusedPairs > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                    } else statusTextColor
                )

                AbsenceStatBox(
                    modifier = Modifier.weight(1f),
                    title = "По справке",
                    subtitle = "уважительная",
                    count = absence.excusedPairs,
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (isSpecial24) Modifier.clickable { showSpecialDialog = true } else Modifier
                ) {
                    Text(
                        text = if (isCumulativeCredit) {
                            "Всего пропусков: ${formatPairs(absence.totalAbsencePairs)} из ${absence.totalSemesterPairs} пар"
                        } else {
                            "Всего пар в семестре: ${absence.totalSemesterPairs}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isSpecial24) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) WarnBgDark else WarnBgLight)
                                .clickable { showSpecialDialog = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "[ ! ]",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) WarnTextDark else WarnTextLight
                            )
                        }
                    }
                }
                Text(
                    text = "Журнал БАРС",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (showSpecialDialog) {
        AlertDialog(
            onDismissRequest = { showSpecialDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isDark) WarnTextDark else WarnTextLight
                )
            },
            title = {
                Text(
                    text = "Согласование автомата (24 пары)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Возможно согласование автомата в БАРСе, однако противоречит документальным критериям >6,25%.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Для предметов с периодичностью 1,5 пары в неделю (24 пары за семестр) норма 6,25% составляет ровно 1,5 пары. По официальному положению лимит округляется в меньшую сторону (допускается не более 1 пропуска), поэтому 2 пропуска (8,33%) формально превышают критерий.\n\nОднако в БАРСе могут быть выполнены условия для получения автомата (студент сам подтверждает согласие с автоматом в системе БАРС при выполнении условий по баллам КМ).",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpecialDialog = false }) {
                    Text("Понятно", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun AbsenceStatBox(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    count: Int,
    accentColor: Color
) {
    val isDark = isAppInDarkTheme()
    val bg = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .clip(StatBoxCornerShape)
            .background(bg)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyAbsencesCard(
    onSyncClick: () -> Unit
) {
    Card(
        shape = CardCornerShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.EventBusy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Список предметов пуст",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Данные о предметах, аудиторных парах и пропусках загружаются автоматически из журнала посещаемости БАРС.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onSyncClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Синхронизировать с БАРС")
            }
        }
    }
}

@Composable
private fun MpeiRuleDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Требования к автомату (НИУ «МЭИ»)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Согласно регламенту проведения промежуточной аттестации в НИУ «МЭИ»:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "1. Итоговый балл за контрольные мероприятия (КМ) должен составлять 4,2 и выше.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "2. Пропущено не более 6,25% аудиторных занятий (пар) по дисциплине без уважительной причины.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()
                Text(
                    text = "Как рассчитывается лимит пропусков:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "• 1 пара в неделю (16 пар за семестр): допускается максимум 1 пара (6,25%). Вторая пара — это уже 12,5% (автомат аннулируется).\n" +
                           "• 2 пары в неделю (32 пары за семестр): допускается максимум 2 пары (6,25%).\n" +
                           "• 1,5 пары в неделю (24 пары): допускается не более 1 пары (1,5 * 6,25% = 1,5 -> floor = 1 пара). При 2 пропусках в БАРСе могут быть выполнены условия для согласия с автоматом, однако это противоречит критерию 6,25%.\n" +
                           "• Пропуски по уважительной причине (медицинские справки, донорство, официальные заявления) не входят в 6,25%.\n" +
                           "• Для предметов с зачётом по совокупности экзамен отсутствует, поэтому лимит 6,25% не блокирует аттестацию.\n" +
                           "• Все данные считываются напрямую из журнала посещаемости БАРС.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Понятно")
            }
        }
    )
}
