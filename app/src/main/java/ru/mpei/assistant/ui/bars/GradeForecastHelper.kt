package ru.mpei.assistant.ui.bars

import androidx.compose.runtime.Immutable
import ru.mpei.assistant.data.model.bars.ControlMeasure
import java.util.Locale
import kotlin.math.floor

@Immutable
enum class ForecastStatus {
    GUARANTEED,
    ACHIEVABLE,
    IMPOSSIBLE
}

@Immutable
data class TargetGradeForecast(
    val targetGrade: Int,
    val targetTitle: String,
    val status: ForecastStatus,
    val requiredAverage: Float?,
    val guidance: String
)

@Immutable
data class SubjectGradeForecast(
    val rawScore: Float,
    val flooredScore: Float,
    val gradedCount: Int,
    val totalCount: Int,
    val remainingCount: Int,
    val closedWeightPercent: Int,
    val totalWeightPercent: Int,
    val hasWeights: Boolean,
    val isFullyGraded: Boolean,
    val isAutomatAchieved: Boolean,
    val hasRetakes: Boolean,
    val targets: List<TargetGradeForecast>
)

object GradeForecastHelper {

    // Thresholds:
    // NRU MPEI rule: автомат при балле 4.2 и больше, десятые округляются в меньшую сторону
    const val THRESHOLD_AUTOMAT_5 = 4.20f
    const val THRESHOLD_GOOD_4 = 3.50f
    const val THRESHOLD_PASS_3 = 2.50f

    /**
     * Округление десятых в меньшую сторону (floor to 1 decimal place)
     * Например: 4.29 -> 4.2; 4.19 -> 4.1; 4.20 -> 4.2
     */
    fun floorToTenths(value: Float): Float {
        if (value.isNaN() || value.isInfinite()) return 0f
        return floor(value * 10f + 0.0001f) / 10f
    }

    fun parseGradeValue(gradeStr: String?): Float? {
        if (gradeStr.isNullOrBlank() || gradeStr == "—") return null
        val clean = gradeStr.trim().lowercase(Locale.getDefault())

        // Check if string contains slash like "2/4" or "2/5" (retake registered in BARS)
        if (clean.contains("/") || clean.contains("->") || clean.contains("→")) {
            val tokens = clean.split(Regex("""[/→\->,]""")).mapNotNull { it.trim().toFloatOrNull() }
            if (tokens.size >= 2) {
                val first = tokens[0]
                val second = tokens[1]
                if (first == 2.0f && second > 2.0f) {
                    return (2.0f + second) / 2.0f
                }
            }
        }

        clean.toFloatOrNull()?.let { return it }

        return when {
            clean.contains("незач") -> 2.0f
            clean.contains("зач") -> 5.0f
            clean == "отл" -> 5.0f
            clean == "хор" -> 4.0f
            clean == "уд" || clean == "удовл" -> 3.0f
            clean == "неуд" -> 2.0f
            else -> null
        }
    }

    /**
     * Правило МЭИ: при пересдаче после 2 балл считается как:
     * (2.0 + оценка_пересдачи) / 2.0 * вес КМ
     * Например: пересдача на 5 даёт (2 + 5)/2 = 3.5
     */
    fun computeEffectiveGrade(gradeStr: String?, isRetakeFromTwo: Boolean): Float? {
        val g = parseGradeValue(gradeStr) ?: return null
        return if (isRetakeFromTwo && g > 2.0f) {
            (2.0f + g) / 2.0f
        } else {
            g
        }
    }

