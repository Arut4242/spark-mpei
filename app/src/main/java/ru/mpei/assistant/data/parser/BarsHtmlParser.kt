package ru.mpei.assistant.data.parser

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import ru.mpei.assistant.data.model.bars.BarsSubject
import ru.mpei.assistant.data.model.bars.ControlMeasure
import java.util.UUID

object BarsHtmlParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseSubjects(html: String, semesterName: String = "Текущий семестр"): List<BarsSubject> {
        val doc: Document = Jsoup.parse(html)
        val subjectsList = mutableListOf<BarsSubject>()

        // Official BARS MPEI layout:
        // Headers have [data-bs-target="#s_ss_N"] and link directly to div#s_ss_N
        val headerElements = doc.select("[data-bs-target^=#s_ss_]")

        if (headerElements.isNotEmpty()) {
            for ((index, headerEl) in headerElements.withIndex()) {
                val targetId = headerEl.attr("data-bs-target").removePrefix("#")
                val rawText = headerEl.text().replace(Regex("\\s+"), " ").trim()
                val parsedHeader = parseHeaderDetails(rawText, index + 1)

                val collapseDiv = doc.getElementById(targetId)
                val table = collapseDiv?.selectFirst("table")

                val kmResult = if (table != null) {
                    parseKmTable(table)
                } else {
                    KmTableResult(emptyList(), null, null, null, null, null)
                }

                val subject = BarsSubject(
                    id = "${parsedHeader.name}_${index}_${semesterName}",
                    name = parsedHeader.name,
                    teacher = parsedHeader.teacher,
                    controlType = parsedHeader.controlType,
                    credits = parsedHeader.credits,
                    deadline = parsedHeader.deadline,
                    controlMeasuresJson = json.encodeToString(kmResult.measures),
                    currentControlScore = kmResult.currentScore,
                    controlWeek1 = kmResult.week1,
                    controlWeek2 = kmResult.week2,
                    controlWeek3 = kmResult.week3,
                    finalGrade = kmResult.finalGrade,
                    semester = semesterName
                )
                subjectsList.add(subject)
            }
        } else {
            // Fallback for different BARS layouts
            val tables = doc.select("table")
            var idx = 0
            for (table in tables) {
                val tableText = table.text()
                if (!tableText.contains("контрольного мероприятия", ignoreCase = true) &&
                    !tableText.contains("Текущий контроль", ignoreCase = true)
                ) {
                    continue
                }
                idx++
                val kmResult = parseKmTable(table)
                val subject = BarsSubject(
                    id = "disc_${idx}_$semesterName",
                    name = "Дисциплина $idx",
                    teacher = "",
                    controlType = "",
                    credits = "",
                    deadline = "",
                    controlMeasuresJson = json.encodeToString(kmResult.measures),
                    currentControlScore = kmResult.currentScore,
                    controlWeek1 = kmResult.week1,
                    controlWeek2 = kmResult.week2,
                    controlWeek3 = kmResult.week3,
                    finalGrade = kmResult.finalGrade,
                    semester = semesterName
                )
                subjectsList.add(subject)
            }
        }

