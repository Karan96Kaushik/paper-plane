package com.example.notificationmonitor.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.notificationmonitor.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notificationmonitor.repository.NotificationRepository
import com.example.notificationmonitor.util.NotificationAccessHelper
import com.example.notificationmonitor.util.TimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: NotificationRepository
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessEnabled by remember {
        mutableStateOf(NotificationAccessHelper.isNotificationAccessEnabled(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessEnabled = NotificationAccessHelper.isNotificationAccessEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val stats by repository.observeHomeStats()
        .collectAsStateWithLifecycle(
            initialValue = NotificationRepository.HomeStats(0, 0, null)
        )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusBlock(
                label = "Notification Access",
                value = if (accessEnabled) "● Enabled" else "● Disabled",
                valueColor = if (accessEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            if (!accessEnabled) {
                Button(
                    onClick = {
                        val intent = NotificationAccessHelper.openNotificationListenerSettings()
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open notification access settings")
                }
            }

            StatusBlock(
                label = "Background monitoring",
                value = if (accessEnabled) "Running" else "Waiting for notification access"
            )
            Text(
                text = "New notifications are read while notification access is on, including when this screen is closed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusBlock(label = "Notifications today", value = stats.notificationsToday.toString())
            StatusBlock(label = "Monitored apps", value = stats.monitoredApps.toString())
            StatusBlock(label = "Republish apps", value = stats.republishApps.toString())

            Text(
                text = "Latest notification",
                style = MaterialTheme.typography.titleMedium
            )
            val latest = stats.latest
            if (latest == null) {
                Text(
                    text = "No notifications captured yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = latest.appName ?: latest.packageName,
                    style = MaterialTheme.typography.titleSmall
                )
                latest.title?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyLarge)
                }
                val body = latest.bigText ?: latest.text
                body?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = TimeFormatter.relativeTime(latest.postedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusBlock(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = valueColor
        )
    }
}
