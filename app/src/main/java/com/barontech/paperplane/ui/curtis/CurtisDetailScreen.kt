package com.barontech.paperplane.ui.curtis

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.barontech.paperplane.notification.CurtisAction
import com.barontech.paperplane.notification.CurtisActionId
import com.barontech.paperplane.notification.CurtisDestination
import com.barontech.paperplane.notification.CurtisImages
import com.barontech.paperplane.notification.CurtisLinks
import com.barontech.paperplane.notification.CurtisPayload
import com.barontech.paperplane.notification.CurtisStyle
import com.barontech.paperplane.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurtisDetailScreen(
    payload: CurtisPayload,
    onBack: () -> Unit,
    onSnooze: (Int) -> Unit,
    onMarkDone: () -> Unit,
    onInApp: (String) -> Unit,
    onExternal: (String) -> Boolean
) {
    var statusMessage by remember(payload.notificationId) { mutableStateOf<String?>(null) }
    var finished by remember(payload.notificationId) { mutableStateOf(false) }
    val imageUrl = payload.imageUrl ?: payload.largeIconUrl
    var image by remember(imageUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUrl) {
        image = if (imageUrl == null) {
            null
        } else {
            withContext(Dispatchers.IO) { CurtisImages.load(imageUrl, 480) }
        }
    }

    val actions = remember(payload) { actionsFor(payload) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(payload.subtitle ?: "Notification") },
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
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = payload.title, style = MaterialTheme.typography.headlineSmall)
                Text(text = payload.body, style = MaterialTheme.typography.bodyLarge)
                payload.expandedBody?.takeIf { it != payload.body }?.let { expanded ->
                    Text(text = expanded, style = MaterialTheme.typography.bodyMedium)
                }
                if (payload.style == CurtisStyle.PROGRESS) {
                    ProgressBlock(payload)
                }
                image?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = payload.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        contentScale = ContentScale.FillWidth
                    )
                }
                if (payload.isExpired()) {
                    Text(
                        text = "This notification has expired",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                payload.whenMillis?.let { DetailLine("When", TimeFormatter.formatDateTime(it)) }
                payload.expiresAtMillis?.let { DetailLine("Expires", TimeFormatter.formatDateTime(it)) }
                payload.category?.let { DetailLine("Category", it) }
                payload.relatedNotificationId?.let { DetailLine("Related", it) }
                payload.deepLink?.let { DetailLine("Link", it) }
                payload.shortCriticalText?.let { DetailLine("Status", it) }
                if (payload.ongoing) {
                    DetailLine("Status", "In progress")
                }
                val visibleExtras = payload.extras.filterKeys { key ->
                    key != "from" &&
                        !key.startsWith("google.") &&
                        !key.startsWith("gcm.")
                }
                if (visibleExtras.isNotEmpty()) {
                    Text(text = "Details", style = MaterialTheme.typography.titleMedium)
                    visibleExtras.toSortedMap().forEach { (key, value) ->
                        DetailLine(key, value.take(500))
                    }
                }
                if (actions.isNotEmpty()) {
                    Text(text = "Actions", style = MaterialTheme.typography.titleMedium)
                    actions.forEach { action ->
                        ActionButtons(
                            action = action,
                            enabled = !finished,
                            onSnooze = { minutes ->
                                if (payload.isExpired()) {
                                    statusMessage = "This notification has expired"
                                } else {
                                    onSnooze(minutes)
                                    statusMessage = if (minutes >= 60) {
                                        "Snoozed for 1 hour"
                                    } else {
                                        "Snoozed for $minutes minutes"
                                    }
                                }
                            },
                            onMarkDone = {
                                finished = true
                                onMarkDone()
                                onBack()
                            },
                            onOpen = { id ->
                                when (val destination = CurtisLinks.resolve(payload, id)) {
                                    is CurtisDestination.InApp -> onInApp(destination.route)
                                    is CurtisDestination.External -> {
                                        if (!onExternal(destination.uri)) {
                                            statusMessage = "Couldn't open that link"
                                        }
                                    }
                                    is CurtisDestination.Unresolved -> {
                                        statusMessage = "Couldn't open that link"
                                    }
                                }
                            }
                        )
                    }
                }
                statusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurtisTargetScreen(
    title: String,
    id: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            SelectionContainer {
                Text(text = id, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ProgressBlock(payload: CurtisPayload) {
    if (payload.progressIndeterminate) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(text = "In progress", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val max = payload.progressMax ?: return
    val current = (payload.progressCurrent ?: 0).coerceIn(0, max)
    LinearProgressIndicator(
        progress = { current.toFloat() / max.toFloat() },
        modifier = Modifier.fillMaxWidth()
    )
    Text(text = "$current / $max", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ActionButtons(
    action: CurtisAction,
    enabled: Boolean,
    onSnooze: (Int) -> Unit,
    onMarkDone: () -> Unit,
    onOpen: (CurtisActionId) -> Unit
) {
    when (action.id) {
        CurtisActionId.SNOOZE -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onSnooze(15) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Snooze 15 minutes")
                }
                OutlinedButton(
                    onClick = { onSnooze(60) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Snooze 1 hour")
                }
            }
        }
        CurtisActionId.MARK_DONE -> {
            Button(
                onClick = onMarkDone,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(action.label)
            }
        }
        CurtisActionId.OPEN,
        CurtisActionId.OPEN_DRAFT,
        CurtisActionId.VIEW_ISSUE -> {
            Button(
                onClick = { onOpen(action.id) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(action.label)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun actionsFor(payload: CurtisPayload): List<CurtisAction> {
    if (payload.actions.isNotEmpty()) return payload.actions
    val hasTarget = !payload.deepLink.isNullOrBlank() ||
        !payload.extras["link"].isNullOrBlank() ||
        !payload.extras["run_id"].isNullOrBlank()
    return if (hasTarget) listOf(CurtisAction(CurtisActionId.OPEN, "Open")) else emptyList()
}
