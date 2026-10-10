package com.barontech.paperplane.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import com.barontech.paperplane.notification.NotificationListener
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

object NotificationAccessHelper {

    fun isNotificationAccessEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val component = ComponentName(context, NotificationListener::class.java)
        val flattened = component.flattenToString()
        val alt = component.flattenToShortString()

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(flat)
        while (splitter.hasNext()) {
            val next = splitter.next()
            if (next.equals(flattened, ignoreCase = true) ||
                next.equals(alt, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    fun openNotificationListenerSettings(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openBatteryOptimizationSettings(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}

object TimeFormatter {

    private val timeFormat: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    private val dateTimeFormat: DateFormat =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    fun formatTime(millis: Long): String = timeFormat.format(Date(millis))

    fun formatDateTime(millis: Long): String = dateTimeFormat.format(Date(millis))

    fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - millis
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                "$minutes minute${if (minutes == 1L) "" else "s"} ago"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "$hours hour${if (hours == 1L) "" else "s"} ago"
            }
            else -> formatDateTime(millis)
        }
    }
}
