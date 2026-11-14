package ru.mpei.assistant.data.model.headman

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId

data class HeadmanGroup(val id: String, val name: String)
data class HeadmanJournal(val id: String, val name: String)
data class HeadmanSubject(val id: String, val name: String)
data class GroupStudent(
    val id: String,
    val number: String,
    val fullName: String,
    val email: String,
    val status: String
) {
    private val parts get() = fullName.trim().split(Regex("\\s+"))
    // BARS exposes only FIO; taking the last two words preserves compound surnames.
    val surname get() = if (parts.size >= 3) parts.dropLast(2).joinToString(" ") else parts.firstOrNull().orEmpty()
    val firstName get() = if (parts.size >= 3) parts[parts.size - 2] else parts.getOrElse(1) { "" }
    val patronymic get() = if (parts.size >= 3) parts.last() else ""
}

enum class StudentCopyField(val title: String, val selectedByDefault: Boolean = false) {
    NUMBER("Номер", true), FIRST_NAME("Имя", true), SURNAME("Фамилия", true),
    PATRONYMIC("Отчество"), EMAIL("Почта"), STATUS("Статус обучения");

    fun value(student: GroupStudent): String = when (this) {
        NUMBER -> student.number
        FIRST_NAME -> student.firstName
        SURNAME -> student.surname
        PATRONYMIC -> student.patronymic
        EMAIL -> student.email
        STATUS -> student.status
    }
}

fun copyStudents(students: List<GroupStudent>, fields: Set<StudentCopyField>): String =
    students.joinToString("\n") { student ->
        StudentCopyField.entries.filter { it in fields }.joinToString("\t") { it.value(student) }
    }

/** The journal day changes at 08:00 Moscow time, including across month/year boundaries. */
fun headmanDay(now: ZonedDateTime = ZonedDateTime.now(ZoneId.of("Europe/Moscow"))): LocalDate {
    val moscow = now.withZoneSameInstant(ZoneId.of("Europe/Moscow"))
    return moscow.toLocalDate().minusDays(if (moscow.hour < 8) 1 else 0)
}

data class HeadmanLesson(
    val id: String,
    val date: LocalDate,
    val number: Int,
    val title: String,
    val summary: String,
    val status: String
)

data class LessonStudent(val id: String, val name: String, val reason: String?)

data class LessonAttendance(
    val lessonId: String,
    val students: List<LessonStudent>,
    val serialized: String,
    val fields: List<Pair<String, String>>
)

enum class HeadmanReportKind(val title: String, val endpoint: String) {
    ATTENDANCE("Посещаемость", "StudyGroupAttendanceSheetBySemesterID"),
    GRADES("Успеваемость", "StudyGroupControlWeekAndCurrentScoreBySemesterID")
}

/** Header paths retain the subject and metric of every column in merged spreadsheet headers. */
data class HeadmanReportTable(
    val name: String,
    val rows: List<List<String>>,
    val dataStart: Int,
    val studentColumn: Int,
    val headers: List<String>,
    val subjects: Map<String, List<Int>>
) {
    val studentNames get() = rows.drop(dataStart).mapNotNull {
        it.getOrNull(studentColumn)?.takeIf { name ->
            name.isNotBlank() && !name.contains(Regex("(?i)^(?:итого|всего|средн|сумм).*"))
        }
    }.distinct()
}

data class HeadmanReport(val tables: List<HeadmanReportTable>)

fun hoursToPairs(raw: String): String {
    val text = raw.trim()
    if (text.isBlank() || text == "—" || text == "-") return text
    // Slash-separated values in BARS denote planned/approved/actual hours.
    val values = text.removeSuffix("ч.").removeSuffix("ч").replace("\u00a0", "").trim().split('/')
    val numbers = values.map { it.trim().replace(',', '.').toBigDecimalOrNull() ?: return raw }
    return numbers.joinToString("/") {
        it.divide(BigDecimal(2)).stripTrailingZeros().toPlainString().replace('.', ',')
    } + " п."
}