        return subjectsList
    }

    private data class ParsedHeader(
        val name: String = "",
        val teacher: String = "",
        val controlType: String = "",
        val credits: String = "",
        val deadline: String = ""
    )

    private fun parseHeaderDetails(rawText: String, fallbackIndex: Int): ParsedHeader {
        if (rawText.isBlank()) {
            return ParsedHeader(name = "Дисциплина $fallbackIndex")
        }

        // Clean out button labels
        val text = rawText
            .replace("Развернуть всё", "")
            .replace("Развернуть", "")
            .replace("Скрыть", "")
            .replace("получить оценку ПА", "")
            .replace("график приёма КМ", "")
            .replace("график приема КМ", "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val parts = text.split(",").map { it.trim() }

        val isPartContinuation = parts.getOrNull(1)?.matches(
            Regex("""^(?:часть|ч\.?|модуль|раздел|семестр)\s*\d+.*""", RegexOption.IGNORE_CASE)
        ) == true

        val name = if (isPartContinuation && parts.size >= 2) {
            "${parts[0]}, ${parts[1]}"
        } else {
            parts.getOrNull(0) ?: text
        }
        val teacherIndex = if (isPartContinuation) 2 else 1
        val teacher = parts.getOrNull(teacherIndex) ?: ""

        val controlType = parts.firstOrNull {
            it.contains("экзамен", ignoreCase = true) ||
            it.contains("зачёт", ignoreCase = true) ||
            it.contains("зачет", ignoreCase = true) ||
            it.contains("защита", ignoreCase = true)
        } ?: parts.getOrNull(teacherIndex + 1) ?: ""

        // Extract credits (e.g. "4 ЗЕ" or "4ЗЕ")
        val zeMatch = Regex("""(\d+\s*ЗЕ)""", RegexOption.IGNORE_CASE).find(text)
        val credits = zeMatch?.groupValues?.get(1)?.trim() ?: ""

        // Extract deadline (e.g. "сдать до 23.12.2026")
        val deadlineMatch = Regex("""(сдать до\s*\d{2}\.\d{2}\.\d{4})""", RegexOption.IGNORE_CASE).find(text)
        val deadline = deadlineMatch?.groupValues?.get(1)?.trim() ?: ""

        return ParsedHeader(
            name = name.removeSuffix(".").trim(),
            teacher = if (teacher == controlType || teacher.contains("ЗЕ")) "" else teacher,
            controlType = controlType,
            credits = credits,
            deadline = deadline
        )
    }

    private data class KmTableResult(
        val measures: List<ControlMeasure>,
        val currentScore: String?,
        val week1: String?,
        val week2: String?,
        val week3: String?,
        val finalGrade: String?
    )

    private fun parseKmTable(table: Element): KmTableResult {
        val measures = mutableListOf<ControlMeasure>()
        var currentScore: String? = null
        var week1: String? = null
        var week2: String? = null
        var week3: String? = null
        var finalGrade: String? = null

        val rows = table.select("tr")
        for (row in rows) {
            val cols = row.select("td, th")
            if (cols.isEmpty()) continue

            val firstColText = cols[0].text().trim()

            when {
                firstColText.contains("Балл текущего контроля", ignoreCase = true) -> {
                    currentScore = cols.lastOrNull()?.text()?.trim()
                }
                firstColText.contains("Контрольная неделя №1", ignoreCase = true) -> {
                    week1 = cols.lastOrNull()?.text()?.trim()
                }
                firstColText.contains("Контрольная неделя №2", ignoreCase = true) -> {
                    week2 = cols.lastOrNull()?.text()?.trim()
                }
                firstColText.contains("Контрольная неделя №3", ignoreCase = true) -> {
                    week3 = cols.lastOrNull()?.text()?.trim()
                }
                firstColText.contains("Итоговая оценка", ignoreCase = true) -> {
                    finalGrade = cols.lastOrNull()?.text()?.trim()
                }
                // Check if row is a Control Measure (e.g., "1. Контрольное задание 1")
                firstColText.matches(Regex("""^\d+\..*""")) -> {
                    val name = firstColText.replace("(критерии)", "").trim()
                    val weightStr = cols.getOrNull(1)?.text()?.replace("%", "")?.trim() ?: "0"
                    val weight = weightStr.toIntOrNull() ?: 0
                    val period = cols.getOrNull(2)?.text()?.trim() ?: ""

                    val gradeCol = cols.getOrNull(3)
                    val gradeText = gradeCol?.text()?.trim() ?: ""

                    var gradeVal: String? = null
                    var dateVal: String? = null

                    if (gradeText.isNotBlank()) {
                        val match = Regex("""^(\d+)\s*(?:\((.*?)\))?""").find(gradeText)
                        if (match != null) {
                            gradeVal = match.groupValues.getOrNull(1)
                            dateVal = match.groupValues.getOrNull(2)
                        } else {
                            gradeVal = gradeText
                        }
                    }

                    val isDebt = gradeVal == "2" ||
                            row.attr("class").contains("danger", ignoreCase = true) ||
                            gradeCol?.attr("style")?.contains("red", ignoreCase = true) == true ||
                            row.attr("style").contains("#f", ignoreCase = true)

                    measures.add(
                        ControlMeasure(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            weightPercent = weight,
                            period = period,
                            grade = gradeVal,
                            dateReceived = dateVal,
                            isDebt = isDebt
                        )
                    )
                }
            }
        }

        return KmTableResult(measures, currentScore, week1, week2, week3, finalGrade)
    }
}
