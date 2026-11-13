package ru.mpei.assistant.data.model.schedule

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.mpei.assistant.ui.schedule.formatLessonType

class ScheduleSearchAndTypeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun rawLesson_mapsStreamCorrectly() {
        val raw = RawLesson(
            auditorium = "Д-213",
            beginLesson = "13:45",
            endLesson = "15:20",
            discipline = "Теория и практика научного исследования",
            stream = "Э-2ам,2м-26",
            group = null
        )

        val lesson = raw.toLesson(targetId = 6632L)
        assertEquals(6632L, lesson.groupId)
        assertEquals("Д-213", lesson.auditorium)
        assertEquals("Э-2ам,2м-26", lesson.stream)
    }

    @Test
    fun rawLesson_fallsBackToGroupWhenStreamIsNull() {
        val raw = RawLesson(
            auditorium = "Д-205",
            beginLesson = "15:35",
            endLesson = "17:10",
            discipline = "Теория и практика научного исследования",
            stream = null,
            group = "Э-09м-26"
        )

        val lesson = raw.toLesson(targetId = 6632L)
        assertEquals("Э-09м-26", lesson.stream)
    }

    @Test
    fun rawLesson_parsesJsonFromMpeiApi() {
        val jsonString = """
            [
              {
                "auditorium": "З-106",
                "beginLesson": "13:45",
                "endLesson": "15:20",
                "date": "2026.11.12",
                "dayOfWeek": 4,
                "dayOfWeekString": "Чт",
                "discipline": "Аналоговые измерительные устройства",
                "kindOfWork": "Лабораторная",
                "lecturer": "доц. Серов А.Н.",
                "group": "С-12-24"
              }
            ]
        """.trimIndent()

        val list = json.decodeFromString<List<RawLesson>>(jsonString)
        assertEquals(1, list.size)
        val lesson = list[0].toLesson(targetId = 20985L)
        assertEquals("З-106", lesson.auditorium)
        assertEquals("С-12-24", lesson.stream)
        assertEquals("доц. Серов А.Н.", lesson.lecturer)
    }

    @Test
    fun searchEntity_preservesTargetType() {
        val entityGroup = SearchEntity(id = 100L, label = "С-12-24", description = "Институт ИВТИ", type = "group")
        val entityPerson = SearchEntity(id = 200L, label = "доц. Лебедева Н.А.", description = "Кафедра ЭКАО", type = "person")
        val entityAud = SearchEntity(id = 300L, label = "Д-2", description = "Корпус Д", type = "auditorium")

        assertEquals("group", entityGroup.type)
        assertEquals("person", entityPerson.type)
        assertEquals("auditorium", entityAud.type)
    }

    @Test
    fun favoriteSchedules_serializesAndDeserializesCorrectly() {
        val favorites = listOf(
            SearchEntity(id = 100L, label = "С-12-24", description = "Институт ИВТИ", type = "group"),
            SearchEntity(id = 200L, label = "доц. Лебедева Н.А.", description = "Кафедра ЭКАО", type = "person"),
            SearchEntity(id = 300L, label = "Б-205", description = "Главный корпус", type = "auditorium")
        )

        val jsonString = json.encodeToString(favorites)
        val decoded = json.decodeFromString<List<SearchEntity>>(jsonString)

        assertEquals(3, decoded.size)
        assertEquals("С-12-24", decoded[0].label)
        assertEquals("group", decoded[0].type)
        assertEquals("доц. Лебедева Н.А.", decoded[1].label)
        assertEquals("person", decoded[1].type)
        assertEquals("Б-205", decoded[2].label)
        assertEquals("auditorium", decoded[2].type)
    }

    @Test
    fun favoriteSchedules_toggleLogic() {
        val list = mutableListOf(
            SearchEntity(id = 100L, label = "С-12-24", type = "group")
        )
        val newPerson = SearchEntity(id = 200L, label = "Лебедева", type = "person")

        // Add
        val index1 = list.indexOfFirst { it.id == newPerson.id && it.type == newPerson.type }
        assertEquals(-1, index1)
        list.add(0, newPerson)
        assertEquals(2, list.size)
        assertEquals(200L, list[0].id)

        // Remove
        val index2 = list.indexOfFirst { it.id == newPerson.id && it.type == newPerson.type }
        assertEquals(0, index2)
        list.removeAt(index2)
        assertEquals(1, list.size)
        assertEquals(100L, list[0].id)
    }

    @Test
    fun formatLessonType_shortensCommonTypes() {
        assertEquals("Практика", formatLessonType("Практическое занятие"))
        assertEquals("Практика", formatLessonType("Практические занятия"))
        assertEquals("Практика", formatLessonType("Практика"))
        assertEquals("Лабораторная", formatLessonType("Лабораторная работа"))
        assertEquals("Лабораторная", formatLessonType("Лабораторные работы"))
        assertEquals("Лабораторная", formatLessonType("Лабораторная"))
        assertEquals("Лекция", formatLessonType("Лекция"))
        assertEquals("Лекция", formatLessonType("Лекции"))
        assertEquals("Семинар", formatLessonType("Семинар"))
        assertEquals("Семинар", formatLessonType("Семинары"))
        assertEquals("Консультация", formatLessonType("Консультация"))
        assertEquals("Диф. зачет", formatLessonType("Дифференцированный зачет"))
        assertEquals("Диф. зачет", formatLessonType("Дифференцированный зачёт"))
        assertEquals("Зачет", formatLessonType("Зачет"))
        assertEquals("Зачет", formatLessonType("Зачёт"))
        assertEquals("Экзамен", formatLessonType("Экзамен"))
        assertEquals("Курс. проект", formatLessonType("Курсовой проект"))
        assertEquals("Курс. работа", formatLessonType("Курсовая работа"))
        assertEquals("Сам. работа", formatLessonType("Самостоятельная работа"))
        assertEquals("НИР", formatLessonType("Научно-исследовательская работа"))
        assertEquals("Произв. практика", formatLessonType("Производственная практика"))
        assertEquals("Учеб. практика", formatLessonType("Учебная практика"))
        assertEquals("Коллоквиум", formatLessonType("Коллоквиум"))
        assertEquals("Неизвестный тип", formatLessonType("Неизвестный тип"))
    }
}
