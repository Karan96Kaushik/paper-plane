package com.example.notificationmonitor.notification

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.notificationmonitor.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receives system notification events independently of the app UI.
 * Matching notifications are republished from this callback so the work
 * continues while the activity is closed.
 */
class NotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var parser: NotificationParser
    private lateinit var repository: NotificationRepository
    private lateinit var republisher: NotificationRepublisher

    override fun onCreate() {
        super.onCreate()
        parser = NotificationParser(applicationContext)
        repository = NotificationRepository.getInstance(applicationContext)
        republisher = NotificationRepublisher(
            repository = repository,
            poster = LocalNotificationManager(applicationContext)
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        BackgroundMonitor.ensureRunning(this)
    }

    override fun onListenerDisconnected() {
        try {
            requestRebind(ComponentName(this, NotificationListener::class.java))
        } catch (error: Exception) {
            Log.w(TAG, "requestRebind failed", error)
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        // Ignore our own notifications to avoid feedback loops in history.
        if (sbn.packageName == packageName) return

        serviceScope.launch {
            try {
                val entity = parser.parse(sbn) ?: return@launch
                val rowId = repository.insertIfAllowed(entity) ?: return@launch
                republisher.onNewNotification(entity.copy(id = rowId))
            } catch (error: Exception) {
                Log.e(TAG, "Failed handling posted notification", error)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Structured for future persistence of removal events.
        if (sbn == null) return
        Log.d(TAG, "Notification removed package=${sbn.packageName}")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NotificationListener"
    }
}
