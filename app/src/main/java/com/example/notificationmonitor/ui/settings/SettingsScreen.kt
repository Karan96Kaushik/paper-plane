package com.example.notificationmonitor.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.example.notificationmonitor.settings.SupabaseConfig
import com.example.notificationmonitor.sync.SupabaseOutcome
import com.example.notificationmonitor.sync.SupabasePublisher
import com.example.notificationmonitor.util.NotificationAccessHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: NotificationRepository,
    localNotificationManager: LocalNotificationManager,
    supabasePublisher: SupabasePublisher
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
    var supabaseEnabled by remember { mutableStateOf(false) }
    var supabaseUrl by remember { mutableStateOf("") }
    var supabasePublishableKey by remember { mutableStateOf("") }
    var supabaseTable by remember { mutableStateOf(SupabaseConfig.DEFAULT_TABLE) }
    var supabaseEmail by remember { mutableStateOf("") }
    var supabasePassword by remember { mutableStateOf("") }
    var supabaseKeyVisible by remember { mutableStateOf(false) }
    var supabasePasswordVisible by remember { mutableStateOf(false) }
    var supabaseStatus by remember { mutableStateOf<String?>(null) }
    var supabaseBusy by remember { mutableStateOf(false) }

    val retention by repository.observeRetentionPeriod()
        .collectAsStateWithLifecycle(initialValue = RetentionPeriod.SEVEN_DAYS)
    val savedSupabase by repository.observeSupabaseConfig()
        .collectAsStateWithLifecycle(initialValue = SupabaseConfig())

    LaunchedEffect(Unit) {
        val saved = repository.currentSupabaseConfig()
        supabaseEnabled = saved.enabled
        supabaseUrl = saved.projectUrl
        supabasePublishableKey = saved.publishableKey
        supabaseTable = saved.table
        supabaseEmail = saved.email
    }

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
                    text = stringResource(R.string.notification_access_explanation),
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

            SettingsSection(title = "Supabase") {
                Text(
                    text = "Send every captured notification to the signed-in Supabase user. Notification content leaves this device only after you sign in, turn this on, and save. If the device is offline, notifications wait here and are sent together when the connection returns.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Push notifications", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = supabaseEnabled,
                        onCheckedChange = { supabaseEnabled = it },
                        enabled = !supabaseBusy
                    )
                }
                OutlinedTextField(
                    value = supabaseUrl,
                    onValueChange = { supabaseUrl = it },
                    label = { Text("Project URL") },
                    placeholder = { Text("https://your-project.supabase.co") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = !supabaseBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = supabasePublishableKey,
                    onValueChange = { supabasePublishableKey = it },
                    label = { Text("Publishable key") },
                    placeholder = { Text("sb_publishable_...") },
                    singleLine = true,
                    visualTransformation = if (supabaseKeyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(
                            onClick = { supabaseKeyVisible = !supabaseKeyVisible },
                            enabled = !supabaseBusy
                        ) {
                            Text(if (supabaseKeyVisible) "Hide" else "Show")
                        }
                    },
                    enabled = !supabaseBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = supabaseTable,
                    onValueChange = { supabaseTable = it },
                    label = { Text("Table") },
                    singleLine = true,
                    enabled = !supabaseBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = supabaseEmail,
                    onValueChange = { supabaseEmail = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !supabaseBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = supabasePassword,
                    onValueChange = { supabasePassword = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (supabasePasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(
                            onClick = { supabasePasswordVisible = !supabasePasswordVisible },
                            enabled = !supabaseBusy
                        ) {
                            Text(if (supabasePasswordVisible) "Hide" else "Show")
                        }
                    },
                    enabled = !supabaseBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val draft = supabaseDraft(
                            enabled = supabaseEnabled,
                            projectUrl = supabaseUrl,
                            publishableKey = supabasePublishableKey,
                            table = supabaseTable,
                            email = supabaseEmail,
                            saved = savedSupabase
                        )
                        val password = supabasePassword
                        scope.launch {
                            supabaseBusy = true
                            val outcome = withContext(Dispatchers.IO) {
                                supabasePublisher.signIn(draft, supabaseEmail, password)
                            }
                            if (outcome is SupabaseOutcome.Success) {
                                supabasePassword = ""
                            }
                            supabaseStatus = outcome.userMessage()
                            supabaseBusy = false
                        }
                    },
                    enabled = !supabaseBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign in")
                }
                if (savedSupabase.session() != null) {
                    Text(
                        text = "Signed in as ${savedSupabase.accountEmail.ifBlank { savedSupabase.email }}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                supabaseBusy = true
                                repository.clearSupabaseSession()
                                supabasePassword = ""
                                supabaseStatus = "Signed out."
                                supabaseBusy = false
                            }
                        },
                        enabled = !supabaseBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign out")
                    }
                }
                Text(
                    text = "The publishable key and session stay on this device. The password is not saved. Use sb_publishable_... from Settings → API Keys in Supabase. Rows are inserted for the signed-in user, with columns user_id, local_id, device_id, package_name, app_name, title, text, sub_text, big_text, category, notification_key, posted_at, received_at, is_ongoing, and is_clearable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val draft = supabaseDraft(
                            enabled = supabaseEnabled,
                            projectUrl = supabaseUrl,
                            publishableKey = supabasePublishableKey,
                            table = supabaseTable,
                            email = supabaseEmail,
                            saved = savedSupabase
                        )
                        if (draft.enabled) {
                            val error = draft.validationError()
                            if (error != null) {
                                supabaseStatus = error
                                return@Button
                            }
                        } else if (draft.projectUrl.isNotBlank() && draft.normalizedUrl() == null) {
                            supabaseStatus = "Use an https project URL like https://your-project.supabase.co."
                            return@Button
                        } else if (draft.normalizedTable() == null) {
                            supabaseStatus = "Table name can only use letters, numbers, and underscores."
                            return@Button
                        }
                        scope.launch {
                            supabaseBusy = true
                            repository.saveSupabaseConfig(draft)
                            supabaseStatus = if (draft.enabled) {
                                "Supabase settings saved. New notifications will be sent."
                            } else {
                                "Supabase settings saved. Push is off."
                            }
                            supabaseBusy = false
                        }
                    },
                    enabled = !supabaseBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = {
                        val draft = supabaseDraft(
                            enabled = supabaseEnabled,
                            projectUrl = supabaseUrl,
                            publishableKey = supabasePublishableKey,
                            table = supabaseTable,
                            email = supabaseEmail,
                            saved = savedSupabase
                        )
                        scope.launch {
                            supabaseBusy = true
                            val outcome = withContext(Dispatchers.IO) {
                                supabasePublisher.test(draft)
                            }
                            supabaseStatus = outcome.userMessage()
                            supabaseBusy = false
                        }
                    },
                    enabled = !supabaseBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test connection")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            supabaseBusy = true
                            val outcome = withContext(Dispatchers.IO) {
                                supabasePublisher.pushPending(
                                    loadPage = { limit -> repository.unsyncedNotifications(limit) },
                                    markSynced = { ids -> repository.markSupabaseSynced(ids) }
                                )
                            }
                            supabaseStatus = outcome.userMessage()
                            supabaseBusy = false
                        }
                    },
                    enabled = !supabaseBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Push existing history")
                }
                supabaseStatus?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            SettingsSection(title = "About") {
                Text(
                    text = "App version: $versionName",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (savedSupabase.isReady()) {
                        "New notifications are sent to ${savedSupabase.accountEmail.ifBlank { "your Supabase account" }}."
                    } else {
                        "Notification content stays on this device until you sign in and turn on Supabase push."
                    },
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
            text = {
                Text(
                    if (savedSupabase.isReady()) {
                        "This permanently deletes all stored notification events on this device. Copies already stored in Supabase are not deleted."
                    } else {
                        "This permanently deletes all stored notification events on this device."
                    }
                )
            },
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

private fun supabaseDraft(
    enabled: Boolean,
    projectUrl: String,
    publishableKey: String,
    table: String,
    email: String,
    saved: SupabaseConfig
): SupabaseConfig {
    val draft = SupabaseConfig(
        enabled = enabled,
        projectUrl = projectUrl,
        publishableKey = publishableKey,
        table = table.trim().ifBlank { SupabaseConfig.DEFAULT_TABLE },
        email = email
    )
    val sameProject = draft.normalizedUrl() != null &&
        draft.normalizedUrl() == saved.normalizedUrl() &&
        draft.normalizedPublishableKey() == saved.normalizedPublishableKey()
    return if (sameProject) {
        draft.copy(
            accountEmail = saved.accountEmail,
            userId = saved.userId,
            accessToken = saved.accessToken,
            refreshToken = saved.refreshToken,
            accessTokenExpiresAt = saved.accessTokenExpiresAt
        )
    } else {
        draft
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
