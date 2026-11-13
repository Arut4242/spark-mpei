package ru.mpei.assistant.data.parser

import org.jsoup.Jsoup
import java.net.URLEncoder

object BarsAttendanceParser {

    data class AttendanceMeta(
        val studentId: String,
        val partialUrl: String,
        val semesterId: String
    ) {
        fun buildQueryUrl(
            skipType: String = "*",
            skipReason: String = "*",
            pageSize: Int = 500
        ): String {
            val queryJson = """{"ID":"$studentId","SortOrder":"CustomSort1 desc","Page":"1","PageSize":"$pageSize","SearchText":"","FilterStartDate":"","FilterEndDate":"","FilterSemester":{"Value":"$semesterId"},"FilterLessonSkipType":{"Value":"$skipType"},"FilterLessonSkipReason":{"Value":"$skipReason"}}"""
            val base = if (partialUrl.startsWith("http")) partialUrl else "https://bars.mpei.ru$partialUrl"
            val separator = if (base.contains("?")) "&" else "?"
            return "$base${separator}query=${URLEncoder.encode(queryJson, "UTF-8")}"
        }
    }

    data class BarsSemesterOption(
        val id: String,
        val name: String,
        val isSelected: Boolean = false
    )

    /**
     * Вычисляет ID текущего семестра в системе БАРС.
     * Базовые значения:
     * 25 - Весна 2024/2025
     * 26 - Осень 2025/2026
     * 27 - Весна 2025/2026
     * 28 - Осень 2026/2027
     * 29 - Весна 2026/2027
     */
    fun estimateCurrentSemesterId(): String {
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1 // 1..12
        val baseYear = 2025
        val yearDiff = year - baseYear
        val semesterIndex = if (month >= 9) {
            yearDiff * 2 // Осенний семестр
        } else if (month <= 1) {
            (yearDiff - 1) * 2 // Январь — окончание осенней сессии
        } else {
            (yearDiff - 1) * 2 + 1 // Весенний семестр
        }
        return (26 + semesterIndex).coerceAtLeast(25).toString()
    }

    /**
     * Формирует прямой AJAX URL для получения таблицы оценок (семестровой ведомости).
     */
    fun buildGradesUrl(studentId: String, semesterId: String): String {
        val queryJson = """{"ID":"$studentId","FilterSemester":{"Value":"$semesterId"}}"""
        val encoded = URLEncoder.encode(queryJson, "UTF-8")
        return "https://bars.mpei.ru/bars_web/ST_Study/Student_SemesterSheet/_PartialListStudent_SemesterSheet__Mark?studentID=$studentId&query=$encoded"
    }

    /**
     * Формирует прямой AJAX URL для получения таблицы посещаемости.
     */
    fun buildAttendanceUrl(
        studentId: String,
        semesterId: String,
        skipType: String = "*",
        skipReason: String = "*",
        pageSize: Int = 500
    ): String {
        val queryJson = """{"ID":"$studentId","SortOrder":"CustomSort1 desc","Page":"1","PageSize":"$pageSize","SearchText":"","FilterStartDate":"","FilterEndDate":"","FilterSemester":{"Value":"$semesterId"},"FilterLessonSkipType":{"Value":"$skipType"},"FilterLessonSkipReason":{"Value":"$skipReason"}}"""
        val encoded = URLEncoder.encode(queryJson, "UTF-8")
        return "https://bars.mpei.ru/bars_web/ST_Study/Lesson__Student/_PartialListStudent__Lesson__Student?studentID=$studentId&query=$encoded"
    }

    /**
     * Извлекает метаданные страницы «Посещение занятий» (ID студента, URL AJAX-запроса, выбранный семестр).
     */
    fun extractMeta(html: String): AttendanceMeta? {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("tbl__PartialListStudent__Lesson__Student") ?: return null
        val studentId = table.attr("data-q-id").trim()
        val partialUrl = table.attr("data-q-href").trim()
        val semesterEl = doc.select("#ddl_LessonStudent_FilterSemester option[selected]").firstOrNull()
        val semesterId = semesterEl?.attr("value")?.trim() ?: "*"

        if (studentId.isEmpty() || partialUrl.isEmpty()) return null

        return AttendanceMeta(studentId, partialUrl, semesterId)
    }

    /**
     * Очищает название дисциплины от формы контроля в скобках.
     * Например: "Экономика (зачёт с оценкой)" -> "Экономика",
     *           "Операционные системы (экзамен)" -> "Операционные системы".
     */
    fun cleanDisciplineName(rawDiscipline: String): String {
        return rawDiscipline
            .replace(Regex("""\s*\((?:зач[её]т(?:\s+с\s+оценкой)?|экзамен|диф\.?\s*зач[её]т|защита(?:\s+КП\/КР)?)\)\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\([^)]*\)\s*$"""), "")
            .trim()
    }

