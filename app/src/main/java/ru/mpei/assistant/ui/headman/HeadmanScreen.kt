package ru.mpei.assistant.ui.headman

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.mpei.assistant.data.model.headman.*
import ru.mpei.assistant.data.parser.HeadmanParser
import ru.mpei.assistant.data.repository.HeadmanRepository

@Composable
fun HeadmanScreen(repository: HeadmanRepository) {
    val group by repository.group.collectAsState()
    var tab by rememberSaveable(group?.id) { mutableIntStateOf(0) }
    var journals by remember { mutableStateOf<List<HeadmanJournal>>(emptyList()) }
    var journal by remember { mutableStateOf<HeadmanJournal?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(group?.id, retry) {
        loading = true; error = null
        val result = repository.journals()
        loading = false
        if (result.isSuccess) {
            journals = result.getOrThrow()
            journal = HeadmanParser.currentJournal(journals)
            if (journal == null) error = if (journals.isEmpty()) "Для группы пока нет учебных журналов"
                else "Журнал текущего семестра не найден. Выберите семестр вручную"
        } else error = result.exceptionOrNull()?.message ?: "Не удалось загрузить журналы"
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("Посещаемость", "Список группы", "Отчёты").forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, modifier = Modifier.height(44.dp),
                    text = { Text(title, maxLines = 1, style = MaterialTheme.typography.labelSmall) })
            }
        }
        Text(group?.name.orEmpty(), style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        if (tab == 1) {
            HeadmanRoster(repository)
        } else {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (journals.isNotEmpty()) {
                HeadmanChoice("Семестр", journal?.name ?: "Выберите семестр", journals.map { it.name }) { index ->
                    journal = journals[index]; error = null
                }
            }
            error?.let { message ->
                HeadmanError(message) { retry++ }
            }
            journal?.let { selected ->
                key(selected.id) {
                    if (tab == 0) HeadmanAttendance(repository, selected)
                    else HeadmanReports(repository, selected)
                }
            }
        }
    }
}

@Composable
internal fun HeadmanChoice(label: String, selected: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $selected")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, text ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(index) })
            }
        }
    }
}

@Composable
internal fun HeadmanError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("Повторить") }
    }
}
