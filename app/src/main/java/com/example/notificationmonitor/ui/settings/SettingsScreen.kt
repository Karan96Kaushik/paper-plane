package com.example.notificationmonitor.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notificationmonitor.BuildConfig
import com.example.notificationmonitor.R
import com.example.notificationmonitor.notification.LocalNotificationManager
import com.example.notificationmonitor.repository.NotificationRepository
import com.example.notificationmonitor.settings.RetentionPeriod
import com.example.notificationmonitor.util.NotificationAccessHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: NotificationRepository,
    localNotificationManager: LocalNotificationManager
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var accessEnabled by remember {
        mutableStateOf(NotificationAccessHelper.isNotificationAccessEnabled(context))
    }
    var batteryUnrestricted by remember {
        mutableStateOf(NotificationAccessHelper.isIgnoringBatteryOptimizations(context))
    }
    var batteryMessage by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var retentionExpanded by remember { mutableStateOf(false) }

    val retention by repository.observeRetentionPeriod()
        .collectAsStateWithLifecycle(initialValue = RetentionPeriod.SEVEN_DAYS)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessEnabled = NotificationAccessHelper.isNotificationAccessEnabled(context)
                batteryUnrestricted = NotificationAccessHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val versionName = remember {
        try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsSection(title = "Notification Access") {
                Text(
                    text = "Status: ${if (accessEnabled) "Enabled" else "Disabled"}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Notification Monitor needs notification access to capture events from other apps. Access is granted only in Android system settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        context.startActivity(
                            NotificationAccessHelper.openNotificationListenerSettings()
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Android settings")
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Background") {
                Text(
                    text = if (batteryUnrestricted) {
                        "Battery optimization: Unrestricted"
                    } else {
                        "Battery optimization: Optimized"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Monitoring keeps running after you leave the app. A silent status notification holds the process open. Unrestricted battery use makes that more reliable on this device. Choose what to republish on the Republish tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        try {
                            context.startActivity(
                                NotificationAccessHelper.openBatteryOptimizationSettings()
                            )
                            batteryMessage = null
                        } catch (_: Exception) {
                            batteryMessage = "Battery settings are not available on this device."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open battery optimization settings")
                }
                batteryMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Notifications") {
                Button(
                    onClick = {
                        val posted = localNotificationManager.showNotification(
                            title = context.getString(R.string.test_notification_title),
                            message = context.getString(R.string.test_notification_message)
                        )
                        testMessage = if (posted) {
                            "Test notification sent"
                        } else {
                            "Cannot post notifications. Grant notification permission in system settings."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send test notification")
                }
                testMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            SettingsSection(title = "History") {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear all history")
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Retention") {
                Text(
                    text = "Keep notifications for:",
                    style = MaterialTheme.typography.bodyMedium
                )
                ExposedDropdownMenuBox(
                    expanded = retentionExpanded,
                    onExpandedChange = { retentionExpanded = it }
                ) {
                    OutlinedTextField(
                        value = retention.label,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = retentionExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = retentionExpanded,
                        onDismissRequest = { retentionExpanded = false }
                    ) {
                        RetentionPeriod.entries.forEach { period ->
                            DropdownMenuItem(
                                text = { Text(period.label) },
                                onClick = {
                                    scope.launch { repository.setRetentionPeriod(period) }
                                    retentionExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            SettingsSection(title = "About") {
                Text(
                    text = "App version: $versionName",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "All notification data stays on this device. Nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all history?") },
            text = { Text("This permanently deletes all stored notification events on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.clearHistory()
                            showClearDialog = false
                        }
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