    /**
     * Извлекает дисциплину из текстовой строки занятия в таблице БАРС.
     * Формат строки в БАРС:
     * "22.12.26, 4 пара (15:35-17:10), лекция (Иванов И.И.), Экономика (зачёт с оценкой), У-01-24"
     * или с запятой в названии (например, составные курсы):
     * "22.12.26, 4 пара (15:35-17:10), лекция (Иванов И.И.), Теория автоматического управления, часть 2 (экзамен), У-01-24"
     */
    fun parseDisciplineFromLessonText(lessonText: String): String? {
        val parts = splitTopLevelCommas(lessonText)
        if (parts.size < 4) {
            val fallback = parts.lastOrNull() ?: lessonText
            return cleanDisciplineName(fallback).ifBlank { null }
        }

        // В строке БАРС первые 3 элемента фиксированы:
        // parts[0] = Дата (например, 22.12.26)
        // parts[1] = Номер и время пары (например, 4 пара (15:35-17:10))
        // parts[2] = Тип занятия и преподаватель (например, лекция (Иванов И.И.))
        // Последний элемент — группа (например, У-01-24 или А-04-23).
        // Все элементы между типом занятия и группой образуют полное название дисциплины (включая части через запятую).
        val lastPart = parts.last()
        val isLastPartGroup = lastPart.matches(Regex("""^[А-Яа-яA-Za-z0-9]{1,8}-[А-Яа-яA-Za-z0-9]{1,8}-\d{2}[а-яА-Яa-zA-Z]?$""")) ||
                (lastPart.contains("-") && lastPart.any { it.isDigit() } && !lastPart.contains(" "))

        val startIndex = 3
        val endIndex = if (isLastPartGroup && parts.size > 3) parts.size - 1 else parts.size

        val rawDisc = if (startIndex < endIndex) {
            parts.subList(startIndex, endIndex).joinToString(", ")
        } else {
            parts[startIndex.coerceAtMost(parts.size - 1)]
        }

        val clean = cleanDisciplineName(rawDisc)
        return clean.ifBlank { null }
    }

    private fun splitTopLevelCommas(text: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0
        for (ch in text) {
            when (ch) {
                '(' -> {
                    depth++
                    current.append(ch)
                }
                ')' -> {
                    if (depth > 0) depth--
                    current.append(ch)
                }
                ',' -> {
                    if (depth == 0) {
                        result.add(current.toString().trim())
                        current = StringBuilder()
                    } else {
                        current.append(ch)
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }
        return result
    }

    /**
     * Считает количество занятий по каждой дисциплине в HTML ответе AJAX таблицы БАРС.
     */
    fun parseDisciplineCounts(html: String): Map<String, Int> {
        val doc = Jsoup.parse(html)
        val trs = doc.select("tr[data-ls-id]")
        val counts = mutableMapOf<String, Int>()

        for (tr in trs) {
            val label = tr.selectFirst("label") ?: continue
            val text = label.text().trim()
            val disc = parseDisciplineFromLessonText(text)
            if (!disc.isNullOrBlank()) {
                counts[disc] = (counts[disc] ?: 0) + 1
            }
        }

        return counts
    }

    /**
     * Извлекает выбранный семестр из любого HTML-ответа страниц БАРС.
     */
    fun extractSelectedSemesterId(html: String): String? {
        if (html.isBlank()) return null
        val doc = Jsoup.parse(html)
        val selectedOpt = doc.select("select[id*='FilterSemester'] option[selected], select[name*='FilterSemester'] option[selected]").firstOrNull()
        val value = selectedOpt?.attr("value")?.trim()
        if (!value.isNullOrBlank() && value != "*") return value

        // Если атрибут selected не указан явно, берем семестр с максимальным номером
        val options = doc.select("select[id*='FilterSemester'] option, select[name*='FilterSemester'] option")
        val maxSem = options.mapNotNull { it.attr("value").trim().toIntOrNull() }.maxOrNull()
        return maxSem?.toString()
    }

    /**
     * Извлекает список всех доступных семестров студента из селектора БАРС.
     */
    fun extractSemesters(html: String): List<BarsSemesterOption> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        val select = doc.select("select[id*='FilterSemester'], select[name*='FilterSemester']").firstOrNull()
            ?: return emptyList()
        return select.select("option").mapNotNull { opt ->
            val id = opt.attr("value").trim()
            val name = opt.text().trim()
            if (id.isNotEmpty() && id != "*") {
                BarsSemesterOption(
                    id = id,
                    name = name,
                    isSelected = opt.hasAttr("selected")
                )
            } else null
        }
    }

    /**
     * Форматирует человекочитаемое название семестра по его ID в БАРС.
     * База: 26 -> 2025/2026 осенний, 27 -> 2025/2026 весенний, 28 -> 2026/2027 осенний
     */
    fun formatSemesterName(semesterId: String): String {
        val semNum = semesterId.toIntOrNull() ?: return "Текущий семестр"
        val yearStart = 2025 + (semNum - 26) / 2
        val isSpring = (semNum % 2 != 0)
        val type = if (isSpring) "весенний" else "осенний"
        val yearEnd = yearStart + 1
        return "$yearStart/$yearEnd, $type"
    }
}
