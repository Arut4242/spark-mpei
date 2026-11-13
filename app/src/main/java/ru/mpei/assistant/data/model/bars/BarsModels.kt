package ru.mpei.assistant.data.model.bars

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class ControlMeasure(
    val id: String,
    val name: String,
    val weightPercent: Int,
    val period: String,
    val grade: String? = null,
    val dateReceived: String? = null,
    val isDebt: Boolean = false
)

@Immutable
@Serializable
@Entity(tableName = "bars_subjects")
data class BarsSubject(
    @PrimaryKey
    val id: String,
    val name: String,
    val teacher: String,
    val controlType: String,
    val credits: String,
    val deadline: String,
    val controlMeasuresJson: String = "[]",
    val currentControlScore: String? = null,
    val controlWeek1: String? = null,
    val controlWeek2: String? = null,
    val controlWeek3: String? = null,
    val finalGrade: String? = null,
    val semester: String = ""
)
