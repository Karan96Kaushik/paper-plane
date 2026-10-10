package com.barontech.paperplane

import android.app.Application
import android.util.Log
import com.barontech.paperplane.notification.BackgroundMonitor
import com.barontech.paperplane.notification.LocalNotificationManager
import com.barontech.paperplane.notification.NotificationRepublisher
import com.barontech.paperplane.repository.NotificationRepository
import com.barontech.paperplane.settings.UserPreferences
import com.barontech.paperplane.sync.SupabaseNetworkSync
import com.barontech.paperplane.sync.SupabaseOutcome
import com.barontech.paperplane.sync.SupabasePublisher
import com.barontech.paperplane.sync.supabaseDeviceId
import com.barontech.paperplane.work.RetentionCleanupWorker
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

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
            onSessionUpdated = { updated -> repository.replaceSupabaseConfig(updated) },
            exclusionRules = { repository.enabledSupabaseExclusionRules() },
            registrationToken = { fcmRegistrationToken() }
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
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = task.result
            if (task.isSuccessful && !token.isNullOrBlank()) {
                syncFcmToken(token)
            } else {
                Log.w(TAG, "FCM token unavailable", task.exception)
            }
        }
    }

    fun syncFcmToken(token: String) {
        if (token.isBlank()) return
        Log.i(TAG, "FCM registration token: $token")
        applicationScope.launch {
            when (val outcome = supabasePublisher.upsertDeviceToken(token)) {
                is SupabaseOutcome.Failure ->
                    Log.w(TAG, "Device token was not saved: ${outcome.message}")
                is SupabaseOutcome.Success -> if (outcome.count > 0) {
                    Log.i(TAG, "Device token saved")
                }
                SupabaseOutcome.Disabled -> Unit
            }
        }
    }

    private suspend fun fcmRegistrationToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!cont.isActive) return@addOnCompleteListener
            val token = task.result
            if (task.isSuccessful && !token.isNullOrBlank()) {
                cont.resume(token)
            } else {
                Log.w(TAG, "FCM token unavailable", task.exception)
                cont.resume(null)
            }
        }
    }

    companion object {
        private const val TAG = "PaperPlaneFcm"
    }
}
