package com.barontech.paperplane.notification

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.barontech.paperplane.NotificationMonitorApplication
import com.barontech.paperplane.repository.NotificationRepository
import com.barontech.paperplane.settings.UserPreferences
import com.barontech.paperplane.sync.SupabaseNetworkSync
import com.barontech.paperplane.sync.SupabaseOutcome
import com.barontech.paperplane.sync.SupabasePublisher
import com.barontech.paperplane.sync.supabaseDeviceId
import kotlinx.coroutines.flow.first
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
    private lateinit var supabasePublisher: SupabasePublisher
    private var supabaseNetworkSync: SupabaseNetworkSync? = null

    override fun onCreate() {
        super.onCreate()
        parser = NotificationParser(applicationContext)
        repository = NotificationRepository.getInstance(applicationContext)
        republisher = NotificationRepublisher(
            repository = repository,
            poster = LocalNotificationManager(applicationContext)
        )
        val app = application
        if (app is NotificationMonitorApplication) {
            supabasePublisher = app.supabasePublisher
            supabaseNetworkSync = app.supabaseNetworkSync
        } else {
            val preferences = UserPreferences(applicationContext)
            supabasePublisher = SupabasePublisher(
                currentConfig = { preferences.supabaseConfig.first() },
                deviceId = supabaseDeviceId(applicationContext),
                exclusionRules = { repository.enabledSupabaseExclusionRules() }
            )
        }
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
                val stored = entity.copy(id = rowId)
                try {
                    republisher.onNewNotification(stored)
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to republish notification", error)
                }
                flushSupabase()
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

    private fun flushSupabase() {
        val sync = supabaseNetworkSync
        if (sync != null) {
            sync.requestFlush()
            return
        }
        serviceScope.launch {
            try {
                when (val outcome = supabasePublisher.pushPending(
                    loadPage = { limit -> repository.unsyncedNotifications(limit) },
                    markSynced = { ids -> repository.markSupabaseSynced(ids) },
                    markExcluded = { ids -> repository.markSupabaseExcluded(ids) }
                )) {
                    is SupabaseOutcome.Failure ->
                        Log.w(TAG, "Supabase push failed status=${outcome.statusCode}")
                    else -> Unit
                }
            } catch (error: Exception) {
                Log.e(TAG, "Supabase push failed", error)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NotificationListener"
    }
}
