package com.barontech.paperplane.notification

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import com.barontech.paperplane.database.NotificationEntity

/**
 * Converts [StatusBarNotification] into [NotificationEntity].
 * Must never throw for malformed third-party notification extras.
 */
class NotificationParser(
    private val context: Context
) {

    fun parse(sbn: StatusBarNotification): NotificationEntity? {
        return try {
            val notification = sbn.notification ?: return null
            val extras = notification.extras

            NotificationEntity(
                packageName = sbn.packageName.orEmpty(),
                appName = resolveAppName(sbn.packageName),
                title = extras.safeCharSequence(Notification.EXTRA_TITLE),
                text = extras.safeCharSequence(Notification.EXTRA_TEXT),
                subText = extras.safeCharSequence(Notification.EXTRA_SUB_TEXT),
                bigText = extras.safeCharSequence(Notification.EXTRA_BIG_TEXT),
                category = notification.category,
                notificationKey = sbn.key,
                postedAt = sbn.postTime,
                receivedAt = System.currentTimeMillis(),
                isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
                isClearable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    sbn.isClearable
                } else {
                    notification.flags and Notification.FLAG_NO_CLEAR == 0
                }
            )
        } catch (error: Exception) {
            Log.e(TAG, "Failed to parse notification from package=${sbn.packageName}", error)
            null
        }
    }

    private fun resolveAppName(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(info)?.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun android.os.Bundle.safeCharSequence(key: String): String? {
        return try {
            getCharSequence(key)?.toString()
        } catch (_: Exception) {
            try {
                getString(key)
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        private const val TAG = "NotificationParser"
    }
}
