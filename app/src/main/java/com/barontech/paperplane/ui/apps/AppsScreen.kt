package com.barontech.paperplane.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barontech.paperplane.database.MonitoredAppEntity
import com.barontech.paperplane.repository.NotificationRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    repository: NotificationRepository
) {
    val scope = rememberCoroutineScope()
    val apps by repository.observeMonitoredApps()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(Unit) {
        repository.syncInstalledApps()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Apps") })
        }
    ) { padding ->
        if (apps.isEmpty()) {
            Text(
                text = "Discovering installed applications…",
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        onToggle = { enabled ->
                            scope.launch {
                                repository.setAppEnabled(app.packageName, enabled)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: MonitoredAppEntity,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
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
        }
        Switch(
            checked = app.enabled,
            onCheckedChange = onToggle
        )
    }
}
