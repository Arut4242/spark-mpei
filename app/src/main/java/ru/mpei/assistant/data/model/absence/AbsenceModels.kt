package ru.mpei.assistant.data.model.absence

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.floor

@Immutable
@Entity(tableName = "subject_absences")
data class SubjectAbsence(
    @PrimaryKey
    val subjectName: String,
    val unexcusedPairs: Int = 0,
    val excusedPairs: Int = 0,
    val totalSemesterPairs: Int = 16,
    val controlType: String = ""
) {
    val totalAbsencePairs: Int
        get() = unexcusedPairs + excusedPairs

    /**
     * Является ли предмет зачётом по совокупности (нет экзамена, оценка формируется накопительно).
     */
    val isCumulativeCredit: Boolean
        get() = controlType.contains("совокупност", ignoreCase = true) ||
                subjectName.contains("совокупност", ignoreCase = true)

    /**
     * Процент пропусков без уважительной причины (влияет на автомат в МЭИ).
     */
    val unexcusedPercent: Float
        get() = if (totalSemesterPairs > 0) {
            (unexcusedPairs.toFloat() / totalSemesterPairs.toFloat()) * 100f
        } else 0f

    /**
     * Максимально допустимое целое число пропусков пар без уважительной причины,
     * чтобы не превысить 6.25% (условие автомата в НИУ «МЭИ»).
     * Например: при 16 парах -> 1 пара (6.25%); при 32 парах -> 2 пары (6.25%).
     */
    val maxAllowedUnexcusedPairs: Int
        get() = floor(totalSemesterPairs * 0.0625f + 0.0001f).toInt()

    /**
     * Сколько ещё пар можно пропустить без уважительной причины без потери автомата.
     */
    val remainingAllowedPairs: Int
        get() = (maxAllowedUnexcusedPairs - unexcusedPairs).coerceAtLeast(0)

    /**
     * Соблюдено ли условие по пропускам для автомата (пропущено <= 6.25%).
     */
    val isAutomatAllowedByAbsences: Boolean
        get() = unexcusedPairs <= maxAllowedUnexcusedPairs

    /**
     * Пограничный случай для дисциплин с 24 парами в семестре:
     * 6.25% от 24 = 1.5 пары.
     * При 2 пропусках (8.33%) формально превышен документальный критерий,
     * однако в БАРСе возможно согласование автомата.
     */
    val isBorderline24PairsCase: Boolean
        get() = totalSemesterPairs == 24 && unexcusedPairs == 2 && !isCumulativeCredit
}
