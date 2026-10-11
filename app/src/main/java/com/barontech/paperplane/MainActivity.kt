package com.barontech.paperplane

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.barontech.paperplane.notification.CurtisActionId
import com.barontech.paperplane.notification.CurtisCommand
import com.barontech.paperplane.notification.CurtisInteractions
import com.barontech.paperplane.notification.CurtisIntents
import com.barontech.paperplane.notification.CurtisPayload
import com.barontech.paperplane.ui.NotificationMonitorApp
import com.barontech.paperplane.ui.theme.NotificationMonitorTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private var pendingNotificationId by mutableStateOf<Long?>(null)
    private var pendingCurtis by mutableStateOf<CurtisPayload?>(null)
    private var curtisLaunchId by mutableStateOf(0)
    private var pendingCurtisCommand by mutableStateOf<CurtisCommand?>(null)
    private var curtisCommandId by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestPostNotificationsPermission()
        if (savedInstanceState == null) {
            applyLaunchIntent(intent)
        }

        val app = application as NotificationMonitorApplication
        setContent {
            NotificationMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val openId = pendingNotificationId
                    NotificationMonitorApp(
                        repository = app.repository,
                        localNotificationManager = app.localNotificationManager,
                        republisher = app.republisher,
                        supabasePublisher = app.supabasePublisher,
                        pendingNotificationId = openId,
                        onPendingNotificationConsumed = { pendingNotificationId = null },
                        pendingCurtis = pendingCurtis,
                        curtisLaunchId = curtisLaunchId,
                        onPendingCurtisConsumed = { pendingCurtis = null },
                        pendingCurtisCommand = pendingCurtisCommand,
                        curtisCommandId = curtisCommandId,
                        onPendingCurtisCommandConsumed = { pendingCurtisCommand = null },
                        onCurtisSnooze = { payload, minutes ->
                            CurtisInteractions.snooze(this@MainActivity, payload, minutes)
                        },
                        onCurtisMarkDone = { payload ->
                            CurtisInteractions.markDone(this@MainActivity, payload)
                        },
                        onCurtisExternal = { uri ->
                            CurtisInteractions.openExternal(this@MainActivity, uri)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLaunchIntent(intent)
    }

    private fun applyLaunchIntent(source: Intent?) {
        val payload = CurtisIntents.payloadFrom(source?.extras)
        val action = CurtisActionId.fromWire(source?.getStringExtra(CurtisIntents.EXTRA_ACTION))
        if (payload != null && action?.opensUi == true) {
            pendingCurtisCommand = CurtisCommand(payload, action)
            curtisCommandId += 1
            pendingCurtis = null
            pendingNotificationId = null
            return
        }
        if (payload != null) {
            pendingCurtis = payload
            curtisLaunchId += 1
            pendingCurtisCommand = null
            pendingNotificationId = null
            return
        }
        pendingNotificationId = notificationIdFrom(source)
    }

    private fun maybeRequestPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun notificationIdFrom(source: Intent?): Long? {
        val id = source?.getLongExtra(EXTRA_OPEN_NOTIFICATION_ID, -1L) ?: return null
        return id.takeIf { it > 0L }
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATION_ID =
            "com.barontech.paperplane.extra.OPEN_NOTIFICATION_ID"
    }
}
