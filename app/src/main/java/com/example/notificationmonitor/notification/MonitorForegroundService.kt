package com.example.notificationmonitor.notification

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Keeps the process resident so notification capture and republish continue
 * after the UI is closed. The listener remains the source of notification events.
 */
class MonitorForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = LocalNotificationManager(this).buildBackgroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    companion object {
        const val NOTIFICATION_ID = 42
    }
}

object BackgroundMonitor {
    private const val TAG = "BackgroundMonitor"

    fun ensureRunning(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, MonitorForegroundService::class.java)
        try {
            ContextCompat.startForegroundService(appContext, intent)
        } catch (error: Exception) {
            Log.w(TAG, "Background monitor not started", error)
        }
    }

    fun rebindListener(context: Context) {
        try {
            NotificationListenerService.requestRebind(
                ComponentName(context, NotificationListener::class.java)
            )
        } catch (error: Exception) {
            Log.w(TAG, "requestRebind failed", error)
        }
    }
}
