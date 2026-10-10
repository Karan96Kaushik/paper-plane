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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.barontech.paperplane.database.WorkflowEntity
import com.barontech.paperplane.notification.MatchField
import com.barontech.paperplane.notification.MatchMode
import com.barontech.paperplane.notification.NotificationRepublisher
import com.barontech.paperplane.notification.NotificationType
import com.barontech.paperplane.notification.StringMatcher
import com.barontech.paperplane.notification.WorkflowAction
import com.barontech.paperplane.notification.WorkflowEngine
import com.barontech.paperplane.repository.NotificationRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowEditScreen(
    workflowId: Long,
    repository: NotificationRepository,
    republisher: NotificationRepublisher,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by repository.observeMonitoredApps()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var draft by remember { mutableStateOf<WorkflowEntity?>(null) }
    var missing by remember { mutableStateOf(false) }
    var includeAlready by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(workflowId) {
        if (workflowId == 0L) {
            draft = WorkflowEntity(name = "")
        } else {
            val stored = repository.getWorkflow(workflowId)
            draft = stored
            missing = stored == null
        }
    }

    val workflow = draft
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (workflowId == 0L) "New workflow" else "Edit workflow") },
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
        if (missing || workflow == null) {
            Text(
                text = if (workflow == null && !missing) "Loading workflow…" else "Workflow not found",
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            return@Scaffold
        }

        fun update(next: WorkflowEntity) {
            draft = next
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = workflow.name,
                onValueChange = { update(workflow.copy(name = it.take(80))) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            SwitchRow(
                title = "Enabled",
                subtitle = "Enabled workflows run when a new notification arrives.",
                checked = workflow.enabled,
                onCheckedChange = { update(workflow.copy(enabled = it)) }
            )
            AppMenu(
                packageName = workflow.packageName,
                apps = apps.filter { it.packageName != context.packageName },
                onSelect = { update(workflow.copy(packageName = it)) }
            )
            HorizontalDivider()
            Text("Notification types", style = MaterialTheme.typography.titleMedium)
            CheckRow(
                label = "All types",
                checked = workflow.selectedTypes().size == NotificationType.entries.size,
                onCheckedChange = { checked ->
                    val next = if (checked) {
                        workflow.withSelection(NotificationType.entries.toSet())
                    } else {
                        workflow.withSelection(emptySet())
                    }
                    update(next)
                }
            )
            NotificationType.entries.forEach { type ->
                CheckRow(
                    label = type.label,
                    checked = type in workflow.selectedTypes(),
                    onCheckedChange = { update(workflow.toggleType(type)) }
                )
            }
            SwitchRow(
                title = "Include ongoing",
                subtitle = "Ongoing notifications are skipped unless this is on.",
                checked = workflow.includeOngoing,
                onCheckedChange = { update(workflow.copy(includeOngoing = it)) }
            )
            HorizontalDivider()
            Text("String match", style = MaterialTheme.typography.titleMedium)
            OptionMenu(
                label = "Look in",
                value = workflow.field().label,
                options = MatchField.entries,
                optionLabel = { it.label },
                onSelect = { update(workflow.copy(matchField = it.name)) }
            )
            OptionMenu(
                label = "Match",
                value = workflow.mode().label,
                options = MatchMode.entries,
                optionLabel = { it.label },
                onSelect = { update(workflow.copy(matchMode = it.name)) }
            )
            OutlinedTextField(
                value = workflow.pattern,
                onValueChange = { update(workflow.copy(pattern = it.take(200))) },
                label = { Text("Text to match") },
                supportingText = {
                    val regexError = if (workflow.mode() == MatchMode.REGEX) {
                        StringMatcher.regexError(workflow.pattern)
                    } else {
                        null
                    }
                    Text(regexError ?: "Leave this empty to match every notification that passes the filters above.")
                },
                modifier = Modifier.fillMaxWidth()
            )
            CheckRow(
                label = "Case sensitive",
                checked = workflow.caseSensitive,
                onCheckedChange = { update(workflow.copy(caseSensitive = it)) }
            )
            HorizontalDivider()
            Text("Then", style = MaterialTheme.typography.titleMedium)
            OptionMenu(
                label = "Action",
                value = workflow.action().label,
                options = WorkflowAction.entries,
                optionLabel = { it.label },
                onSelect = { update(workflow.copy(action = it.name)) }
            )
            if (workflow.action() == WorkflowAction.REPUBLISH) {
                OutlinedTextField(
                    value = workflow.titlePrefix,
                    onValueChange = { update(workflow.copy(titlePrefix = it.take(40))) },
                    label = { Text("Title prefix") },
                    supportingText = { Text("Optional text added to the republished title.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            CheckRow(
                label = "Include already republished",
                checked = includeAlready,
                onCheckedChange = { includeAlready = it }
            )
            Button(
                onClick = {
                    if (working) return@Button
                    working = true
                    scope.launch {
                        try {
                            val saved = saveDraft(workflow, repository)
                            if (saved == null) {
                                statusMessage = "Add a name for this workflow."
                            } else {
                                draft = saved
                                statusMessage = "Workflow saved."
                            }
                        } finally {
                            working = false
                        }
                    }
                },
                enabled = !working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save workflow")
            }
            OutlinedButton(
                onClick = {
                    if (working) return@OutlinedButton
                    working = true
                    scope.launch {
                        try {
                            val saved = saveDraft(workflow, repository)
                            if (saved == null) {
                                statusMessage = "Add a name for this workflow."
                                return@launch
                            }
                            draft = saved
                            val result = republisher.runWorkflow(saved, includeAlready)
                            statusMessage = result.userMessage()
                        } finally {
                            working = false
                        }
                    }
                },
                enabled = !working && workflow.matchesTypeReady(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Run on stored notifications")
            }
            TextButton(
                onClick = {
                    scope.launch {
                        val source = if (workflow.packageName.isBlank()) {
                            repository.recentNotifications(NotificationRepublisher.SCAN_LIMIT)
                        } else {
                            repository.recentByPackage(
                                workflow.packageName,
                                NotificationRepublisher.SCAN_LIMIT
                            )
                        }
                        val count = source.count { WorkflowEngine.matches(it, workflow) }
                        statusMessage = "$count stored notifications match this workflow."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Count stored matches")
            }
            if (workflow.id > 0L) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete workflow")
                }
            }
            Text(
                text = "If a notification matches a \"Don't republish\" workflow, it is not republished, even when an app rule or another workflow would republish it. The first matching republish workflow supplies the title prefix.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            statusMessage?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (confirmDelete && workflow != null && workflow.id > 0L) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete workflow?") },
            text = { Text("\"${workflow.name}\" will stop running.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteWorkflow(workflow.id)
                            confirmDelete = false
                            onBack()
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private suspend fun saveDraft(
    workflow: WorkflowEntity,
    repository: NotificationRepository
): WorkflowEntity? {
    if (workflow.name.isBlank()) return null
    val id = repository.saveWorkflow(workflow.copy(name = workflow.name.trim()))
    return workflow.copy(id = id, name = workflow.name.trim())
}

private fun WorkflowEntity.matchesTypeReady(): Boolean = allTypes || selectedTypes().isNotEmpty()

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppMenu(
    packageName: String,
    apps: List<com.barontech.paperplane.database.MonitoredAppEntity>,
    onSelect: (String) -> Unit
) {
    val label = if (packageName.isBlank()) {
        "Any app"
    } else {
        apps.firstOrNull { it.packageName == packageName }?.appName ?: packageName
    }
    OptionMenu(
        label = "Application",
        value = label,
        options = listOf("" to "Any app") + apps.map { it.packageName to (it.appName ?: it.packageName) },
        optionLabel = { it.second },
        onSelect = { onSelect(it.first) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionMenu(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
