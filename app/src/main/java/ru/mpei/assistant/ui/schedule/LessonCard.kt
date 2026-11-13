package ru.mpei.assistant.ui.schedule

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mpei.assistant.data.model.schedule.Lesson
import ru.mpei.assistant.ui.theme.AppleBlue
import ru.mpei.assistant.ui.theme.AppleCardShape
import ru.mpei.assistant.ui.theme.AppleGreen
import ru.mpei.assistant.ui.theme.AppleOrange
import ru.mpei.assistant.ui.theme.ApplePillShape
import ru.mpei.assistant.ui.theme.applePressAnimation

@Immutable
private data class BadgeColors(
    val textColor: Color,
    val backgroundColor: Color
)

// Pastel Apple tint badges (~12% alpha container)
private val LectureColors = BadgeColors(AppleBlue, AppleBlue.copy(alpha = 0.12f))
private val LabColors = BadgeColors(AppleGreen, AppleGreen.copy(alpha = 0.12f))
private val PracticeColors = BadgeColors(AppleOrange, AppleOrange.copy(alpha = 0.12f))

private val StaticBadgeColorMap = mapOf(
    "Лек" to LectureColors,
    "Лекция" to LectureColors,
    "Лекции" to LectureColors,
    "Лаб" to LabColors,
    "Лабораторная" to LabColors,
    "Лабораторная работа" to LabColors,
    "Лабораторные работы" to LabColors,
    "Сем" to PracticeColors,
    "Семинар" to PracticeColors,
    "Семинары" to PracticeColors,
    "Прак" to PracticeColors,
    "Практика" to PracticeColors,
    "Практическое занятие" to PracticeColors,
    "Практические занятия" to PracticeColors
)

private fun getBadgeColors(kindOfWork: String, primaryColor: Color): BadgeColors {
    StaticBadgeColorMap[kindOfWork]?.let { return it }
    return when {
        kindOfWork.contains("Лек", ignoreCase = true) -> LectureColors
        kindOfWork.contains("Лаб", ignoreCase = true) -> LabColors
        kindOfWork.contains("Сем", ignoreCase = true) ||
                kindOfWork.contains("Прак", ignoreCase = true) -> PracticeColors
        else -> BadgeColors(primaryColor, primaryColor.copy(alpha = 0.12f))
    }
}

