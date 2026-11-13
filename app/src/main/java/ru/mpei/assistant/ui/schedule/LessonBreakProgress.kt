package ru.mpei.assistant.ui.schedule

import androidx.compose.runtime.Immutable

/**
 * Этап текущей пары.
 */
enum class LessonStage {
    FIRST_HALF,
    BREAK,
    SECOND_HALF
}

/**
 * Модель прогресса текущей пары с учётом 5-минутного перерыва в середине.
 */
@Immutable
data class LessonBreakProgress(
    val stage: LessonStage,
    val half1Duration: Int,
    val breakDuration: Int,
    val half2Duration: Int,
    val half1Progress: Float,
    val breakProgress: Float,
    val half2Progress: Float,
    val minutesUntilBreak: Int,
    val breakRemainingMinutes: Int,
    val lessonRemainingMinutes: Int,
    val totalProgress: Float
)

/**
 * Парсит время в формате "ЧЧ:ММ" в общее количество минут с начала суток.
 */
fun parseTimeToMinutes(timeStr: String): Int? {
    if (timeStr.isBlank()) return null
    val parts = timeStr.trim().split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

/**
 * Рассчитывает состояние и прогресс пары с 5-минутным перерывом в середине.
 * Стандартная пара МЭИ (95 мин):
 * 1-я часть: 45 мин, перерыв: 5 мин, 2-я часть: 45 мин.
 */
fun calculateLessonBreakProgress(
    beginMin: Int,
    endMin: Int,
    currentMinuteOfDay: Int,
    defaultBreakDuration: Int = 5
): LessonBreakProgress {
    val totalDuration = (endMin - beginMin).coerceAtLeast(1)
    val hasBreak = totalDuration >= 15 && defaultBreakDuration > 0
    val breakDuration = if (hasBreak) defaultBreakDuration else 0
    val half1Duration = if (hasBreak) (totalDuration - breakDuration) / 2 else totalDuration
    val half2Duration = if (hasBreak) totalDuration - half1Duration - breakDuration else 0

    val breakStartMin = beginMin + half1Duration
    val breakEndMin = breakStartMin + breakDuration

    val elapsed = (currentMinuteOfDay - beginMin).coerceIn(0, totalDuration)
    val totalProgress = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    val lessonRemainingMinutes = (endMin - currentMinuteOfDay).coerceAtLeast(0)

    val stage = when {
        !hasBreak -> LessonStage.FIRST_HALF
        currentMinuteOfDay < breakStartMin -> LessonStage.FIRST_HALF
        currentMinuteOfDay < breakEndMin -> LessonStage.BREAK
        else -> LessonStage.SECOND_HALF
    }

    val half1Progress = if (half1Duration > 0) {
        ((currentMinuteOfDay - beginMin).toFloat() / half1Duration.toFloat()).coerceIn(0f, 1f)
    } else 1f

    val breakProgress = if (hasBreak && breakDuration > 0) {
        if (currentMinuteOfDay < breakStartMin) 0f
        else ((currentMinuteOfDay - breakStartMin).toFloat() / breakDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val half2Progress = if (hasBreak && half2Duration > 0) {
        if (currentMinuteOfDay < breakEndMin) 0f
        else ((currentMinuteOfDay - breakEndMin).toFloat() / half2Duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val minutesUntilBreak = if (hasBreak) (breakStartMin - currentMinuteOfDay).coerceAtLeast(0) else 0
    val breakRemainingMinutes = if (hasBreak) (breakEndMin - currentMinuteOfDay).coerceAtLeast(0) else 0

    return LessonBreakProgress(
        stage = stage,
        half1Duration = half1Duration,
        breakDuration = breakDuration,
        half2Duration = half2Duration,
        half1Progress = half1Progress,
        breakProgress = breakProgress,
        half2Progress = half2Progress,
        minutesUntilBreak = minutesUntilBreak,
        breakRemainingMinutes = breakRemainingMinutes,
        lessonRemainingMinutes = lessonRemainingMinutes,
        totalProgress = totalProgress
    )
}
