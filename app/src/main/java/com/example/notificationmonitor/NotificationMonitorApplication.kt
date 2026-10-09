package com.example.notificationmonitor

import android.app.Application
import com.example.notificationmonitor.notification.BackgroundMonitor
import com.example.notificationmonitor.notification.LocalNotificationManager
import com.example.notificationmonitor.notification.NotificationRepublisher
import com.example.notificationmonitor.repository.NotificationRepository
import com.example.notificationmonitor.settings.UserPreferences
import com.example.notificationmonitor.sync.SupabaseNetworkSync
import com.example.notificationmonitor.sync.SupabasePublisher
import com.example.notificationmonitor.sync.supabaseDeviceId
import com.example.notificationmonitor.work.RetentionCleanupWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationMonitorApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: NotificationRepository
        private set

    lateinit var localNotificationManager: LocalNotificationManager
        private set

    lateinit var republisher: NotificationRepublisher
        private set

    lateinit var supabasePublisher: SupabasePublisher
        private set

    lateinit var supabaseNetworkSync: SupabaseNetworkSync
        private set

    override fun onCreate() {
        super.onCreate()
        repository = NotificationRepository.getInstance(this)
        localNotificationManager = LocalNotificationManager(this)
        localNotificationManager.ensureChannel()
        republisher = NotificationRepublisher(
            repository = repository,
            poster = localNotificationManager
        )
        val preferences = UserPreferences(this)
        supabasePublisher = SupabasePublisher(
            currentConfig = { preferences.supabaseConfig.first() },
            deviceId = supabaseDeviceId(this),
            onSessionUpdated = { updated -> repository.replaceSupabaseConfig(updated) }
        )
        supabaseNetworkSync = SupabaseNetworkSync(
            context = this,
            publisher = supabasePublisher,
            repository = repository,
            scope = applicationScope
        )
        supabaseNetworkSync.start()
        BackgroundMonitor.ensureRunning(this)
        BackgroundMonitor.rebindListener(this)
        RetentionCleanupWorker.schedule(this)

        applicationScope.launch {
            runCatching { repository.syncInstalledApps() }
        }
    }
}
