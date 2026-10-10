package com.barontech.paperplane.ui.republish

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barontech.paperplane.database.WorkflowEntity
import com.barontech.paperplane.repository.NotificationRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowListScreen(
    repository: NotificationRepository,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val workflows by repository.observeWorkflows()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val apps by repository.observeMonitoredApps()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val appNames = apps.associate { it.packageName to (it.appName ?: it.packageName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workflows") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "A workflow matches notification text, then republishes the match or keeps it from being republished. New notifications are checked while the app is in the background.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Button(
                    onClick = onCreate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("New workflow")
                }
            }
            if (workflows.isEmpty()) {
                item {
                    Text(
                        text = "No workflows yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(workflows, key = { it.id }) { workflow ->
                    WorkflowRow(
                        workflow = workflow,
                        appLabel = appNames[workflow.packageName],
                        onOpen = { onOpen(workflow.id) },
                        onToggle = { enabled ->
                            scope.launch { repository.setWorkflowEnabled(workflow.id, enabled) }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun WorkflowRow(
    workflow: WorkflowEntity,
    appLabel: String?,
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
            Text(text = workflow.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = workflow.summary(appLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = workflow.enabled, onCheckedChange = onToggle)
    }
}
