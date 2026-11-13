package ru.mpei.assistant.ui.bars

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import ru.mpei.assistant.ui.theme.isAppInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import ru.mpei.assistant.data.model.bars.BarsSubject
import ru.mpei.assistant.data.model.bars.ControlMeasure
import ru.mpei.assistant.ui.theme.MpeiGreenBg
import ru.mpei.assistant.ui.theme.MpeiGreenBgDark
import ru.mpei.assistant.ui.theme.MpeiGreenSuccess
import ru.mpei.assistant.ui.theme.MpeiGreenSuccessDark
import ru.mpei.assistant.ui.theme.MpeiRedDebt
import ru.mpei.assistant.ui.theme.MpeiRedDebtDark
import ru.mpei.assistant.ui.theme.MpeiRedDebtBg
import ru.mpei.assistant.ui.theme.MpeiRedDebtBgDark

// Hoisted singletons, shapes, specs, and colors to eliminate per-frame allocations during scrolling
private val SubjectJson = Json { ignoreUnknownKeys = true }
private val CardShape = RoundedCornerShape(16.dp)
private val BadgeShape = RoundedCornerShape(8.dp)
private val ExpandAnimationSpec = tween<IntSize>(durationMillis = 200)

// Static warn & debt colors to eliminate allocations during recomposition
private val WarnBgLight = Color(0xFFFFF3E0)
private val WarnBgDark = Color(0xFF3E2710)
private val WarnTextLight = Color(0xFFE65100)
private val WarnTextDark = Color(0xFFFFB74D)
private val DebtTitleLight = Color(0xFF8B0000)
private val DebtSubtitleLight = Color(0xFF5C0000)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectCard(
    subject: BarsSubject,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    var isExpanded by remember { mutableStateOf(false) }

    val controlMeasures: List<ControlMeasure> = remember(subject.controlMeasuresJson) {
        try {
            SubjectJson.decodeFromString(subject.controlMeasuresJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    val hasDebt = remember(controlMeasures) { controlMeasures.any { it.isDebt } }

    val simulatedGrades = remember { mutableStateMapOf<String, String>() }
    val retakeKmIds = remember { mutableStateMapOf<String, Boolean>() }

    // Automatically mark original debts or '2's as retake candidates
    androidx.compose.runtime.LaunchedEffect(controlMeasures) {
        controlMeasures.forEach { m ->
            if (m.grade?.trim() == "2" || m.isDebt) {
                retakeKmIds[m.id] = true
            }
        }
    }

    val activeMeasures: List<ControlMeasure> = remember(controlMeasures, simulatedGrades.toMap(), retakeKmIds.toMap()) {
        if (simulatedGrades.isEmpty()) {
            controlMeasures
        } else {
            controlMeasures.map { m ->
                val sim = simulatedGrades[m.id]
                if (sim != null) {
                    val cleanSim = sim.ifBlank { null }
                    m.copy(grade = cleanSim, isDebt = cleanSim == "2")
                } else {
                    m
                }
            }
        }
    }

    val activeRetakesSet = remember(retakeKmIds.toMap()) {
        retakeKmIds.filterValues { it }.keys
    }

    val forecast = remember(activeMeasures, subject.controlType, activeRetakesSet) {
        GradeForecastHelper.calculateForecast(activeMeasures, subject.controlType, activeRetakesSet)
    }

    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "card_arrow_rotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // Removes ugly dark ripple circle artifact
            ) {
                isExpanded = !isExpanded
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = ExpandAnimationSpec)
        ) {
            // Header Row: Title, Teacher, Score, Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.name.trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subject.teacher.isNotBlank()) {
                        Text(
                            text = subject.teacher.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Current control score badge
                subject.currentControlScore?.let { score ->
                    val badgeBg = if (hasDebt) {
                        if (isDark) MpeiRedDebtBgDark else MpeiRedDebtBg
                    } else {
                        if (isDark) MpeiGreenBgDark else MpeiGreenBg
                    }
                    val badgeText = if (hasDebt) {
                        if (isDark) MpeiRedDebtDark else MpeiRedDebt
                    } else {
                        if (isDark) MpeiGreenSuccessDark else MpeiGreenSuccess
                    }

                    Box(
                        modifier = Modifier
                            .clip(BadgeShape)
                            .background(badgeBg)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = score,
                            fontWeight = FontWeight.Bold,
                            color = badgeText,
                            fontSize = 14.sp
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 6.dp, top = 4.dp)
                        .graphicsLayer { rotationZ = rotationState }
                )
            }

            // Quick KM grades row (visible immediately without expanding!)
            if (controlMeasures.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    controlMeasures.forEach { km ->
                        key(km.id) {
                            KmQuickBadge(
                                grade = km.grade,
                                isDebt = km.isDebt,
                                isDark = isDark
                            )
                        }
                    }
                }
            }

            // Compact Badges: Control Type, Credits, Deadline
            val hasChips = subject.controlType.isNotBlank() || subject.credits.isNotBlank() || subject.deadline.isNotBlank()
            if (hasChips) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (subject.controlType.isNotBlank()) {
                        BadgePill(text = subject.controlType)
                    }
                    if (subject.credits.isNotBlank()) {
                        BadgePill(text = subject.credits)
                    }
                    if (subject.deadline.isNotBlank()) {
                        BadgePill(text = subject.deadline)
                    }
                }
            }

            // Expandable KM details
            if (isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (forecast != null && forecast.totalCount > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        GradeForecastSection(
                            forecast = forecast,
                            controlMeasures = controlMeasures,
                            simulatedGrades = simulatedGrades,
                            retakeKmMap = retakeKmIds,
                            isDark = isDark,
                            onGradeSelected = { kmId, grade ->
                                if (grade == null) {
                                    simulatedGrades.remove(kmId)
                                } else {
                                    simulatedGrades[kmId] = grade
                                }
                            },
                            onToggleRetake = { kmId ->
                                val cur = retakeKmIds[kmId] ?: false
                                retakeKmIds[kmId] = !cur
                            },
                            onResetSimulation = {
                                simulatedGrades.clear()
                                retakeKmIds.clear()
                                controlMeasures.forEach { m ->
                                    if (m.grade?.trim() == "2" || m.isDebt) {
                                        retakeKmIds[m.id] = true
                                    }
                                }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Контрольные мероприятия (КМ)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (controlMeasures.isEmpty()) {
                        Text(
                            text = "Контрольные мероприятия не выставлены",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        controlMeasures.forEach { km ->
                            key(km.id) {
                                KmRowItem(
                                    km = km,
                                    isDark = isDark
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }

                    // Summary Section: Weeks
                    val hasWeeks = !subject.controlWeek1.isNullOrBlank() ||
                            !subject.controlWeek2.isNullOrBlank() ||
                            !subject.controlWeek3.isNullOrBlank()

                    if (hasWeeks) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            subject.controlWeek1?.let { Text("Неделя 1: $it", fontSize = 12.sp) }
                            subject.controlWeek2?.let { Text("Неделя 2: $it", fontSize = 12.sp) }
                            subject.controlWeek3?.let { Text("Неделя 3: $it", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KmQuickBadge(
    grade: String?,
    isDebt: Boolean,
    modifier: Modifier = Modifier,
    isDark: Boolean = isAppInDarkTheme()
) {
    val cleanGrade = grade?.trim()?.takeIf { it.isNotBlank() && it != "—" }

    val isFail = isDebt || cleanGrade == "2" || cleanGrade?.contains("незач", ignoreCase = true) == true
    val isWarn = cleanGrade == "3"
    val isPass = cleanGrade != null && !isFail && !isWarn

    val bg = when {
        isFail -> if (isDark) MpeiRedDebtBgDark else MpeiRedDebtBg
        isWarn -> if (isDark) WarnBgDark else WarnBgLight
        isPass -> if (isDark) MpeiGreenBgDark else MpeiGreenBg
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = when {
        isFail -> if (isDark) MpeiRedDebtDark else MpeiRedDebt
        isWarn -> if (isDark) WarnTextDark else WarnTextLight
        isPass -> if (isDark) MpeiGreenSuccessDark else MpeiGreenSuccess
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(BadgeShape)
            .background(bg)
            .defaultMinSize(minWidth = 32.dp, minHeight = 28.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = cleanGrade ?: "—",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
fun BadgePill(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(BadgeShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun KmRowItem(
    km: ControlMeasure,
    modifier: Modifier = Modifier,
    isDark: Boolean = isAppInDarkTheme()
) {
    val bgColor = if (km.isDebt) {
        if (isDark) MpeiRedDebtBgDark else MpeiRedDebtBg
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val gradeColor = if (km.isDebt) {
        if (isDark) MpeiRedDebtDark else MpeiRedDebt
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val titleTextColor = if (km.isDebt && !isDark) {
        DebtTitleLight
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleTextColor = if (km.isDebt && !isDark) {
        DebtSubtitleLight
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(BadgeShape)
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = km.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = titleTextColor
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (km.weightPercent > 0) {
                    Text(
                        text = "Вес: ${km.weightPercent}%",
                        fontSize = 11.sp,
                        color = subtitleTextColor
                    )
                }
                if (km.period.isNotBlank()) {
                    Text(
                        text = "Срок: ${km.period}",
                        fontSize = 11.sp,
                        color = subtitleTextColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Grade Column
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = km.grade ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = gradeColor
            )
            km.dateReceived?.let { date ->
                Text(
                    text = date,
                    fontSize = 10.sp,
                    color = subtitleTextColor
                )
            }
        }
    }
}

@Composable
fun GradeForecastSection(
    forecast: SubjectGradeForecast,
    controlMeasures: List<ControlMeasure>,
    simulatedGrades: Map<String, String>,
    retakeKmMap: Map<String, Boolean>,
    isDark: Boolean,
    onGradeSelected: (kmId: String, grade: String?) -> Unit,
    onToggleRetake: (kmId: String) -> Unit,
    onResetSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerBg = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    val isSimulating = simulatedGrades.isNotEmpty() || retakeKmMap.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerBg)
            .padding(12.dp)
    ) {
        // Header Row: Title, Score Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSimulating) "Калькулятор (Симуляция)" else "Прогноз и калькулятор",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Score Badge
            val scoreBg = if (forecast.flooredScore >= GradeForecastHelper.THRESHOLD_AUTOMAT_5) {
                if (isDark) MpeiGreenBgDark else MpeiGreenBg
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.25f else 0.15f)
            }
            val scoreColor = if (forecast.flooredScore >= GradeForecastHelper.THRESHOLD_AUTOMAT_5) {
                if (isDark) MpeiGreenSuccessDark else MpeiGreenSuccess
            } else {
                MaterialTheme.colorScheme.primary
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(scoreBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Балл: ${String.format(java.util.Locale.US, "%.1f", forecast.flooredScore)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            }
        }

        // Automat banner / Subtitle info
        if (forecast.flooredScore >= GradeForecastHelper.THRESHOLD_AUTOMAT_5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) MpeiGreenBgDark else MpeiGreenBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (isDark) MpeiGreenSuccessDark else MpeiGreenSuccess,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Балл ≥ 4.2 — Автомат получен! 🎉",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MpeiGreenSuccessDark else MpeiGreenSuccess
                )
            }
        } else {
            val neededToAutomat = (GradeForecastHelper.THRESHOLD_AUTOMAT_5 - forecast.flooredScore).coerceAtLeast(0.1f)
            Text(
                text = "До автомата (4.2) не хватает: ${String.format(java.util.Locale.US, "%.1f", neededToAutomat)} балла • Округление десятых вниз",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }

        // Note if retake formula is active in calculation
        if (forecast.hasRetakes) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.10f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Учтено правило пересдачи МЭИ: (2 + Оценка) / 2",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Subtitle status: graded vs total KMs
        val progressText = if (forecast.hasWeights) {
            "Учтено ${forecast.gradedCount} из ${forecast.totalCount} КМ • Закрыто ${forecast.closedWeightPercent}% веса"
        } else {
            "Учтено ${forecast.gradedCount} из ${forecast.totalCount} КМ"
        }

        Text(
            text = progressText,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Target Forecast Rows
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            forecast.targets.forEach { target ->
                TargetGradeRow(target = target, isDark = isDark)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Simulation Section ("Что если")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Смоделировать свои оценки:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isSimulating) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onResetSimulation)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Сбросить",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Сбросить",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // List of KMs with interactive score selectors
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            controlMeasures.forEach { km ->
                val isRetake = retakeKmMap[km.id] == true || (km.grade?.trim() == "2" || km.isDebt)
                KmSimulationRow(
                    km = km,
                    simulatedGrade = simulatedGrades[km.id],
                    isRetakeActive = isRetake,
                    isDark = isDark,
                    onSelectGrade = { grade ->
                        onGradeSelected(km.id, grade)
                    },
                    onToggleRetake = {
                        onToggleRetake(km.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun KmSimulationRow(
    km: ControlMeasure,
    simulatedGrade: String?,
    isRetakeActive: Boolean,
    isDark: Boolean,
    onSelectGrade: (String?) -> Unit,
    onToggleRetake: () -> Unit
) {
    val originalGrade = km.grade?.trim()
    val currentSelected = simulatedGrade ?: originalGrade
    val isModified = simulatedGrade != null && simulatedGrade != originalGrade

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.7f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = km.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isModified) {
                        Text(
                            text = " (изм.)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (km.weightPercent > 0) {
                        Text(
                            text = "Вес: ${km.weightPercent}%",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isRetakeActive) {
                        Text(
                            text = "• (2 + Оц) / 2",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Retake toggle chip
            val retakeChipBg = if (isRetakeActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.3f else 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
            val retakeChipColor = if (isRetakeActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(retakeChipBg)
                    .clickable(onClick = onToggleRetake)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isRetakeActive) "Пересдача ✓" else "+ Пересдача",
                    fontSize = 10.sp,
                    fontWeight = if (isRetakeActive) FontWeight.Bold else FontWeight.Normal,
                    color = retakeChipColor
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Buttons for 2, 3, 4, 5
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("2", "3", "4", "5").forEach { gradeOption ->
                    val isChosen = currentSelected == gradeOption
                    val buttonBg = when {
                        isChosen && isModified -> MaterialTheme.colorScheme.primary
                        isChosen -> if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                    val textColor = when {
                        isChosen && isModified -> Color.White
                        isChosen -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    val label = if (isRetakeActive && gradeOption != "2") {
                        val eff = (2f + gradeOption.toFloat()) / 2f
                        "$gradeOption (${String.format(java.util.Locale.US, "%.1f", eff)})"
                    } else {
                        gradeOption
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(buttonBg)
                            .clickable {
                                if (isChosen && isModified) {
                                    onSelectGrade(null)
                                } else {
                                    onSelectGrade(gradeOption)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetGradeRow(
    target: TargetGradeForecast,
    isDark: Boolean
) {
    val badgeBg = when (target.status) {
        ForecastStatus.GUARANTEED -> if (isDark) MpeiGreenBgDark else MpeiGreenBg
        ForecastStatus.ACHIEVABLE -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.25f else 0.15f)
        ForecastStatus.IMPOSSIBLE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val badgeTextColor = when (target.status) {
        ForecastStatus.GUARANTEED -> if (isDark) MpeiGreenSuccessDark else MpeiGreenSuccess
        ForecastStatus.ACHIEVABLE -> MaterialTheme.colorScheme.primary
        ForecastStatus.IMPOSSIBLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    val statusBadgeText = when (target.status) {
        ForecastStatus.GUARANTEED -> "Гарантировано ✓"
        ForecastStatus.ACHIEVABLE -> target.requiredAverage?.let { "Ср. балл ≥ ${String.format(java.util.Locale.US, "%.1f", it)}" } ?: "Возможно"
        ForecastStatus.IMPOSSIBLE -> "Недостижимо"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.6f else 0.8f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.targetTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = target.guidance,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(badgeBg)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = statusBadgeText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = badgeTextColor
            )
        }
    }
}