fun formatLessonType(kindOfWork: String): String {
    val trimmed = kindOfWork.trim()
    return when {
        trimmed.contains("Научно-исслед", ignoreCase = true) || trimmed.contains("НИР", ignoreCase = true) -> "НИР"
        trimmed.contains("Производствен", ignoreCase = true) -> "Произв. практика"
        trimmed.contains("Учебн", ignoreCase = true) && trimmed.contains("практ", ignoreCase = true) -> "Учеб. практика"
        trimmed.contains("Прак", ignoreCase = true) -> "Практика"
        trimmed.contains("Лаб", ignoreCase = true) -> "Лабораторная"
        trimmed.contains("Лек", ignoreCase = true) -> "Лекция"
        trimmed.contains("Сем", ignoreCase = true) -> "Семинар"
        trimmed.contains("Консульт", ignoreCase = true) -> "Консультация"
        trimmed.contains("Дифф", ignoreCase = true) -> "Диф. зачет"
        trimmed.contains("Зачет", ignoreCase = true) || trimmed.contains("Зачёт", ignoreCase = true) -> "Зачет"
        trimmed.contains("Экзамен", ignoreCase = true) -> "Экзамен"
        trimmed.contains("Курсов", ignoreCase = true) && trimmed.contains("проект", ignoreCase = true) -> "Курс. проект"
        trimmed.contains("Курсов", ignoreCase = true) && trimmed.contains("работ", ignoreCase = true) -> "Курс. работа"
        trimmed.contains("Самостоят", ignoreCase = true) -> "Сам. работа"
        trimmed.contains("Коллоквиум", ignoreCase = true) -> "Коллоквиум"
        else -> trimmed
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonCard(
    lesson: Lesson,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    remainingMinutes: Int = 0,
    currentProgress: Float = 0f,
    breakProgress: LessonBreakProgress? = null,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val isBreak = isCurrent && breakProgress?.stage == LessonStage.BREAK
    val activeAccentColor = if (isBreak) AppleOrange else primaryColor

    val badgeColors = remember(lesson.kindOfWork, primaryColor) {
        getBadgeColors(lesson.kindOfWork, primaryColor)
    }

    // Minimalist Apple hairline border across all cards (no loud colored frames)
    val cardBorder = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)

    // Solid surface to completely eliminate shadow bleed-through artifacts
    val containerColor = MaterialTheme.colorScheme.surface

    // Live Activity breathing dot animation (iOS Dynamic Island style)
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .applePressAnimation(),
        shape = AppleCardShape,
        border = cardBorder,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (isCurrent) 14.dp else 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isCurrent) {
                // Signature Apple vertical accent pill on leading edge
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .width(4.dp)
                        .height(46.dp)
                        .clip(ApplePillShape)
                        .background(activeAccentColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            // Time Column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(62.dp)
            ) {
                Text(
                    text = lesson.beginLesson,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (isCurrent) activeAccentColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = lesson.endLesson,
                    fontSize = 13.sp,
                    color = if (isCurrent) activeAccentColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                if (isCurrent) {
                    val badgeBg = activeAccentColor.copy(alpha = 0.12f)
                    val badgeFg = activeAccentColor
                    val badgeText = when {
                        breakProgress == null -> {
                            if (remainingMinutes > 0) "Идёт сейчас • осталось ${remainingMinutes} мин" else "Идёт сейчас"
                        }
                        breakProgress.stage == LessonStage.BREAK -> {
                            val rem = breakProgress.breakRemainingMinutes
                            if (rem > 0) "Перерыв 5 минут • осталось $rem мин" else "Перерыв 5 минут"
                        }
                        breakProgress.stage == LessonStage.FIRST_HALF -> {
                            val rem = breakProgress.minutesUntilBreak
                            if (rem > 0) "1-я половина • до перерыва $rem мин" else "1-я половина"
                        }
                        else -> {
                            val rem = breakProgress.lessonRemainingMinutes
                            if (rem > 0) "2-я половина • осталось $rem мин" else "2-я половина"
                        }
                    }

                    // iOS Live Activity Capsule Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clip(ApplePillShape)
                            .background(badgeBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        if (isBreak) {
                            Text(
                                text = "☕",
                                fontSize = 11.sp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(badgeFg.copy(alpha = pulseAlpha))
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeFg
                        )
                    }
                }

                Text(
                    text = lesson.discipline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                val formattedType = formatLessonType(lesson.kindOfWork)
                val hasType = formattedType.isNotBlank()
                val hasAuditorium = !lesson.auditorium.isNullOrBlank()

                if (hasType || hasAuditorium) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (hasType) {
                            Box(
                                modifier = Modifier
                                    .clip(ApplePillShape)
                                    .background(badgeColors.backgroundColor)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = formattedType,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = badgeColors.textColor
                                )
                            }
                        }

                        if (hasAuditorium) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (isCurrent) activeAccentColor else primaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Ауд. ${lesson.auditorium}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (!lesson.lecturer.isNullOrBlank()) {
                    val lecturerName = lesson.lecturer
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = lecturerName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!lesson.stream.isNullOrBlank()) {
                    val streamName = lesson.stream
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = streamName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isCurrent) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (breakProgress != null && breakProgress.breakDuration > 0) {
                        // Smooth Spring Physics for progress changes
                        val animatedP1 by animateFloatAsState(
                            targetValue = breakProgress.half1Progress,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "animP1"
                        )
                        val animatedBreak by animateFloatAsState(
                            targetValue = breakProgress.breakProgress,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "animBreak"
                        )
                        val animatedP2 by animateFloatAsState(
                            targetValue = breakProgress.half2Progress,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "animP2"
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { animatedP1 },
                                    modifier = Modifier
                                        .weight(breakProgress.half1Duration.toFloat())
                                        .fillMaxHeight()
                                        .clip(ApplePillShape),
                                    color = primaryColor,
                                    trackColor = primaryColor.copy(alpha = 0.15f),
                                    strokeCap = StrokeCap.Round,
                                    gapSize = 0.dp,
                                    drawStopIndicator = {}
                                )
                                LinearProgressIndicator(
                                    progress = { animatedBreak },
                                    modifier = Modifier
                                        .weight(breakProgress.breakDuration.toFloat().coerceAtLeast(8f))
                                        .fillMaxHeight()
                                        .clip(ApplePillShape),
                                    color = AppleOrange,
                                    trackColor = AppleOrange.copy(alpha = 0.2f),
                                    strokeCap = StrokeCap.Round,
                                    gapSize = 0.dp,
                                    drawStopIndicator = {}
                                )
                                LinearProgressIndicator(
                                    progress = { animatedP2 },
                                    modifier = Modifier
                                        .weight(breakProgress.half2Duration.toFloat())
                                        .fillMaxHeight()
                                        .clip(ApplePillShape),
                                    color = primaryColor,
                                    trackColor = primaryColor.copy(alpha = 0.15f),
                                    strokeCap = StrokeCap.Round,
                                    gapSize = 0.dp,
                                    drawStopIndicator = {}
                                )
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "1-я часть (${breakProgress.half1Duration}м)",
                                    fontSize = 10.sp,
                                    fontWeight = if (breakProgress.stage == LessonStage.FIRST_HALF) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (breakProgress.stage == LessonStage.FIRST_HALF) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "☕ Перерыв (${breakProgress.breakDuration}м)",
                                    fontSize = 10.sp,
                                    fontWeight = if (breakProgress.stage == LessonStage.BREAK) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (breakProgress.stage == LessonStage.BREAK) AppleOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "2-я часть (${breakProgress.half2Duration}м)",
                                    fontSize = 10.sp,
                                    fontWeight = if (breakProgress.stage == LessonStage.SECOND_HALF) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (breakProgress.stage == LessonStage.SECOND_HALF) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        val animatedCurrent by animateFloatAsState(
                            targetValue = currentProgress,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "animCurrent"
                        )
                        LinearProgressIndicator(
                            progress = { animatedCurrent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(ApplePillShape),
                            color = primaryColor,
                            trackColor = primaryColor.copy(alpha = 0.15f),
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                    }
                }
            }
        }
    }
}
