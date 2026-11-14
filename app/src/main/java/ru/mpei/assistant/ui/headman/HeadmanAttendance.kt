package ru.mpei.assistant.ui.headman

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.mpei.assistant.data.model.headman.*
import ru.mpei.assistant.data.repository.HeadmanRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun HeadmanAttendance(repository: HeadmanRepository, journal: HeadmanJournal) {
    var dateText by rememberSaveable { mutableStateOf(headmanDay().toString()) }
    var followJournalDay by rememberSaveable { mutableStateOf(true) }
    var bySubject by rememberSaveable { mutableStateOf(false) }
    var subject by rememberSaveable { mutableStateOf("") }
    var subjects by remember { mutableStateOf<List<HeadmanSubject>>(emptyList()) }
    var lessons by remember { mutableStateOf<List<HeadmanLesson>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var selectedLesson by remember { mutableStateOf<HeadmanLesson?>(null) }
    var today by remember { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Moscow"))) }
    val date = LocalDate.parse(dateText)
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now(ZoneId.of("Europe/Moscow"))
            if (followJournalDay) dateText = headmanDay().toString()
            delay(30000)
        }
    }
    LaunchedEffect(journal.id) {
        repository.subjects(journal).onSuccess { subjects = it }
    }
    LaunchedEffect(journal.id, dateText, bySubject, subject, retry) {
        loading = true; error = null; lessons = emptyList()
        val result = repository.lessons(journal, if (bySubject) null else date,
            if (bySubject) subject.takeIf { it.isNotBlank() } else null)
        loading = false
        if (result.isSuccess) lessons = result.getOrThrow()
        else error = result.exceptionOrNull()?.message ?: "Не удалось загрузить занятия"
    }
    fun chooseDate(value: LocalDate) {
        dateText = value.toString(); followJournalDay = false; bySubject = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !bySubject, onClick = { bySubject = false }, label = { Text("По дате") })
            FilterChip(selected = bySubject, onClick = { bySubject = true }, label = { Text("По предмету") })
        }
        if (bySubject) {
            val options = listOf("Все предметы") + subjects.map { it.name }.distinct()
            HeadmanChoice("Предмет", subject.ifBlank { "Все предметы" }, options) { index ->
                subject = if (index == 0) "" else options[index]
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { chooseDate(date.minusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Предыдущий день")
                }
                TextButton(onClick = {
                    DatePickerDialog(context, { _, year, month, day -> chooseDate(LocalDate.of(year, month + 1, day)) },
                        date.year, date.monthValue - 1, date.dayOfMonth).show()
                }, modifier = Modifier.weight(1f)) { Text(date.format(formatter)) }
                IconButton(onClick = { chooseDate(date.plusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Следующий день")
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Вчера" to today.minusDays(1), "Сегодня" to today, "Завтра" to today.plusDays(1)).forEach { (label, value) ->
                    FilterChip(selected = date == value, onClick = { chooseDate(value) }, label = { Text(label) })
                }
            }
            if (followJournalDay && date != today) Text("До 08:00 показаны вчерашние занятия",
                modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { retry++ }, enabled = !loading, modifier = Modifier.align(Alignment.End)) { Text("Обновить") }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { HeadmanError(it) { retry++ } }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!loading && error == null && lessons.isEmpty()) item {
                Text("В журнале нет занятий по выбранному фильтру")
            }
            items(lessons, key = { it.id }) { lesson ->
                Card(onClick = { selectedLesson = lesson }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(lesson.title, style = MaterialTheme.typography.titleSmall)
                        if (lesson.status.isNotBlank()) Text(lesson.status, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(lesson.summary, style = MaterialTheme.typography.bodySmall)
                        Text("Отметить посещаемость", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    selectedLesson?.let { lesson ->
        LessonAttendanceDialog(repository, lesson,
            onDismiss = { selectedLesson = null }, onSaved = { selectedLesson = null; retry++ })
    }
}

@Composable
private fun LessonAttendanceDialog(repository: HeadmanRepository, lesson: HeadmanLesson, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var form by remember(lesson.id) { mutableStateOf<LessonAttendance?>(null) }
    var absentIds by remember(lesson.id) { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(lesson.id, retry) {
        loading = true; error = null
        val result = repository.attendance(lesson.id)
        loading = false
        if (result.isSuccess) {
            form = result.getOrThrow()
            absentIds = form!!.students.filter { it.reason != null }.map { it.id }.toSet()
        } else error = result.exceptionOrNull()?.message ?: "Не удалось загрузить отметки"
    }
    // Keep the draft open during the single save request; the server may already have accepted it.
    BackHandler(enabled = saving) { }
    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!saving) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(.96f).fillMaxHeight(.9f)) {
            Column(Modifier.padding(16.dp)) {
                Text(lesson.title, style = MaterialTheme.typography.titleSmall)
                Text("Галочка — отсутствует", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                if (loading || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                    if (form == null) TextButton(onClick = { retry++ }) { Text("Повторить") }
                }
                val students = form?.students.orEmpty()
                Text("Присутствует: ${students.size - absentIds.size} · Отсутствует: ${absentIds.size}",
                    style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.weight(1f)) {
                    items(students, key = { it.id }) { student ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = student.id in absentIds, enabled = !saving,
                                onCheckedChange = { absent -> absentIds = if (absent) absentIds + student.id else absentIds - student.id })
                            Column {
                                Text(student.name, style = MaterialTheme.typography.bodyMedium)
                                if (student.reason != null && student.reason != "1" && student.id in absentIds) {
                                    Text(if (student.reason == "2") "Уважительная причина (БАРС)" else "Причина пропуска в БАРС: ${student.reason}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Отмена") }
                    Button(enabled = form != null && !loading && !saving, onClick = {
                        val original = form ?: return@Button
                        saving = true; error = null
                        scope.launch {
                            val result = repository.saveAttendance(original, absentIds)
                            saving = false
                            if (result.isSuccess) onSaved()
                            else error = result.exceptionOrNull()?.message ?: "Не удалось сохранить отметки"
                        }
                    }) { Text(if (saving) "Сохранение…" else "Сохранить") }
                }
            }
        }
    }
}
