package ru.mpei.assistant.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BarsAttendanceParserTest {

    @Test
    fun `test cleanDisciplineName removes control types in brackets`() {
        assertEquals("Экономика", BarsAttendanceParser.cleanDisciplineName("Экономика (зачёт с оценкой)"))
        assertEquals("Операционные системы", BarsAttendanceParser.cleanDisciplineName("Операционные системы (экзамен)"))
        assertEquals("Информатика", BarsAttendanceParser.cleanDisciplineName("Информатика (диф. зачёт)"))
        assertEquals("Физика", BarsAttendanceParser.cleanDisciplineName("Физика"))
    }

    @Test
    fun `test parseDisciplineFromLessonText correctly extracts subject`() {
        val text1 = "22.12.26, 4 пара (15:35-17:10), лекция (Иванов И.И.), Экономика (зачёт с оценкой), У-01-24"
        assertEquals("Экономика", BarsAttendanceParser.parseDisciplineFromLessonText(text1))

        val text2 = "17.12.26, 2 пара (11:10-12:45), практическое занятие (Петров П.П.), ЭВМ и периферийные устройства (зачёт с оценкой), У-01-24"
        assertEquals("ЭВМ и периферийные устройства", BarsAttendanceParser.parseDisciplineFromLessonText(text2))

        val text3 = "09.11.26, 3 пара (13:45-15:20), практическое занятие (Сидоров С.С.), Цифровые измерительные приборы (экзамен), У-01-24"
        assertEquals("Цифровые измерительные приборы", BarsAttendanceParser.parseDisciplineFromLessonText(text3))

        val textTau = "22.12.26, 4 пара (15:35-17:10), лекция (Иванов И.И.), Теория автоматического управления, часть 2 (экзамен), У-01-24"
        assertEquals("Теория автоматического управления, часть 2", BarsAttendanceParser.parseDisciplineFromLessonText(textTau))

        val textTauNoControl = "01.10.26, 2 пара (11:10-12:45), практическое занятие, Теория автоматического управления, часть 2, А-04-23"
        assertEquals("Теория автоматического управления, часть 2", BarsAttendanceParser.parseDisciplineFromLessonText(textTauNoControl))

        val textWithTeacherComma = "15.11.26, 3 пара, лабораторная работа (Иванов И.И., Петров П.П.), Теория автоматического управления, часть 2, А-04-23"
        assertEquals("Теория автоматического управления, часть 2", BarsAttendanceParser.parseDisciplineFromLessonText(textWithTeacherComma))
    }

    @Test
    fun `test parseDisciplineCounts aggregates correctly`() {
        val html = """
            <table>
                <tr data-ls-id="1">
                    <td><label><input type="checkbox" /> 01.09.26, 1 пара (09:20-10:55), лекция, Экономика (зачёт), У-01-24</label></td>
                </tr>
                <tr data-ls-id="2">
                    <td><label><input type="checkbox" /> 08.09.26, 1 пара (09:20-10:55), лекция, Экономика (зачёт), У-01-24</label></td>
                </tr>
                <tr data-ls-id="3">
                    <td><label><input type="checkbox" /> 02.09.26, 2 пара (11:10-12:45), лекция, Физика (экзамен), У-01-24</label></td>
                </tr>
            </table>
        """.trimIndent()

        val counts = BarsAttendanceParser.parseDisciplineCounts(html)
        assertEquals(2, counts["Экономика"])
        assertEquals(1, counts["Физика"])
    }

    @Test
    fun `test buildGradesUrl encodes query with studentId and semesterId`() {
        val studentId = "1f061364-9447-ee11-80da-005056be401c"
        val semId = "26"
        val url = BarsAttendanceParser.buildGradesUrl(studentId, semId)
        org.junit.Assert.assertTrue(url.contains("_PartialListStudent_SemesterSheet__Mark"))
        org.junit.Assert.assertTrue(url.contains("studentID=$studentId"))
        org.junit.Assert.assertTrue(url.contains("query="))
    }

    @Test
    fun `test buildAttendanceUrl contains Lesson__Student and query parameters`() {
        val studentId = "1f061364-9447-ee11-80da-005056be401c"
        val semId = "26"
        val url = BarsAttendanceParser.buildAttendanceUrl(studentId, semId, skipType = "1", skipReason = "1")
        org.junit.Assert.assertTrue(url.contains("Lesson__Student/_PartialListStudent__Lesson__Student"))
        org.junit.Assert.assertTrue(url.contains("studentID=$studentId"))
    }

    @Test
    fun `test estimateCurrentSemesterId returns valid format`() {
        val semId = BarsAttendanceParser.estimateCurrentSemesterId()
        val num = semId.toIntOrNull()
        assertNotNull(num)
        org.junit.Assert.assertTrue(num!! >= 25)
    }

    @Test
    fun `test formatSemesterName returns correct academic year and season`() {
        assertEquals("2025/2026, осенний", BarsAttendanceParser.formatSemesterName("26"))
        assertEquals("2025/2026, весенний", BarsAttendanceParser.formatSemesterName("27"))
        assertEquals("2026/2027, осенний", BarsAttendanceParser.formatSemesterName("28"))
        assertEquals("2026/2027, весенний", BarsAttendanceParser.formatSemesterName("29"))
    }

    @Test
    fun `test extractSelectedSemesterId extracts selected or max semester`() {
        val htmlSelected = """
            <select id="ddl_Student_SemesterSheet_FilterSemester">
                <option value="26">2025/2026 осенний</option>
                <option value="27">2025/2026 весенний</option>
                <option value="28" selected="selected">2026/2027 осенний</option>
            </select>
        """.trimIndent()
        assertEquals("28", BarsAttendanceParser.extractSelectedSemesterId(htmlSelected))

        val htmlNoSelected = """
            <select name="FilterSemester">
                <option value="26">2025/2026 осенний</option>
                <option value="27">2025/2026 весенний</option>
                <option value="28">2026/2027 осенний</option>
            </select>
        """.trimIndent()
        assertEquals("28", BarsAttendanceParser.extractSelectedSemesterId(htmlNoSelected))
    }

    @Test
    fun `test extractStudentIdFromHtml extracts valid UUID from ListStudent HTML`() {
        val sampleListStudentHtml = """
            <html>
                <body>
                    <a href="/bars_web/ST/Student/Main?studentID=1f061364-9447-ee11-80da-005056be401c">Студент</a>
                </body>
            </html>
        """.trimIndent()
        val extracted = ru.mpei.assistant.data.network.BarsWebClient.extractStudentIdFromHtml(sampleListStudentHtml)
        assertEquals("1f061364-9447-ee11-80da-005056be401c", extracted)
    }
}
