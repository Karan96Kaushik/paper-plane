package com.example.notificationmonitor.ui.republish

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notificationmonitor.database.MonitoredAppEntity
import com.example.notificationmonitor.database.RepublishRuleEntity
import com.example.notificationmonitor.notification.NotificationRepublisher
import com.example.notificationmonitor.repository.NotificationRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepublishScreen(
    repository: NotificationRepository,
    republisher: NotificationRepublisher,
    onOpenApp: (String) -> Unit,
    onOpenWorkflows: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by repository.observeMonitoredApps()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val rules by repository.observeRepublishRules()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val autoRepublish by repository.observeAutoRepublishEnabled()
        .collectAsStateWithLifecycle(initialValue = true)

    var query by remember { mutableStateOf("") }
    var includeAlready by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val rulesByPackage = remember(rules) { rules.associateBy { it.packageName } }
    val otherApps = remember(apps, context.packageName) {
        apps.filter { it.packageName != context.packageName }
    }
    val visibleApps = remember(otherApps, query) {
        otherApps.filter { app ->
            val name = app.appName.orEmpty()
            query.isBlank() ||
                name.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
        }
    }

    LaunchedEffect(Unit) {
        repository.syncInstalledApps()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Republish") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Choose which apps and notification types are posted again. Workflows can also match notification text and republish it, or keep a match from being republished.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedButton(
                    onClick = onOpenWorkflows,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Workflows")
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Republish new notifications", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Applies to every app you turn on below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoRepublish,
                        onCheckedChange = { enabled ->
                            scope.launch { repository.setAutoRepublishEnabled(enabled) }
                        }
                    )
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeAlready,
                        onCheckedChange = { includeAlready = it }
                    )
                    Text("Include notifications that were already republished")
                }
            }
            item {
                Button(
                    onClick = {
                        if (working) return@Button
                        working = true
                        scope.launch {
                            try {
                                val result = republisher.republishPastForEnabledApps(includeAlready)
                                statusMessage = result.userMessage()
                            } finally {
                                working = false
                            }
                        }
                    },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Republish past matches")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.setRepublishEnabledForPackages(
                                    otherApps.map { it.packageName },
                                    enabled = true
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("All apps")
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                repository.setRepublishEnabledForPackages(
                                    otherApps.map { it.packageName },
                                    enabled = false
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Turn off")
                    }
                }
            }
            item {
                statusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search apps") },
                    singleLine = true
                )
            }
            if (visibleApps.isEmpty()) {
                item {
                    Text(
                        text = "No applications to configure yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(visibleApps, key = { it.packageName }) { app ->
                    RepublishAppRow(
                        app = app,
                        rule = rulesByPackage[app.packageName],
                        onOpen = { onOpenApp(app.packageName) },
                        onToggle = { enabled ->
                            scope.launch { repository.setRepublishEnabled(app.packageName, enabled) }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RepublishAppRow(
    app: MonitoredAppEntity,
    rule: RepublishRuleEntity?,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = app.appName ?: app.packageName,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = rule?.selectionSummary() ?: "Off",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = rule?.enabled == true,
            onCheckedChange = onToggle
        )
    }
}
