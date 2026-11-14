package ru.mpei.assistant.ui.headman

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import ru.mpei.assistant.data.model.headman.*
import ru.mpei.assistant.data.repository.HeadmanRepository

@Composable
internal fun HeadmanRoster(repository: HeadmanRepository) {
    var students by remember { mutableStateOf<List<GroupStudent>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var showCopy by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(retry) {
        loading = true; error = null
        val result = repository.students()
        loading = false
        if (result.isSuccess) students = result.getOrThrow()
        else error = result.exceptionOrNull()?.message ?: "Не удалось загрузить список группы"
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { retry++ }, enabled = !loading) { Text("Обновить") }
            TextButton(onClick = { showCopy = true }, enabled = students.isNotEmpty()) {
                Text(if (copied) "Скопировано" else "Скопировать")
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { HeadmanError(it) { retry++ } }
        if (!loading && error == null && students.isEmpty()) Text("Список группы пуст", Modifier.padding(16.dp))
        HeadmanTable(
            headers = listOf("№", "ФИО", "Почта", "Статус обучения"),
            rows = students.map { listOf(it.number, it.fullName, it.email, it.status) },
            widths = listOf(52, 260, 240, 180),
            modifier = Modifier.weight(1f)
        )
    }
    if (showCopy) {
        var fields by remember { mutableStateOf(StudentCopyField.entries.filter { it.selectedByDefault }.toSet()) }
        AlertDialog(
            onDismissRequest = { showCopy = false },
            title = { Text("Что скопировать") },
            text = {
                Column {
                    StudentCopyField.entries.forEach { field ->
                        Row {
                            Checkbox(checked = field in fields, onCheckedChange = { checked ->
                                fields = if (checked) fields + field else fields - field
                            })
                            Text(field.title, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    Text("Столбцы разделяются табуляцией — можно вставить в таблицу.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(copyStudents(students, fields)))
                    copied = true; showCopy = false
                }, enabled = fields.isNotEmpty()) { Text("Скопировать") }
            },
            dismissButton = { TextButton(onClick = { showCopy = false }) { Text("Отмена") } }
        )
    }
}

@Composable
internal fun HeadmanTable(headers: List<String>, rows: List<List<String>>, widths: List<Int> = emptyList(), modifier: Modifier = Modifier) {
    Box(modifier.horizontalScroll(rememberScrollState())) {
        val tableWidth = headers.indices.sumOf { widths.getOrElse(it) { 170 } }
        Column(Modifier.width(tableWidth.dp)) {
            Row {
                headers.forEachIndexed { index, header ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.width(widths.getOrElse(index) { 170 }.dp)) {
                        Text(header, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(10.dp))
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(rows) { index, row ->
                    Surface(color = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .3f)) {
                        Row {
                            headers.indices.forEach { c ->
                                Text(row.getOrElse(c) { "" }, modifier = Modifier.width(widths.getOrElse(c) { 170 }.dp).padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
