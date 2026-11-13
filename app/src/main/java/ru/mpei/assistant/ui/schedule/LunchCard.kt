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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mpei.assistant.data.model.schedule.Lesson
import ru.mpei.assistant.ui.theme.AppleCardShape
import ru.mpei.assistant.ui.theme.AppleOrange
import ru.mpei.assistant.ui.theme.ApplePillShape
import ru.mpei.assistant.ui.theme.applePressAnimation

const val LUNCH_BEGIN_TIME = "12:45"
const val LUNCH_END_TIME = "13:45"
const val LUNCH_BEGIN_MINUTES = 12 * 60 + 45 // 765
const val LUNCH_END_MINUTES = 13 * 60 + 45   // 825
const val LUNCH_DURATION_MINUTES = 60

val LunchOrange = AppleOrange

/**
 * Элемент списка расписания (пара или обеденный перерыв).
 */
sealed interface ScheduleScreenItem {
    data class LessonItem(val lesson: Lesson) : ScheduleScreenItem
    data object LunchItem : ScheduleScreenItem
}

/**
 * Прогресс обеденного перерыва (12:45 – 13:45).
 */
@Immutable
data class LunchProgress(
    val isCurrent: Boolean,
    val remainingMinutes: Int,
    val progress: Float
)

/**
 * Расчет прогресса обеда для текущего времени суток.
 */
fun calculateLunchProgress(
    currentMinuteOfDay: Int,
    isToday: Boolean,
    lunchBeginMin: Int = LUNCH_BEGIN_MINUTES,
    lunchEndMin: Int = LUNCH_END_MINUTES
): LunchProgress {
    val isCurrent = isToday && currentMinuteOfDay in lunchBeginMin until lunchEndMin
    val remainingMinutes = if (isCurrent) (lunchEndMin - currentMinuteOfDay) else 0
    val duration = (lunchEndMin - lunchBeginMin).coerceAtLeast(1)
    val progress = if (isCurrent) {
        ((currentMinuteOfDay - lunchBeginMin).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    return LunchProgress(
        isCurrent = isCurrent,
        remainingMinutes = remainingMinutes,
        progress = progress
    )
}

/**
 * Вставляет карточку обеда (12:45 – 13:45) в список пар в правильном хронологическом порядке.
 * Обед отображается ТОЛЬКО если в этот день есть пары как ДО обеда (до 12:45), так и ПОСЛЕ обеда (с 13:45),
 * являясь реальным перерывом между занятиями.
 */
fun insertLunchIntoSchedule(lessons: List<Lesson>): List<ScheduleScreenItem> {
    val hasLessonsBeforeLunch = lessons.any { lesson ->
        val begin = parseTimeToMinutes(lesson.beginLesson) ?: Int.MAX_VALUE
        begin < LUNCH_BEGIN_MINUTES
    }

    val hasLessonsAfterLunch = lessons.any { lesson ->
        val begin = parseTimeToMinutes(lesson.beginLesson) ?: 0
        val end = parseTimeToMinutes(lesson.endLesson) ?: 0
        begin >= LUNCH_END_MINUTES || end > LUNCH_END_MINUTES
    }

    val shouldShowLunch = hasLessonsBeforeLunch && hasLessonsAfterLunch
    if (!shouldShowLunch) {
        return lessons.map { ScheduleScreenItem.LessonItem(it) }
    }

    val result = ArrayList<ScheduleScreenItem>(lessons.size + 1)
    var lunchInserted = false

    for (lesson in lessons) {
        val beginMin = parseTimeToMinutes(lesson.beginLesson) ?: 0
        if (!lunchInserted && beginMin >= LUNCH_BEGIN_MINUTES) {
            result.add(ScheduleScreenItem.LunchItem)
            lunchInserted = true
        }
        result.add(ScheduleScreenItem.LessonItem(lesson))
    }
    if (!lunchInserted) {
        result.add(ScheduleScreenItem.LunchItem)
    }
    return result
}

/**
 * Минималистичная карточка обеденного перерыва в стиле Apple.
 */
@Composable
fun LunchCard(
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    remainingMinutes: Int = 0,
    currentProgress: Float = 0f,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
) {
    val activeAccentColor = AppleOrange

    // Minimalist Apple hairline border across all cards (no loud colored frames)
    val cardBorder = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)

    // Solid surface to completely eliminate shadow bleed-through artifacts
    val containerColor = MaterialTheme.colorScheme.surface

    // Live Activity pulsing dot
    val infiniteTransition = rememberInfiniteTransition(label = "lunchPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lunchPulseAlpha"
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
                    text = LUNCH_BEGIN_TIME,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (isCurrent) activeAccentColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = LUNCH_END_TIME,
                    fontSize = 13.sp,
                    color = if (isCurrent) activeAccentColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                if (isCurrent) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clip(ApplePillShape)
                            .background(activeAccentColor.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🍽️",
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (remainingMinutes > 0) "Обед идёт сейчас • осталось $remainingMinutes мин" else "Обед идёт сейчас",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = activeAccentColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(activeAccentColor.copy(alpha = pulseAlpha))
                        )
                    }
                }

                Text(
                    text = "Обед",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = if (isCurrent) activeAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Время отдыха и столовой",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isCurrent) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val animatedProgress by animateFloatAsState(
                        targetValue = currentProgress,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "lunchAnimProgress"
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(ApplePillShape),
                            color = activeAccentColor,
                            trackColor = activeAccentColor.copy(alpha = 0.15f),
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Начало $LUNCH_BEGIN_TIME",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "🍽️ Обед ($LUNCH_DURATION_MINUTES мин)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = activeAccentColor
                            )
                            Text(
                                text = "Конец $LUNCH_END_TIME",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
