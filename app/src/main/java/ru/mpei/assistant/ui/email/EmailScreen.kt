package ru.mpei.assistant.ui.email

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.jsoup.Jsoup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import ru.mpei.assistant.ui.theme.isAppInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import ru.mpei.assistant.data.network.cleanHtmlToPlainText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.platform.LocalContext
import ru.mpei.assistant.worker.EmailCheckWorker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import ru.mpei.assistant.data.model.email.EmailAttachment
import ru.mpei.assistant.data.model.email.EmailMessage
import ru.mpei.assistant.data.repository.EmailRepository
import ru.mpei.assistant.ui.theme.AppleBlue
import ru.mpei.assistant.ui.theme.AppleCardShape
import ru.mpei.assistant.ui.theme.AppleOrange
import ru.mpei.assistant.ui.theme.ApplePillShape
import ru.mpei.assistant.ui.theme.appleClickable
import ru.mpei.assistant.ui.theme.applePressAnimation
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class EmailFilterTab(val title: String) {
    ALL("Все"),
    UNREAD("Непрочтенные"),
    WITH_ATTACHMENTS("С вложениями"),
    URGENT("Важные")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailScreen(
    emailRepository: EmailRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isAuthorized by remember { mutableStateOf(emailRepository.isAuthorized()) }
    val emails by emailRepository.getEmailsFlow().collectAsState(initial = emptyList())
    val unreadCount by emailRepository.getUnreadCountFlow().collectAsState(initial = 0)
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedEmail by remember { mutableStateOf<EmailMessage?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val hasMoreOlder by emailRepository.hasMoreOlderFlow.collectAsState()
    var isLoadingOlder by remember { mutableStateOf(false) }
    var loadOlderError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(EmailFilterTab.ALL) }

    val context = LocalContext.current

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // Срабатывает только когда пользователь действительно проскроллил список вниз и подошёл к концу
            totalItems > 0 &&
                    listState.canScrollBackward &&
                    lastVisibleIndex >= totalItems - 2
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoadingOlder && !isRefreshing && hasMoreOlder && loadOlderError == null) {
            if (emailRepository.oldestLoadedMessageNumber != null) {
                isLoadingOlder = true
                val res = emailRepository.loadOlderEmails()
                isLoadingOlder = false
                if (res.isFailure) {
                    loadOlderError = res.exceptionOrNull()?.localizedMessage ?: "Ошибка загрузки"
                }
            }
        }
    }

    LaunchedEffect(isAuthorized) {
        if (isAuthorized) {
            EmailCheckWorker.start(context)
            isRefreshing = true
            loadOlderError = null
            val res = emailRepository.refreshEmails()
            isRefreshing = false
            if (res.isFailure) {
                snackbarHostState.showSnackbar(
                    res.exceptionOrNull()?.localizedMessage ?: "Ошибка обновления почты"
                )
            }
        } else {
            EmailCheckWorker.cancel(context)
        }
    }

    // Full screen detail view when an email is selected
    if (selectedEmail != null) {
        BackHandler(enabled = true) {
            selectedEmail = null
        }
        EmailDetailScreen(
            email = selectedEmail!!,
            emailRepository = emailRepository,
            onBack = { selectedEmail = null },
            snackbarHostState = snackbarHostState
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Почта ОСЭП",
                            fontWeight = FontWeight.Bold
                        )
                        if (isAuthorized && unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(ApplePillShape)
                                    .background(AppleBlue)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$unreadCount",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (isAuthorized) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isRefreshing = true
                                    loadOlderError = null
                                    val res = emailRepository.refreshEmails()
                                    isRefreshing = false
                                    if (res.isFailure) {
                                        snackbarHostState.showSnackbar(
                                            res.exceptionOrNull()?.localizedMessage ?: "Ошибка обновления почты"
                                        )
                                    }
                                }
                            },
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                            }
                        }
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Выйти")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isAuthorized) {
                EmailLoginCard(
                    onLoginSuccess = { isAuthorized = true },
                    emailRepository = emailRepository,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.Center)
                )
            } else {
                val filteredEmails = remember(emails, searchQuery, selectedTab) {
                    emails.filter { email ->
                        val matchesSearch = if (searchQuery.isBlank()) {
                            true
                        } else {
                            val q = searchQuery.trim().lowercase()
                            email.senderName.lowercase().contains(q) ||
                            email.senderEmail.lowercase().contains(q) ||
                            email.subject.lowercase().contains(q) ||
                            email.snippet.lowercase().contains(q)
                        }

                        val matchesTab = when (selectedTab) {
                            EmailFilterTab.ALL -> true
                            EmailFilterTab.UNREAD -> email.isUnread
                            EmailFilterTab.WITH_ATTACHMENTS -> email.hasAttachments
                            EmailFilterTab.URGENT -> email.isUrgent
                        }

                        matchesSearch && matchesTab
                    }
                }

                val groupedEmails = remember(filteredEmails) {
                    groupEmailsByDate(filteredEmails)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Search Bar (как в Outlook "Поиск в почте и среди людей")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Поиск в почте и среди людей",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Поиск",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Очистить",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                focusedBorderColor = AppleBlue,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        )
                    }

                    // Filter tabs ("Все", "Непрочтенные", "С вложениями", "Важные")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EmailFilterTab.values().forEach { tab ->
                            val isSelected = selectedTab == tab
                            val chipBg = if (isSelected) AppleBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            val chipTextColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                            Box(
                                modifier = Modifier
                                    .clip(ApplePillShape)
                                    .background(chipBg)
                                    .clickable { selectedTab = tab }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (tab == EmailFilterTab.WITH_ATTACHMENTS) {
                                        Icon(
                                            Icons.Default.AttachFile,
                                            contentDescription = null,
                                            tint = chipTextColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    } else if (tab == EmailFilterTab.URGENT) {
                                        Icon(
                                            Icons.Default.PriorityHigh,
                                            contentDescription = null,
                                            tint = chipTextColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = tab.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = chipTextColor
                                    )
                                    if (tab == EmailFilterTab.UNREAD && unreadCount > 0 && !isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(AppleBlue)
                                                .size(6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (filteredEmails.isEmpty() && isRefreshing) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (filteredEmails.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Ничего не найдено" else "Писем пока нет",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Попробуйте изменить поисковый запрос" else "Потяните для обновления или нажмите значок синхронизации",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groupedEmails.forEach { (dateGroupTitle, groupList) ->
                                item(key = "group_header_$dateGroupTitle") {
                                    Text(
                                        text = dateGroupTitle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppleBlue,
                                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                                    )
                                }

                                items(
                                    items = groupList,
                                    key = { it.id }
                                ) { email ->
                                    EmailListItemCard(
                                        email = email,
                                        onClick = {
                                            selectedEmail = email
                                            if (email.isUnread) {
                                                scope.launch { emailRepository.markAsRead(email.id) }
                                            }
                                        }
                                    )
                                }
                            }

                            if (isLoadingOlder) {
                                item(key = "footer_loading_older") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = AppleBlue
                                            )
                                            Text(
                                                text = "Загрузка предыдущих писем...",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else if (loadOlderError != null) {
                                item(key = "footer_error_older") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    loadOlderError = null
                                                    isLoadingOlder = true
                                                    val res = emailRepository.loadOlderEmails()
                                                    isLoadingOlder = false
                                                    if (res.isFailure) {
                                                        loadOlderError = res.exceptionOrNull()?.localizedMessage ?: "Ошибка загрузки"
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = "Не удалось загрузить старые письма. Повторить",
                                                fontSize = 12.sp,
                                                color = AppleBlue
                                            )
                                        }
                                    }
                                }
                            } else if (hasMoreOlder && filteredEmails.isNotEmpty() && searchQuery.isBlank() && selectedTab == EmailFilterTab.ALL && emailRepository.oldestLoadedMessageNumber != null) {
                                item(key = "footer_load_more_button") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    loadOlderError = null
                                                    isLoadingOlder = true
                                                    val res = emailRepository.loadOlderEmails()
                                                    isLoadingOlder = false
                                                    if (res.isFailure) {
                                                        loadOlderError = res.exceptionOrNull()?.localizedMessage ?: "Ошибка загрузки"
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = "Загрузить более ранние письма",
                                                fontSize = 12.sp,
                                                color = AppleBlue
                                            )
                                        }
                                    }
                                }
                            } else if (!hasMoreOlder && filteredEmails.isNotEmpty() && searchQuery.isBlank() && selectedTab == EmailFilterTab.ALL) {
                                item(key = "footer_all_loaded") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Все письма загружены",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выход из почты ОСЭП") },
            text = { Text("Вы действительно хотите выйти? Кэшированные письма, скачанные вложения и учетные данные будут удалены с устройства.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        scope.launch {
                            emailRepository.logout(context)
                            isAuthorized = false
                        }
                    }
                ) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun EmailListItemCard(
    email: EmailMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = AppleCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (email.isUnread) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = if (email.isUnread) AppleBlue.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .applePressAnimation()
            .appleClickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Unread dot indicator
            Box(
                modifier = Modifier
                    .padding(top = 5.dp, end = 10.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (email.isUnread) AppleBlue else Color.Transparent)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = email.senderName,
                        fontWeight = if (email.isUnread) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (email.isUrgent) {
                            Icon(
                                imageVector = Icons.Default.PriorityHigh,
                                contentDescription = "Важное",
                                tint = AppleOrange,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        if (email.hasAttachments) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Вложение",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = formatEmailDate(email.dateMillis),
                            fontSize = 11.sp,
                            fontWeight = if (email.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (email.isUnread) AppleBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = email.subject,
                    fontSize = 13.sp,
                    fontWeight = if (email.isUnread) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val displaySnippet = remember(email.snippet, email.bodyText, email.bodyHtml) {
                    val html = email.bodyHtml
                    val text = when {
                        email.snippet.isNotBlank() -> email.snippet
                        email.bodyText.isNotBlank() -> email.bodyText
                        !html.isNullOrBlank() -> {
                            try {
                                cleanHtmlToPlainText(html)
                            } catch (_: Exception) { "" }
                        }
                        else -> ""
                    }
                    text.trim()
                        .replace(Regex("""^[\|\s]+"""), "")
                        .replace("\\s+".toRegex(), " ")
                        .take(140)
                        .ifBlank { "(Без текста)" }
                }

                Text(
                    text = displaySnippet,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailDetailScreen(
    email: EmailMessage,
    emailRepository: EmailRepository,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentEmail by remember(email.id) { mutableStateOf(email) }
    var isFetchingDetails by remember { mutableStateOf(false) }

    LaunchedEffect(email.id) {
        val needsBody = currentEmail.bodyText.isBlank() && currentEmail.bodyHtml.isNullOrBlank()
        val needsAttachments = currentEmail.attachments.isEmpty() && currentEmail.hasAttachments
        if (needsBody || needsAttachments) {
            isFetchingDetails = true
            val res = emailRepository.fetchEmailDetails(email.id)
            isFetchingDetails = false
            if (res.isSuccess) {
                currentEmail = res.getOrThrow()
            }
        }
    }

    var isHtmlView by remember(currentEmail.bodyHtml, currentEmail.bodyText) {
        mutableStateOf(!currentEmail.bodyHtml.isNullOrBlank())
    }
    var downloadingFileName by remember { mutableStateOf<String?>(null) }
    var isDownloadingAll by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentEmail.subject,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!currentEmail.bodyHtml.isNullOrBlank()) {
                        TextButton(onClick = { isHtmlView = !isHtmlView }) {
                            Text(
                                text = if (isHtmlView) "Текст" else "HTML",
                                color = AppleBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                isFetchingDetails = true
                                val res = emailRepository.fetchEmailDetails(currentEmail.id)
                                isFetchingDetails = false
                                if (res.isSuccess) {
                                    currentEmail = res.getOrThrow()
                                    snackbarHostState.showSnackbar("Письмо обновлено")
                                } else {
                                    val err = res.exceptionOrNull()?.localizedMessage ?: "Ошибка обновления"
                                    snackbarHostState.showSnackbar(err)
                                }
                            }
                        },
                        enabled = !isFetchingDetails
                    ) {
                        if (isFetchingDetails) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить письмо")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header card with Subject, Sender avatar, Recipient, and Attachments
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Subject title
                Text(
                    text = currentEmail.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Sender block (Avatar, Name, Date/Time, Recipient)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar Circle with Initials
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(AppleBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getSenderInitials(currentEmail.senderName),
                            color = AppleBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentEmail.senderName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = formatDetailDate(currentEmail.dateMillis),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (currentEmail.senderEmail.isNotBlank()) {
                            Text(
                                text = "<${currentEmail.senderEmail}>",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (currentEmail.recipient.isNotBlank()) {
                            Text(
                                text = "Кому: ${currentEmail.recipient}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Loading attachments indicator if in progress
                if (isFetchingDetails && currentEmail.attachments.isEmpty() && currentEmail.hasAttachments) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Загрузка вложений с сервера...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Attachments section (tiles matching Outlook screenshot)
                val attachments = currentEmail.attachments
                if (attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = AppleBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Вложений: ${attachments.size}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (attachments.size > 1) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        isDownloadingAll = true
                                        for (att in attachments) {
                                            emailRepository.downloadAttachment(context, currentEmail.id, att)
                                        }
                                        isDownloadingAll = false
                                        snackbarHostState.showSnackbar("Все вложения (${attachments.size}) загружены")
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                if (isDownloadingAll) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Скачать все", fontSize = 12.sp, color = AppleBlue)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Attachment cards list
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        attachments.forEach { att ->
                            val isCached = emailRepository.isAttachmentCached(context, currentEmail.id, att.fileName)
                            val isLoading = downloadingFileName == att.fileName

                            AttachmentTileCard(
                                attachment = att,
                                isCached = isCached,
                                isLoading = isLoading,
                                onClick = {
                                    scope.launch {
                                        downloadingFileName = att.fileName
                                        val res = emailRepository.downloadAttachment(context, currentEmail.id, att)
                                        downloadingFileName = null
                                        if (res.isSuccess) {
                                            openAttachment(context, res.getOrThrow(), att.mimeType)
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                res.exceptionOrNull()?.localizedMessage ?: "Ошибка скачивания вложения"
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (isFetchingDetails) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = AppleBlue,
                    trackColor = Color.Transparent
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            // Message Body (always on a clean white background so all fonts/HTML styles remain crisp and readable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
            ) {
                val hasNoContent = currentEmail.bodyText.isBlank() && currentEmail.bodyHtml.isNullOrBlank()

                if (isFetchingDetails && hasNoContent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AppleBlue)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Загрузка содержимого письма...",
                                fontSize = 13.sp,
                                color = Color(0xFF636366)
                            )
                        }
                    }
                } else if (isHtmlView && !currentEmail.bodyHtml.isNullOrBlank()) {
                    EmailHtmlViewer(
                        htmlBody = currentEmail.bodyHtml,
                        plainBody = currentEmail.bodyText,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    )
                } else {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            val displayText = remember(currentEmail.bodyText, currentEmail.bodyHtml) {
                                val html = currentEmail.bodyHtml
                                when {
                                    currentEmail.bodyText.isNotBlank() -> currentEmail.bodyText
                                    !html.isNullOrBlank() -> cleanHtmlToPlainText(html)
                                    else -> ""
                                }.ifBlank { "(Пустое сообщение)" }
                            }

                            Text(
                                text = displayText,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = Color(0xFF1C1C1E)
                            )

                            if (hasNoContent && !isFetchingDetails) {
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            isFetchingDetails = true
                                            val res = emailRepository.fetchEmailDetails(currentEmail.id)
                                            isFetchingDetails = false
                                            if (res.isSuccess) {
                                                currentEmail = res.getOrThrow()
                                            } else {
                                                val err = res.exceptionOrNull()?.localizedMessage ?: "Ошибка загрузки"
                                                snackbarHostState.showSnackbar(err)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Загрузить с сервера", color = AppleBlue)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentTileCard(
    attachment: EmailAttachment,
    isCached: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (typeColor, typeLabel) = when {
        attachment.isPdf -> Pair(Color(0xFFE53935), "PDF")
        attachment.isDoc -> Pair(Color(0xFF1E88E5), "DOC")
        attachment.isXls -> Pair(Color(0xFF43A047), "XLS")
        attachment.isPpt -> Pair(Color(0xFFFB8C00), "PPT")
        attachment.isArchive -> Pair(Color(0xFFF57C00), "ZIP")
        attachment.isImage -> Pair(Color(0xFF8E24AA), "IMG")
        else -> Pair(Color(0xFF757575), attachment.extension.uppercase().take(4).ifEmpty { "FILE" })
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .applePressAnimation()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(typeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = typeLabel,
                    color = typeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val sizeStr = attachment.getFormattedSize()
                val statusStr = if (isCached) "Загружено • Нажмите для открытия" else "Нажмите для загрузки"
                Text(
                    text = if (sizeStr.isNotBlank()) "$sizeStr • $statusStr" else statusStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (isCached) Icons.AutoMirrored.Filled.OpenInNew else Icons.Default.Download,
                    contentDescription = null,
                    tint = if (isCached) AppleBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun EmailHtmlViewer(
    htmlBody: String?,
    plainBody: String,
    modifier: Modifier = Modifier
) {
    val fullHtml = remember(htmlBody, plainBody) {
        val content = if (!htmlBody.isNullOrBlank()) {
            htmlBody
        } else {
            plainBody
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>")
        }
        """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
        <style>
            * { box-sizing: border-box; }
            html, body {
                margin: 0;
                padding: 14px;
                background-color: #ffffff !important;
                color: #1c1c1e !important;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                font-size: 14px;
                line-height: 1.6;
                word-wrap: break-word;
            }
            a { color: #007aff !important; text-decoration: underline; }
            img { width: 100% !important; height: auto !important; border-radius: 8px; }
            table { width: 100% !important; border-collapse: collapse; margin: 8px 0; background-color: #ffffff !important; }
            th, td { border: 1px solid #e5e5ea; padding: 6px 8px; font-size: 13px; color: #1c1c1e !important; background-color: #ffffff !important; }
            pre, code {
                white-space: pre-wrap;
                font-family: SF Mono, Consolas, monospace;
                background-color: #f2f2f7 !important;
                color: #1c1c1e !important;
                padding: 2px 4px;
                border-radius: 4px;
                font-size: 12px;
            }
            blockquote {
                border-left: 3px solid #007aff;
                margin: 8px 0;
                padding-left: 10px;
                color: #636366 !important;
            }
            p { margin: 0 0 10px 0; }
            [style*="color: #fff"], [style*="color:#fff"], [style*="color: white"], [style*="color:white"], [style*="color: #ffffff"], [style*="color:#ffffff"] {
                color: #1c1c1e !important;
            }
            [style*="background-color: #000"], [style*="background-color:#000"], [style*="background-color: black"], [style*="background-color:black"] {
                background-color: #f2f2f7 !important;
                color: #1c1c1e !important;
            }
        </style>
        </head>
        <body bgcolor="#ffffff">
            $content
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.WHITE)
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    useWideViewPort = false
                    loadWithOverviewMode = true
                    defaultFontSize = 14
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    blockNetworkImage = true // Защита от скрытых веб-маяков и трекинговых пикселей
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            view.context.startActivity(intent)
                        } catch (_: Exception) {}
                        return true
                    }
                }
            }
        },
        update = { webView ->
            webView.setBackgroundColor(android.graphics.Color.WHITE)
            webView.loadDataWithBaseURL("https://mail.mpei.ru", fullHtml, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}

@Composable
fun EmailLoginCard(
    onLoginSuccess: () -> Unit,
    emailRepository: EmailRepository,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Card(
        shape = AppleCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AppleBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = AppleBlue,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Корпоративная почта ОСЭП",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Сервер mail.mpei.ru (Exchange/Outlook)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = login,
                onValueChange = { login = it },
                label = { Text("Логин студента (например, ivanovii)") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль от почты ОСЭП") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    scope.launch {
                        isLoading = true
                        val res = emailRepository.login(login, password)
                        isLoading = false
                        if (res.isSuccess) {
                            onLoginSuccess()
                        } else {
                            snackbarHostState.showSnackbar(
                                res.exceptionOrNull()?.localizedMessage ?: "Ошибка авторизации в ОСЭП"
                            )
                        }
                    }
                },
                enabled = !isLoading && login.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Войти в почту", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Данные хранятся только на устройстве в защищенном хранилище Android Keystore. Входя, вы соглашаетесь с Политикой конфиденциальности.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

fun getSenderInitials(name: String): String {
    val clean = name.trim()
    if (clean.isEmpty()) return "?"
    val parts = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
        parts.isNotEmpty() -> parts[0].take(2).uppercase()
        else -> "?"
    }
}

fun groupEmailsByDate(emails: List<EmailMessage>): Map<String, List<EmailMessage>> {
    val groups = linkedMapOf<String, MutableList<EmailMessage>>()
    val now = Calendar.getInstance()
    val todayYear = now.get(Calendar.YEAR)
    val todayDay = now.get(Calendar.DAY_OF_YEAR)

    val startOfThisWeek = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val startOfLastWeek = startOfThisWeek - (7 * 24 * 60 * 60 * 1000L)

    for (email in emails) {
        val cal = Calendar.getInstance().apply { timeInMillis = email.dateMillis }
        val emailYear = cal.get(Calendar.YEAR)
        val emailDay = cal.get(Calendar.DAY_OF_YEAR)

        val groupTitle = when {
            emailYear == todayYear && emailDay == todayDay -> "СЕГОДНЯ"
            email.dateMillis >= startOfThisWeek -> "НА ЭТОЙ НЕДЕЛЕ"
            email.dateMillis >= startOfLastWeek -> "НА ПРОШЛОЙ НЕДЕЛЕ"
            else -> "РАНЕЕ"
        }
        groups.getOrPut(groupTitle) { mutableListOf() }.add(email)
    }
    return groups
}

fun formatEmailDate(timeMillis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
    val now = Calendar.getInstance()

    return if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    ) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))
    } else if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
        SimpleDateFormat("d MMM", Locale("ru")).format(Date(timeMillis))
    } else {
        SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(timeMillis))
    }
}

fun formatDetailDate(timeMillis: Long): String {
    return SimpleDateFormat("EEE dd.MM.yyyy HH:mm", Locale("ru")).format(Date(timeMillis))
}

fun openAttachment(context: Context, file: File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val safeMime = if (mimeType.isNotBlank() && mimeType != "application/octet-stream") {
            mimeType
        } else {
            val ext = file.extension.lowercase()
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, safeMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Открыть файл"))
    } catch (e: Exception) {
        Toast.makeText(context, "Не найдено приложение для открытия файла: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
