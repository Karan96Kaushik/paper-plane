package com.barontech.paperplane.notification

import android.util.Log
import com.barontech.paperplane.NotificationMonitorApplication
import com.barontech.paperplane.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives Firebase Cloud Messaging pushes and posts them on the push channel.
 *
 * Notification payloads are shown by the system when the app is in the background.
 * This service shows them when the app is in the foreground, and always shows data-only messages.
 */
class PaperPlaneMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val app = application
        if (app is NotificationMonitorApplication) {
            app.syncFcmToken(token)
        } else {
            Log.i(TAG, "FCM registration token: $token")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
        if (title.isNullOrBlank() && body.isNullOrBlank()) {
            Log.i(TAG, "FCM message ${message.messageId} had no displayable content")
            return
        }
        val shown = LocalNotificationManager(this).showPushNotification(
            title = title?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name),
            message = body.orEmpty(),
            notificationId = notificationId(message)
        )
        if (!shown) {
            Log.w(TAG, "FCM message not posted; notification permission is off")
        }
    }

    private fun notificationId(message: RemoteMessage): Int {
        val key = message.messageId ?: "${message.sentTime}:${message.data.hashCode()}"
        return key.hashCode() and Int.MAX_VALUE
    }

    companion object {
        private const val TAG = "PaperPlaneFcm"
    }
}
