package com.barontech.paperplane.ui.republish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barontech.paperplane.database.RepublishRuleEntity
import com.barontech.paperplane.notification.NotificationRepublisher
import com.barontech.paperplane.notification.NotificationType
import com.barontech.paperplane.repository.NotificationRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRepublishScreen(
    packageName: String,
    repository: NotificationRepository,
    republisher: NotificationRepublisher,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val apps by repository.observeMonitoredApps()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val stored by repository.observeRepublishRule(packageName)
        .collectAsStateWithLifecycle(initialValue = null)
    val rule = stored ?: RepublishRuleEntity(packageName = packageName)
    val app = apps.firstOrNull { it.packageName == packageName }
    val selected = rule.selectedTypes()
    val allSelected = selected.size == NotificationType.entries.size

    var includeAlready by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app?.appName ?: packageName) },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (app != null && !app.enabled) {
                Text(
                    text = "Monitoring is off for this app, so new notifications will not be stored or republished. Notifications already stored can still be republished.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Republish this app", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "New notifications of the selected types are posted again in the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { enabled ->
                        scope.launch { repository.setRepublishEnabled(packageName, enabled) }
                    }
                )
            }
            HorizontalDivider()
            Text("Notification types", style = MaterialTheme.typography.titleMedium)
            TypeRow(
                label = "All types",
                checked = allSelected,
                onCheckedChange = { checked ->
                    val next = if (checked) {
                        rule.withSelection(NotificationType.entries.toSet())
                    } else {
                        rule.withSelection(emptySet())
                    }
                    scope.launch { repository.saveRepublishRule(next) }
                }
            )
            NotificationType.entries.forEach { type ->
                TypeRow(
                    label = type.label,
                    checked = type in selected,
                    onCheckedChange = {
                        scope.launch { repository.saveRepublishRule(rule.toggleType(type)) }
                    }
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Include ongoing", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Ongoing notifications such as media players are skipped unless this is on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rule.includeOngoing,
                    onCheckedChange = { include ->
                        scope.launch {
                            repository.saveRepublishRule(rule.copy(includeOngoing = include))
                        }
                    }
                )
            }
            TypeRow(
                label = "Include already republished",
                checked = includeAlready,
                onCheckedChange = { includeAlready = it }
            )
            Button(
                onClick = {
                    if (working) return@Button
                        working = true
                        val ruleToApply = rule
                        scope.launch {
                            try {
                                repository.saveRepublishRule(ruleToApply)
                                val result = republisher.republishPast(packageName, includeAlready)
                                statusMessage = result.userMessage()
                            } finally {
                                working = false
                            }
                        }
                },
                enabled = !working && (rule.allTypes || selected.isNotEmpty()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Republish past notifications")
            }
            Text(
                text = "Past republish uses the types selected above, including when the app switch is off. Automatic republish of new notifications uses the switch and the Republish tab master switch. Republished items use their own notification channel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            statusMessage?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TypeRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
