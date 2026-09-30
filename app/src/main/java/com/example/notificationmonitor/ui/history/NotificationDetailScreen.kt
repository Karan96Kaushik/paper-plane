package com.example.notificationmonitor.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notificationmonitor.repository.NotificationRepository
import com.example.notificationmonitor.util.TimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailScreen(
    notificationId: Long,
    repository: NotificationRepository,
    onBack: () -> Unit
) {
    val notification by repository.observeNotification(notificationId)
        .collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        val item = notification
        if (item == null) {
            Text(
                text = "Notification not found",
                modifier = Modifier.padding(padding).padding(16.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailRow("Application", item.appName)
                DetailRow("Package", item.packageName)
                DetailRow("Title", item.title)
                DetailRow("Text", item.text)
                DetailRow("Subtext", item.subText)
                DetailRow("Big text", item.bigText)
                DetailRow("Category", item.category)
                DetailRow("Posted time", TimeFormatter.formatDateTime(item.postedAt))
                DetailRow("Received time", TimeFormatter.formatDateTime(item.receivedAt))
                DetailRow("Notification key", item.notificationKey)
                DetailRow("Ongoing", item.isOngoing.toString())
                DetailRow("Clearable", item.isClearable.toString())
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    val display = value?.takeIf { it.isNotBlank() } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = display, style = MaterialTheme.typography.bodyLarge)
    }
}
