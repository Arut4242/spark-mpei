package ru.mpei.assistant.ui.bars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.repository.BarsRepository
import ru.mpei.assistant.ui.settings.AutostartDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarsScreen(
    barsRepository: BarsRepository,
    sessionStorage: SessionStorage
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val subjects by remember(barsRepository) {
        barsRepository.getSubjectsFlow()
    }.collectAsState(initial = emptyList())
    var isAuthorized by remember { 
        mutableStateOf(barsRepository.isAuthorized()) 
    }
    var isLoading by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showAutostartDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Оценки БАРС")
                        val currentSem = subjects.firstOrNull()?.semester
                        if (!currentSem.isNullOrBlank()) {
                            Text(
                                text = currentSem,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (isAuthorized) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    val res = barsRepository.refreshGrades()
                                    isLoading = false
                                    if (res.isFailure) {
                                        val msg = res.exceptionOrNull()?.message ?: "Ошибка загрузки"
                                        snackbarHostState.showSnackbar(msg)
                                        if (msg.contains("Сессия")) {
                                            isAuthorized = false
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                !isAuthorized -> {
                    // Not authorized state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = "Вход в личный кабинет БАРС",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Для просмотра КМ и оценок авторизуйтесь через систему МЭИ. Авторизация происходит напрямую на устройстве, данные никуда не отправляются.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showLoginDialog = true }
                        ) {
                            Text("Войти через БАРС")
                        }
                    }
                }

                isLoading && subjects.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                subjects.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Нет загруженных данных",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    barsRepository.refreshGrades()
                                    isLoading = false
                                }
                            }
                        ) {
                            Text("Загрузить оценки")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = subjects,
                            key = { it.id },
                            contentType = { "subject_card" }
                        ) { subject ->
                            SubjectCard(
                                subject = subject,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }

            if (isLoading && subjects.isNotEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }

    if (showLoginDialog) {
        BarsLoginWebView(
            onDismiss = { showLoginDialog = false },
            onLoginSuccess = { cookies, studentUrl ->
                isAuthorized = true
                showLoginDialog = false
                if (!sessionStorage.hasShownAutostartPrompt) {
                    sessionStorage.hasShownAutostartPrompt = true
                    showAutostartDialog = true
                }
                scope.launch {
                    barsRepository.onNewLogin(cookies, studentUrl)
                    isLoading = true
                    val res = barsRepository.refreshGrades()
                    isLoading = false
                    if (res.isFailure) {
                        val msg = res.exceptionOrNull()?.message ?: "Ошибка загрузки оценок"
                        snackbarHostState.showSnackbar(msg)
                    } else {
                        snackbarHostState.showSnackbar("Оценки БАРС успешно загружены!")
                    }
                }
            }
        )
    }

    if (showAutostartDialog) {
        AutostartDialog(
            onDismiss = { showAutostartDialog = false }
        )
    }
}
