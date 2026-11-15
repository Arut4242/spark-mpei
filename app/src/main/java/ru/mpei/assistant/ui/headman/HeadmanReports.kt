package ru.mpei.assistant.ui.headman

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.mpei.assistant.data.model.headman.*
import ru.mpei.assistant.data.repository.HeadmanRepository

@Composable
internal fun HeadmanReports(repository: HeadmanRepository, journal: HeadmanJournal) {
    var kindIndex by rememberSaveable { mutableIntStateOf(0) }
    val kind = HeadmanReportKind.entries[kindIndex]
    var report by remember(kind, journal.id) { mutableStateOf(repository.cachedReport(kind, journal)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var view by rememberSaveable { mutableIntStateOf(0) }
    var sheetIndex by remember(report) { mutableIntStateOf(0) }
    var student by remember(report, sheetIndex) { mutableStateOf("") }
    var subject by remember(report, sheetIndex) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeadmanReportKind.entries.forEachIndexed { index, option ->
                FilterChip(selected = kindIndex == index, enabled = !loading,
                    onClick = { kindIndex = index; error = null }, label = { Text(option.title) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
            Button(enabled = !loading, onClick = {
                loading = true; error = null
                scope.launch {
                    val result = repository.requestReport(kind, journal)
                    loading = false
                    if (result.isSuccess) report = result.getOrThrow()
                    else error = result.exceptionOrNull()?.message ?: "Не удалось получить отчёт"
                }
            }) { Text(if (loading) "Формирование…" else if (report == null) "Сформировать отчёт" else "Обновить отчёт") }
        }
        Text("Отчёт запрашивается в БАРС только по этой кнопке.", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        error?.let { message ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            loading = true; error = null
                            scope.launch {
                                val result = repository.requestReport(kind, journal)
                                loading = false
                                if (result.isSuccess) report = result.getOrThrow()
                                else error = result.exceptionOrNull()?.message ?: "Не удалось получить отчёт"
                            }
                        }
                    ) {
                        Text("Повторить")
                    }
                }
            }
        }

        val current = report
        val bottomPadding = 100.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        if (current == null && !loading && error == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp)
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Отчёт «${kind.title}» ещё не сформирован",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Нажмите «Сформировать отчёт» выше для загрузки данных из БАРС",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (current != null) {
            if (current.tables.size > 1) {
                HeadmanChoice("Таблица", current.tables[sheetIndex].name, current.tables.map { it.name }) { sheetIndex = it }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Вся таблица", "По студенту", "По предмету").forEachIndexed { index, title ->
                    FilterChip(selected = view == index, onClick = { view = index }, label = { Text(title) })
                }
            }
            val table = current.tables[sheetIndex]
            if (view == 1) {
                val names = table.studentNames
                val selected = student.ifBlank { names.firstOrNull().orEmpty() }
                HeadmanChoice("Студент", selected, names) { student = names[it] }
                val studentRows = table.rows.drop(table.dataStart).filter { it.getOrNull(table.studentColumn) == selected }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(studentRows.flatMap { row ->
                        table.headers.indices.filter { it > table.studentColumn }.map { column ->
                            table.headers[column] to reportValue(kind, table, row, column)
                        }
                    }) { _, (label, value) ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                                Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            } else {
                var columns = table.headers.indices.toList()
                if (view == 2) {
                    val options = table.subjects.keys.toList()
                    val selected = subject.ifBlank { options.firstOrNull().orEmpty() }
                    if (options.isEmpty()) {
                        Text("В отчёте не удалось выделить предметы. Доступны полная таблица и просмотр по студенту.",
                            modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                    } else {
                        HeadmanChoice("Предмет", selected, options) { subject = options[it] }
                        columns = (0..table.studentColumn).toList() + table.subjects[selected].orEmpty()
                    }
                }
                val rows = table.rows.drop(table.dataStart).map { row -> columns.map { c -> reportValue(kind, table, row, c) } }
                HeadmanTable(headers = columns.map { table.headers[it] }, rows = rows,
                    widths = columns.map { if (it == table.studentColumn) 250 else 170 }, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun reportValue(kind: HeadmanReportKind, table: HeadmanReportTable, row: List<String>, column: Int): String {
    val raw = row.getOrElse(column) { "" }
    val header = table.headers[column]
    return if (kind == HeadmanReportKind.ATTENDANCE && column > table.studentColumn &&
        !header.contains('%') && !header.contains("процент", ignoreCase = true)) hoursToPairs(raw) else raw
}
