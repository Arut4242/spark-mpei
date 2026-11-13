package ru.mpei.assistant.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.data.model.schedule.SearchEntity
import ru.mpei.assistant.data.repository.ScheduleRepository
import ru.mpei.assistant.ui.theme.ApplePillShape
import ru.mpei.assistant.ui.theme.detectTapAndDragAfterLongPress
import java.util.Collections

private val SearchDialogFieldShape = RoundedCornerShape(12.dp)

@Composable
fun GroupSelectDialog(
    scheduleRepository: ScheduleRepository,
    sessionStorage: SessionStorage,
    initialType: String = "group",
    onDismiss: () -> Unit,
    onGroupSelected: (SearchEntity) -> Unit
) {
    val searchTabs = remember {
        listOf(
            Pair("group", "Группы"),
            Pair("person", "Преподаватели"),
            Pair("auditorium", "Аудитории")
        )
    }

    val favorites by sessionStorage.favoriteSchedulesFlow.collectAsState()
    var localFavorites by remember { mutableStateOf(favorites) }
    var draggedFavItem by remember { mutableStateOf<SearchEntity?>(null) }
    var dragFavOffsetY by remember { mutableFloatStateOf(0f) }
    val favItemHeights = remember { mutableMapOf<String, Float>() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(favorites) {
        if (draggedFavItem == null) {
            localFavorites = favorites
        }
    }
    var selectedType by remember { mutableStateOf(initialType) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val (searchLabel, emptyText) = when (selectedType) {
        "person" -> Pair("Фамилия преподавателя (напр. Лебедева)", "Преподаватели не найдены")
        "auditorium" -> Pair("Номер аудитории (напр. Д-2 или А-100)", "Аудитории не найдены")
        else -> Pair("Название группы (напр. С-12-24)", "Группы не найдены")
    }

    LaunchedEffect(searchQuery, selectedType) {
        val query = searchQuery.trim()
        if (query.length >= 2) {
            delay(300) // Debounce typing with automatic cancellation on query change
            isLoading = true
            try {
                val result = scheduleRepository.search(query, type = selectedType)
                if (result.isSuccess) {
                    searchResults = result.getOrNull() ?: emptyList()
                }
            } finally {
                isLoading = false
            }
        } else {
            isLoading = false
            searchResults = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f),
        title = { Text("Выбор расписания") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Segmented control tabs (Apple HIG style)
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = LocalDensity.current.density,
                        fontScale = LocalDensity.current.fontScale.coerceIn(0.85f, 1.0f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ApplePillShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        searchTabs.forEach { (typeKey, title) ->
                            val isSelected = selectedType == typeKey
                            val bgModifier = if (isSelected) {
                                Modifier
                                    .weight(1f)
                                    .clip(ApplePillShape)
                                    .background(MaterialTheme.colorScheme.surface)
                            } else {
                                Modifier
                                    .weight(1f)
                                    .clip(ApplePillShape)
                            }
                            Row(
                                modifier = bgModifier
                                    .clickable {
                                        if (selectedType != typeKey) {
                                            selectedType = typeKey
                                        }
                                    }
                                    .padding(vertical = 7.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 11.5.sp,
                                    letterSpacing = (-0.2).sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(searchLabel, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SearchDialogFieldShape
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    if (searchQuery.trim().length >= 2) {
                        // Search results mode
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (searchResults.isEmpty()) {
                            Text(
                                text = emptyText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(
                                    items = searchResults,
                                    key = { it.id },
                                    contentType = { "search_item" }
                                ) { item ->
                                    val itemType = item.type ?: selectedType
                                    val itemIcon = when (itemType) {
                                        "person" -> Icons.Default.Person
                                        "auditorium" -> Icons.Default.LocationOn
                                        else -> Icons.Default.School
                                    }
                                    val isFav = favorites.any { it.id == item.id && (it.type ?: "group") == itemType }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem()
                                            .clickable { onGroupSelected(item.copy(type = itemType)) }
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = itemIcon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.label,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (!item.description.isNullOrBlank()) {
                                                Text(
                                                    text = item.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                sessionStorage.toggleFavoriteSchedule(item.copy(type = itemType))
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = if (isFav) "Удалить из избранного" else "В избранное",
                                                tint = if (isFav) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    } else {
                        // Empty query -> show Favorites list
                        if (favorites.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Избранное",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    items(
                                        items = localFavorites,
                                        key = { "${it.type}_${it.id}" }
                                    ) { item ->
                                        val itemType = item.type ?: "group"
                                        val itemKey = "${itemType}_${item.id}"
                                        val isBeingDragged = draggedFavItem?.id == item.id && (draggedFavItem?.type ?: "group") == itemType
                                        val itemIcon = when (itemType) {
                                            "person" -> Icons.Default.Person
                                            "auditorium" -> Icons.Default.LocationOn
                                            else -> Icons.Default.School
                                        }
                                        val typeTitle = when (itemType) {
                                            "person" -> "Преподаватель"
                                            "auditorium" -> "Аудитория"
                                            else -> "Группа"
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .zIndex(if (isBeingDragged) 10f else 1f)
                                                .graphicsLayer {
                                                    if (isBeingDragged) {
                                                        translationY = dragFavOffsetY
                                                        scaleX = 1.03f
                                                        scaleY = 1.03f
                                                        shadowElevation = 8.dp.toPx()
                                                    }
                                                }
                                                .animateItem()
                                                .onSizeChanged { size ->
                                                    favItemHeights[itemKey] = size.height.toFloat()
                                                }
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isBeingDragged) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                                                    else Color.Transparent
                                                )
                                                .pointerInput(itemKey) {
                                                    detectTapAndDragAfterLongPress(
                                                        onTap = {
                                                            if (draggedFavItem == null) {
                                                                onGroupSelected(item)
                                                            }
                                                        },
                                                        onDragStart = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            draggedFavItem = item
                                                            dragFavOffsetY = 0f
                                                        },
                                                        onDrag = { _, dragAmount ->
                                                            dragFavOffsetY += dragAmount.y
                                                            val currentIdx = localFavorites.indexOfFirst { it.id == item.id && (it.type ?: "group") == itemType }
                                                            if (currentIdx != -1) {
                                                                // Dragging down
                                                                if (dragFavOffsetY > 0 && currentIdx < localFavorites.size - 1) {
                                                                    val nextItem = localFavorites[currentIdx + 1]
                                                                    val nextHeight = favItemHeights["${nextItem.type}_${nextItem.id}"] ?: 56f
                                                                    val threshold = nextHeight * 0.5f
                                                                    if (dragFavOffsetY > threshold) {
                                                                        val mutable = localFavorites.toMutableList()
                                                                        Collections.swap(mutable, currentIdx, currentIdx + 1)
                                                                        localFavorites = mutable
                                                                        dragFavOffsetY -= nextHeight
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    }
                                                                }
                                                                // Dragging up
                                                                else if (dragFavOffsetY < 0 && currentIdx > 0) {
                                                                    val prevItem = localFavorites[currentIdx - 1]
                                                                    val prevHeight = favItemHeights["${prevItem.type}_${prevItem.id}"] ?: 56f
                                                                    val threshold = -prevHeight * 0.5f
                                                                    if (dragFavOffsetY < threshold) {
                                                                        val mutable = localFavorites.toMutableList()
                                                                        Collections.swap(mutable, currentIdx, currentIdx - 1)
                                                                        localFavorites = mutable
                                                                        dragFavOffsetY += prevHeight
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            sessionStorage.favoriteSchedules = localFavorites
                                                            draggedFavItem = null
                                                            dragFavOffsetY = 0f
                                                        },
                                                        onDragCancel = {
                                                            draggedFavItem = null
                                                            dragFavOffsetY = 0f
                                                            localFavorites = favorites
                                                        }
                                                    )
                                                }
                                                .padding(vertical = 6.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = itemIcon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.label,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = if (!item.description.isNullOrBlank()) "${item.description} • $typeTitle" else typeTitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "Удерживайте для перемещения",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = {
                                                    sessionStorage.removeFavoriteSchedule(item.id, item.type)
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Удалить из избранного",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Начните поиск расписания. Нажмите ⭐, чтобы добавить группу, преподавателя или аудиторию в избранное",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
