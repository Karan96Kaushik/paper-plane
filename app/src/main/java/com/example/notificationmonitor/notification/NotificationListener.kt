package com.example.notificationmonitor.notification

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
 */
class NotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var parser: NotificationParser
    private lateinit var repository: NotificationRepository

    override fun onCreate() {
        super.onCreate()
        parser = NotificationParser(applicationContext)
        repository = NotificationRepository.getInstance(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        // Ignore our own notifications to avoid feedback loops in history.
        if (sbn.packageName == packageName) return

        serviceScope.launch {
            try {
                val entity = parser.parse(sbn) ?: return@launch
                repository.insertIfAllowed(entity)
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
