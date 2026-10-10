package com.barontech.paperplane.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barontech.paperplane.database.NotificationEntity
import com.barontech.paperplane.notification.NotificationRepublisher
import com.barontech.paperplane.notification.NotificationType
import com.barontech.paperplane.repository.NotificationRepository
import com.barontech.paperplane.util.TimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    repository: NotificationRepository,
    republisher: NotificationRepublisher,
    onOpenDetail: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val packages by repository.observeDistinctPackageNames()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<NotificationType?>(null) }
    var filterExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    val notificationsFlow = remember(selectedPackage, selectedType) {
        repository.observeHistory(selectedPackage, selectedType)
    }
    val notifications by notificationsFlow
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(notifications) {
        val visible = notifications.map { it.id }.toSet()
        selectedIds = selectedIds.intersect(visible)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedIds.isEmpty()) "History" else "${selectedIds.size} selected"
                    )
                },
                actions = {
                    if (selectedIds.isEmpty()) {
                        IconButton(
                            onClick = { scope.launch { repository.clearHistory() } }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear history"
                            )
                        }
                    } else {
                        TextButton(
                            onClick = { selectedIds = notifications.map { it.id }.toSet() }
                        ) {
                            Text("All")
                        }
                        IconButton(
                            onClick = {
                                if (working) return@IconButton
                                working = true
                                val ids = selectedIds.toList()
                                scope.launch {
                                    try {
                                        statusMessage = republisher.republishIds(ids).userMessage()
                                        selectedIds = emptySet()
                                    } finally {
                                        working = false
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Publish,
                                contentDescription = "Republish selected"
                            )
                        }
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel selection"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = filterExpanded,
                onExpandedChange = { filterExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = selectedPackage ?: "All apps",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Filter by app") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All apps") },
                        onClick = {
                            selectedPackage = null
                            filterExpanded = false
                        }
                    )
                    packages.forEach { pkg ->
                        DropdownMenuItem(
                            text = { Text(pkg) },
                            onClick = {
                                selectedPackage = pkg
                                filterExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                OutlinedTextField(
                    value = selectedType?.label ?: "All types",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Filter by type") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All types") },
                        onClick = {
                            selectedType = null
                            typeExpanded = false
                        }
                    )
                    NotificationType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = {
                                selectedType = type
                                typeExpanded = false
                            }
                        )
                    }
                }
            }

            Text(
                text = "Long-press a notification to select it, then republish the selection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            statusMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (notifications.isEmpty()) {
                Text(
                    text = "No notifications yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(notifications, key = { it.id }) { item ->
                        NotificationHistoryItem(
                            notification = item,
                            selected = item.id in selectedIds,
                            selectionMode = selectedIds.isNotEmpty(),
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    selectedIds = if (item.id in selectedIds) {
                                        selectedIds - item.id
                                    } else {
                                        selectedIds + item.id
                                    }
                                } else {
                                    onOpenDetail(item.id)
                                }
                            },
                            onLongClick = {
                                selectedIds = selectedIds + item.id
                            },
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) {
                                    selectedIds + item.id
                                } else {
                                    selectedIds - item.id
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationHistoryItem(
    notification: NotificationEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = buildString {
                        append(notification.appName ?: notification.packageName)
                        append(" · ")
                        append(NotificationType.fromCategory(notification.category).label)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = TimeFormatter.formatTime(notification.postedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            notification.title?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodyLarge)
            }
            val body = notification.bigText ?: notification.text
            body?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
            if (notification.republishedAt != null) {
                Text(
                    text = "Republished",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
