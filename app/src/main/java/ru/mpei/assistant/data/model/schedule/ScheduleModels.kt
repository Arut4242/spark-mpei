package ru.mpei.assistant.data.model.schedule

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SearchEntity(
    val id: Long = 0,
    val label: String = "",
    val description: String? = null,
    val type: String? = null
)

@Immutable
@Serializable
data class RawLesson(
    val auditorium: String? = null,
    val beginLesson: String = "",
    val endLesson: String = "",
    val date: String = "",
    val dayOfWeek: Int = 1,
    val dayOfWeekString: String = "",
    val discipline: String = "",
    val kindOfWork: String = "",
    val lecturer: String? = null,
    val stream: String? = null,
    val group: String? = null
) {
    fun toLesson(targetId: Long): Lesson {
        val effectiveStream = when {
            !stream.isNullOrBlank() -> stream
            !group.isNullOrBlank() -> group
            else -> null
        }
        return Lesson(
            groupId = targetId,
            auditorium = auditorium,
            beginLesson = beginLesson,
            endLesson = endLesson,
            date = date,
            dayOfWeek = dayOfWeek,
            dayOfWeekString = dayOfWeekString,
            discipline = discipline,
            kindOfWork = kindOfWork,
            lecturer = lecturer,
            stream = effectiveStream
        )
    }
}

@Immutable
@Serializable
@Entity(tableName = "schedule_lessons")
data class Lesson(
    @PrimaryKey(autoGenerate = true)
    val dbId: Long = 0,
    val groupId: Long = 0L,
    val auditorium: String? = null,
    val beginLesson: String = "",
    val endLesson: String = "",
    val date: String = "",
    val dayOfWeek: Int = 1,
    val dayOfWeekString: String = "",
    val discipline: String = "",
    val kindOfWork: String = "",
    val lecturer: String? = null,
    val stream: String? = null
)
