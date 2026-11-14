package ru.mpei.assistant.data.parser

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import ru.mpei.assistant.data.model.headman.*
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Reads the downloaded file locally; filtering never asks BARS to generate another report. */
object HeadmanReportParser {
    private const val MAX_ROWS = 10000
    private const val MAX_COLUMNS = 512
    private const val MAX_CELLS = 500000

    fun parse(bytes: ByteArray, students: List<GroupStudent>): HeadmanReport {
        val tables = when {
            bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() -> xlsx(bytes)
            bytes.size >= 2 && bytes[0] == 0xd0.toByte() && bytes[1] == 0xcf.toByte() -> xls(bytes)
            else -> html(bytes.toString(Charsets.UTF_8))
        }.filter { (_, rows) -> rows.any { row -> row.any { it.isNotBlank() } } }
        check(tables.isNotEmpty()) { "БАРС не вернул таблицу отчёта" }
        // Some workbooks include cover/legend sheets without student rows.
        val described = tables.mapNotNull { (name, rows) ->
            runCatching { describe(name, rows, students) }.getOrNull()
        }
        check(described.isNotEmpty()) { "В отчёте не удалось определить строки студентов" }
        return HeadmanReport(described)
    }

    private fun xls(bytes: ByteArray): List<Pair<String, List<List<String>>>> {
        val book = jxl.Workbook.getWorkbook(ByteArrayInputStream(bytes))
        return try {
            (0 until book.numberOfSheets).map { index ->
                val sheet = book.getSheet(index)
                check(sheet.rows <= MAX_ROWS && sheet.columns <= MAX_COLUMNS &&
                    sheet.rows.toLong() * sheet.columns <= MAX_CELLS) { "Таблица слишком велика" }
                val rows = MutableList(sheet.rows) { r -> MutableList(sheet.columns) { c -> sheet.getCell(c, r).contents } }
                sheet.mergedCells.forEach { range ->
                    val value = rows[range.topLeft.row][range.topLeft.column]
                    for (r in range.topLeft.row..range.bottomRight.row) for (c in range.topLeft.column..range.bottomRight.column) rows[r][c] = value
                }
                sheet.name to rows
            }
        } finally { book.close() }
    }

