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
import com.barontech.paperplane.ui.NotificationMonitorApp
import com.barontech.paperplane.ui.theme.NotificationMonitorTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private var pendingNotificationId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestPostNotificationsPermission()
        pendingNotificationId = notificationIdFrom(intent)

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
                        onPendingNotificationConsumed = { pendingNotificationId = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationId = notificationIdFrom(intent)
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
