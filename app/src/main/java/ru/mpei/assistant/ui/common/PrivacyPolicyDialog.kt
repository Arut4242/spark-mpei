package ru.mpei.assistant.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mpei.assistant.ui.theme.AppleBlue

@Composable
fun WelcomePrivacyDialog(
    onAccept: () -> Unit
) {
    val context = LocalContext.current
    var showFullPolicy by remember { mutableStateOf(false) }

    if (showFullPolicy) {
        FullPrivacyDialog(onDismiss = { showFullPolicy = false })
    }

    AlertDialog(
        onDismissRequest = { /* Не разрешаем закрывать тапнув мимо при первом старте */ },
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(AppleBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Добро пожаловать в Spark",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Автономный помощник студента НИУ «МЭИ»",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Статус проекта
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Неофициальный независимый проект. Не аффилирован с ФГБОУ ВО «НИУ «МЭИ»».",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Пункт 1: Local-First
                PrivacyFeatureItem(
                    icon = Icons.Default.Lock,
                    title = "Приватность и Local-First",
                    description = "Все ваши пароли, сессии, оценки и расписание сохраняются и шифруются исключительно на этом устройстве (Android Keystore). У приложения нет собственных серверов или баз данных."
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Пункт 2: Прямое подключение
                PrivacyFeatureItem(
                    icon = Icons.Default.Public,
                    title = "Прямое соединение",
                    description = "Сетевые запросы идут напрямую к официальным серверам (bars.mpei.ru, mail.mpei.ru). Никакие данные не передаются третьим лицам или аналитическим сервисам."
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Пункт 3: Дисклеймер
                PrivacyFeatureItem(
                    icon = Icons.Default.Security,
                    title = "Справочный характер расчётов",
                    description = "Прогнозы баллов, расчёт пропусков и допуска носят ознакомительный характер. Официальными являются данные личного кабинета БАРС и деканата."
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = { showFullPolicy = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "Ознакомиться с Политикой конфиденциальности",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppleBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Принять и продолжить",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    )
}

@Composable
private fun PrivacyFeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun FullPrivacyDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Политика конфиденциальности",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Краткое резюме политики Spark:",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Хранение данных: Все учётные данные (сессии БАРС, логин и пароль студенческой почты ОСЭП) хранятся исключительно локально на вашем мобильном устройстве и защищены аппаратным шифрованием Android Keystore (AES-256-GCM / AES-256-SIV).\n\n" +
                            "2. Сеть: Приложение подключается только к официальным сервисам МЭИ (bars.mpei.ru, mail.mpei.ru, mpei.space) и GitHub (проверка версий). Приложение не имеет собственных промежуточных серверов.\n\n" +
                            "3. Диагностика: Логи приложения хранятся на устройстве, маскируют персональные данные (ФИО, логины, токены, URL-параметры) и передаются только по явному запросу пользователя.\n\n" +
                            "4. Отказ от ответственности: Spark является независимым программным обеспечением. Расчёты баллов, прогнозов и посещаемости носят информационно-справочный характер.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
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
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AppleBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Связь с разработчиком: @Qwerty28369",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppleBlue,
                        fontWeight = FontWeight.Medium
                    )
                }

                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/Arut4242/spark-mpei/blob/main/PRIVACY.md")
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "Открыть полный текст PRIVACY.md на GitHub",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppleBlue
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Закрыть")
            }
        }
    )
}