    private fun xlsx(bytes: ByteArray): List<Pair<String, List<List<String>>>> {
        val files = mutableMapOf<String, String>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entries = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                check(++entries <= 1000) { "Слишком много файлов в отчёте" }
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= 50 * 1024 * 1024) { "Распакованный отчёт слишком велик" }
                    if (entry.name.endsWith(".xml") || entry.name.endsWith(".rels")) out.write(buffer, 0, count)
                }
                if (out.size() > 0) files[entry.name] = out.toString("UTF-8")
            }
        }
        fun xml(name: String) = Jsoup.parse(files[name].orEmpty(), "", Parser.xmlParser())
        val strings = xml("xl/sharedStrings.xml").select("si").map { si -> si.select("t").joinToString("") { it.wholeText() } }
        val relationships = xml("xl/_rels/workbook.xml.rels").select("Relationship").associate {
            it.attr("Id") to it.attr("Target").let { target ->
                if (target.startsWith('/')) target.removePrefix("/") else "xl/$target"
            }
        }
        return xml("xl/workbook.xml").select("sheet").map { sheet ->
            val target = relationships[sheet.attr("r:id")] ?: error("Не найден лист отчёта")
            val doc = xml(target)
            val cells = mutableMapOf<Pair<Int, Int>, String>()
            var maxRow = -1; var maxColumn = -1
            doc.select("sheetData row c").forEach { cell ->
                val position = address(cell.attr("r"))
                maxRow = maxOf(maxRow, position.first); maxColumn = maxOf(maxColumn, position.second)
                val value = when (cell.attr("t")) {
                    "s" -> strings.getOrElse(cell.select("v").text().toIntOrNull() ?: -1) { "" }
                    "inlineStr" -> cell.select("is t").joinToString("") { it.wholeText() }
                    else -> cell.select("v").text()
                }
                cells[position] = value
                check(cells.size <= MAX_CELLS) { "Таблица слишком велика" }
            }
            check((maxRow + 1).toLong() * (maxColumn + 1) <= MAX_CELLS) { "Таблица слишком велика" }
            val rows = MutableList(maxRow + 1) { r -> MutableList(maxColumn + 1) { c -> cells[r to c].orEmpty() } }
            doc.select("mergeCells mergeCell").forEach { merge ->
                val endpoints = merge.attr("ref").split(':')
                val first = address(endpoints[0]); val last = address(endpoints.last())
                val value = cells[first].orEmpty()
                for (r in first.first..minOf(last.first, maxRow)) for (c in first.second..minOf(last.second, maxColumn)) rows[r][c] = value
            }
            sheet.attr("name") to rows
        }
    }

    private fun address(text: String): Pair<Int, Int> {
        val match = Regex("([A-Za-z]+)([0-9]+)").matchEntire(text) ?: error("Некорректная ячейка отчёта")
        val column = match.groupValues[1].uppercase().fold(0) { sum, c -> sum * 26 + (c - 'A' + 1) } - 1
        val row = match.groupValues[2].toInt() - 1
        check(row in 0 until MAX_ROWS && column in 0 until MAX_COLUMNS) { "Таблица слишком велика" }
        return row to column
    }

    private fun html(text: String): List<Pair<String, List<List<String>>>> {
        val doc = Jsoup.parse(text)
        return doc.select("table").filter { it.parents().none { parent -> parent.tagName() == "table" } }.mapIndexed { index, table ->
            val grid = mutableMapOf<Pair<Int, Int>, String>()
            val trs = table.select("tr")
            check(trs.size <= MAX_ROWS) { "Таблица слишком велика" }
            trs.forEachIndexed { r, tr ->
                var c = 0
                tr.children().filter { it.tagName() in listOf("th", "td") }.forEach { cell ->
                    while (grid.containsKey(r to c)) c++
                    val rowSpan = (cell.attr("rowspan").toIntOrNull() ?: 1).coerceAtLeast(1)
                    val colSpan = (cell.attr("colspan").toIntOrNull() ?: 1).coerceAtLeast(1)
                    check(r + rowSpan <= MAX_ROWS && c + colSpan <= MAX_COLUMNS &&
                        grid.size.toLong() + rowSpan.toLong() * colSpan <= MAX_CELLS) { "Таблица слишком велика" }
                    for (rr in r until r + rowSpan) for (cc in c until c + colSpan) grid[rr to cc] = cell.text()
                    c += colSpan
                }
            }
            val width = (grid.keys.maxOfOrNull { it.second } ?: -1) + 1
            "Таблица ${index + 1}" to List(trs.size) { r -> List(width) { c -> grid[r to c].orEmpty() } }
        }
    }

    fun describe(name: String, rawRows: List<List<String>>, students: List<GroupStudent>): HeadmanReportTable {
        val width = rawRows.maxOfOrNull { it.size } ?: 0
        val rows = rawRows.map { row -> List(width) { row.getOrElse(it) { "" }.trim() } }
        fun normalized(text: String) = text.lowercase().replace('ё', 'е').replace(Regex("[\\s.]+"), "").trim()
        val names = students.flatMap { student -> listOf(
            normalized(student.fullName),
            normalized("${student.surname} ${student.firstName.firstOrNull().orEmptyChar()}${student.patronymic.firstOrNull().orEmptyChar()}")
        ) }.toSet()
        val namePattern = Regex("[А-ЯЁ][а-яё-]+(?:\\s+[А-ЯЁ][а-яё-]+){1,3}|[А-ЯЁ][а-яё-]+\\s+[А-ЯЁ]\\.\\s*[А-ЯЁ]\\.")
        val firstData = rows.indexOfFirst { row -> row.any { normalized(it) in names } }.takeIf { it >= 0 }
            ?: rows.indexOfFirst { row -> row.firstOrNull()?.toIntOrNull() != null && row.any { namePattern.matches(it) } }.takeIf { it >= 0 }
            ?: error("Не удалось определить строки студентов в отчёте «$name»")
        val studentColumn = rows[firstData].indexOfFirst { normalized(it) in names }.takeIf { it >= 0 }
            ?: rows[firstData].indexOfFirst { namePattern.matches(it) }
        val headerRows = rows.take(firstData).filter { row ->
            // A document title merged across all columns does not label an individual metric.
            row.filter { it.isNotBlank() }.distinct().size > 1
        }
        val headers = (0 until width).map { c ->
            headerRows.map { it[c] }.filter { it.isNotBlank() }.distinct().joinToString(" · ").ifBlank { "Столбец ${c + 1}" }
        }
        // Merged subject labels cover multiple metric columns. Skip report titles spanning the whole sheet.
        val subjects = linkedMapOf<String, MutableList<Int>>()
        val metric = Regex("(?i)^(?:.*(?:час|всего|итого|пропуск|уваж|неуваж|посещ|балл|текущ|итог|оценк|студент|фамил|семестр|контрольн|недел|процент|отч[её]т).*|(?:КН|ТБ|ИБ|ЛК|ЛР|ПР|КП|КР)\\s*\\d*|[\\d%./\\s]+)$")
        for (c in studentColumn + 1 until width) {
            val subject = headerRows.asReversed().map { it[c] }.firstOrNull { candidate ->
                candidate.length > 3 && candidate.any { it.isLetter() } &&
                    !metric.matches(candidate) &&
                    headerRows.any { row -> row.count { it == candidate } < width - studentColumn }
            }
            if (subject != null) subjects.getOrPut(subject) { mutableListOf() }.add(c)
        }
        return HeadmanReportTable(name, rows, firstData, studentColumn, headers, subjects)
    }

    private fun Char?.orEmptyChar(): String = this?.toString().orEmpty()
}