    fun calculateForecast(
        measures: List<ControlMeasure>,
        controlType: String,
        retakeKmIds: Set<String> = emptySet()
    ): SubjectGradeForecast? {
        if (measures.isEmpty()) return null

        val totalCount = measures.size
        var gradedCount = 0
        var remainingCount = 0

        var sumWeights = 0
        var closedWeight = 0

        for (m in measures) {
            val w = if (m.weightPercent > 0) m.weightPercent else 0
            sumWeights += w
            val g = parseGradeValue(m.grade)
            if (g != null) {
                gradedCount++
                closedWeight += w
            } else {
                remainingCount++
            }
        }

        val hasWeights = sumWeights > 0
        val effectiveTotalWeight = if (hasWeights) sumWeights.toFloat() else totalCount.toFloat()
        val effectiveClosedWeight = if (hasWeights) closedWeight else ((gradedCount.toFloat() / totalCount.toFloat()) * 100).toInt()

        // Calculate current weighted score (on a 0.0 .. 5.0 scale)
        var rawWeightedScore = 0f
        var maxPossibleScore = 0f
        var minPassPossibleScore = 0f
        var hasRetakes = false

        for (m in measures) {
            val isRetake = retakeKmIds.contains(m.id) || (m.isDebt && m.grade != null && m.grade != "2") || (m.grade?.contains("/") == true)
            if (isRetake) hasRetakes = true
            val effectiveGrade = computeEffectiveGrade(m.grade, isRetakeFromTwo = isRetake)
            val w = if (hasWeights) m.weightPercent.toFloat() else 1f

            if (effectiveGrade != null) {
                rawWeightedScore += (effectiveGrade * w) / effectiveTotalWeight
                maxPossibleScore += (effectiveGrade * w) / effectiveTotalWeight
                minPassPossibleScore += (effectiveGrade * w) / effectiveTotalWeight
            } else {
                // Not graded yet: what is potential max/min?
                val isFutureDebtRetake = m.isDebt || retakeKmIds.contains(m.id)
                val maxGrade = if (isFutureDebtRetake) 3.5f else 5.0f
                val minPassGrade = if (isFutureDebtRetake) 2.5f else 3.0f
                maxPossibleScore += (maxGrade * w) / effectiveTotalWeight
                minPassPossibleScore += (minPassGrade * w) / effectiveTotalWeight
            }
        }

        val flooredScore = floorToTenths(rawWeightedScore)
        val isFullyGraded = remainingCount == 0

        // Remaining weight / fraction
        val remainingWeight = if (hasWeights) {
            (sumWeights - closedWeight).toFloat().coerceAtLeast(0f)
        } else {
            remainingCount.toFloat()
        }

        val flooredMax = floorToTenths(maxPossibleScore)
        val flooredMinPass = floorToTenths(minPassPossibleScore)

        val targets = mutableListOf<TargetGradeForecast>()
        val isZachet = controlType.contains("зачет", ignoreCase = true) || controlType.contains("зачёт", ignoreCase = true)

        // Target thresholds:
        // 5 (Автомат): >= 4.2
        // 4: >= 3.5
        // 3: >= 2.5
        val targetDefinitions = listOf(
            Triple(5, "«5» Автомат / Отлично (≥ 4.2)", THRESHOLD_AUTOMAT_5),
            Triple(4, "«4» Хорошо (≥ 3.5)", THRESHOLD_GOOD_4),
            Triple(3, if (isZachet) "«Зачёт» / Допуск (≥ 2.5)" else "«3» Удовлетворительно (≥ 2.5)", THRESHOLD_PASS_3)
        )

        for ((grade, title, threshold) in targetDefinitions) {
            if (isFullyGraded) {
                val finalFloored = flooredScore
                if (finalFloored >= threshold) {
                    val label = if (grade == 5) "Автомат получен! Итоговый балл: ${String.format(Locale.US, "%.1f", finalFloored)}" else "Оценка получена! Балл: ${String.format(Locale.US, "%.1f", finalFloored)}"
                    targets.add(
                        TargetGradeForecast(
                            targetGrade = grade,
                            targetTitle = title,
                            status = ForecastStatus.GUARANTEED,
                            requiredAverage = null,
                            guidance = label
                        )
                    )
                } else {
                    targets.add(
                        TargetGradeForecast(
                            targetGrade = grade,
                            targetTitle = title,
                            status = ForecastStatus.IMPOSSIBLE,
                            requiredAverage = null,
                            guidance = "Не достигнуто (итоговый балл: ${String.format(Locale.US, "%.1f", finalFloored)})"
                        )
                    )
                }
                continue
            }

            if (flooredMinPass >= threshold && gradedCount > 0) {
                targets.add(
                    TargetGradeForecast(
                        targetGrade = grade,
                        targetTitle = title,
                        status = ForecastStatus.GUARANTEED,
                        requiredAverage = null,
                        guidance = "Уже гарантировано! 🎉 Даже при тройках за оставшиеся КМ."
                    )
                )
            } else if (flooredMax < threshold) {
                targets.add(
                    TargetGradeForecast(
                        targetGrade = grade,
                        targetTitle = title,
                        status = ForecastStatus.IMPOSSIBLE,
                        requiredAverage = null,
                        guidance = "Недостижимо (максимум: ${String.format(Locale.US, "%.1f", flooredMax)})"
                    )
                )
            } else {
                // Achievable: calculate required average on remaining KMs to reach threshold
                val neededScore = threshold - rawWeightedScore
                val reqAvg = if (hasWeights) {
                    if (remainingWeight > 0f) {
                        (neededScore * effectiveTotalWeight) / remainingWeight
                    } else 5.0f
                } else {
                    if (remainingCount > 0) {
                        (threshold * totalCount - (rawWeightedScore * totalCount)) / remainingCount.toFloat()
                    } else 5.0f
                }

                val clampedReq = reqAvg.coerceIn(2.0f, 5.0f)
                val guidance = when {
                    clampedReq >= 4.75f -> "Нужны только «5» за оставшиеся $remainingCount КМ"
                    clampedReq >= 4.15f -> "Нужны «5» и «4» (ср. балл ≥ ${String.format(Locale.US, "%.1f", clampedReq)})"
                    clampedReq >= 3.40f -> "Достаточно четвёрок (ср. балл ≥ ${String.format(Locale.US, "%.1f", clampedReq)})"
                    else -> "Достаточно сдать без двоек (ср. балл ≥ ${String.format(Locale.US, "%.1f", clampedReq)})"
                }

                targets.add(
                    TargetGradeForecast(
                        targetGrade = grade,
                        targetTitle = title,
                        status = ForecastStatus.ACHIEVABLE,
                        requiredAverage = clampedReq,
                        guidance = guidance
                    )
                )
            }
        }

        return SubjectGradeForecast(
            rawScore = rawWeightedScore,
            flooredScore = flooredScore,
            gradedCount = gradedCount,
            totalCount = totalCount,
            remainingCount = remainingCount,
            closedWeightPercent = effectiveClosedWeight,
            totalWeightPercent = if (hasWeights) sumWeights else 100,
            hasWeights = hasWeights,
            isFullyGraded = isFullyGraded,
            isAutomatAchieved = flooredScore >= THRESHOLD_AUTOMAT_5 && isFullyGraded,
            hasRetakes = hasRetakes,
            targets = targets
        )
    }
}
