package com.example.notificationmonitor

import android.app.Application
import com.example.notificationmonitor.notification.BackgroundMonitor
import com.example.notificationmonitor.notification.LocalNotificationManager
import com.example.notificationmonitor.notification.NotificationRepublisher
import com.example.notificationmonitor.repository.NotificationRepository
import com.example.notificationmonitor.work.RetentionCleanupWorker
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

    override fun onCreate() {
        super.onCreate()
        repository = NotificationRepository.getInstance(this)
        localNotificationManager = LocalNotificationManager(this)
        localNotificationManager.ensureChannel()
        republisher = NotificationRepublisher(
            repository = repository,
            poster = localNotificationManager
        )
        BackgroundMonitor.ensureRunning(this)
        BackgroundMonitor.rebindListener(this)
        RetentionCleanupWorker.schedule(this)

        applicationScope.launch {
            runCatching { repository.syncInstalledApps() }
        }
    }
}
