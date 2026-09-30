package com.example.notificationmonitor.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts background monitoring after reboot and after an app update.
 * Notification access itself is still granted only by the system.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                BackgroundMonitor.ensureRunning(context)
                BackgroundMonitor.rebindListener(context)
            }
        }
    }
}
