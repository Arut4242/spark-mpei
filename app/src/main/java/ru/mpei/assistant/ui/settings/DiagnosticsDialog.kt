package ru.mpei.assistant.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.ui.theme.ApplePillShape
import ru.mpei.assistant.util.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsDialog(
    sessionStorage: SessionStorage,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(AppLogger.getLogsList()) }
    val isVpn = remember { AppLogger.isVpnActive(context) }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Диагностика и логи") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            AppLogger.clear()
                            logs = AppLogger.getLogsList()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Очистить логи")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                // Status overview card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Состояние приложения:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))

                        // VPN Status
                        StatusRow(
                            label = "VPN статус:",
                            value = if (isVpn) "ВКЛЮЧЕН (⚠️ Блокирует МЭИ!)" else "Отключен (норма)",
                            isWarning = isVpn
                        )

                        // BARS Status
                        val barsAuth = !sessionStorage.barsCookies.isNullOrBlank()
                        StatusRow(
                            label = "БАРС:",
                            value = if (barsAuth) "Сессия активна" else "Не авторизован ⚠️",
                            isWarning = !barsAuth
                        )

                        // Schedule Group
                        val groupSelected = sessionStorage.selectedGroupId != -1L
                        StatusRow(
                            label = "Группа:",
                            value = sessionStorage.selectedGroupName ?: "Не выбрана ⚠️",
                            isWarning = !groupSelected
                        )

                        // Email Status
                        StatusRow(
                            label = "Почта ОСЭП:",
                            value = if (sessionStorage.isOsepAuthorized) "${sessionStorage.osepLogin}" else "Не настроена",
                            isWarning = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { AppLogger.shareLogs(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Поделиться", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { AppLogger.copyToClipboard(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Копировать", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://t.me/Qwerty28369")
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Написать разработчику: @Qwerty28369", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Log console
                Text("Журнал событий (${logs.size} записей):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(10.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            "Логи пока пусты. Попробуйте обновить экраны расписания или БАРС.",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(verticalScroll)
                                .horizontalScroll(horizontalScroll)
                        ) {
                            logs.forEach { line ->
                                val textColor = when {
                                    line.contains("[ERROR]") -> Color(0xFFFF5252)
                                    line.contains("[WARN]") -> Color(0xFFFFB74D)
                                    line.contains("[DEBUG]") -> Color(0xFF90CAF9)
                                    else -> Color(0xFFE0E0E0)
                                }
                                Text(
                                    text = line,
                                    color = textColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, isWarning: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .clip(ApplePillShape)
                .background(
                    if (isWarning) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isWarning) Color(0xFFC62828) else Color(0xFF2E7D32)
            )
        }
    }
}
