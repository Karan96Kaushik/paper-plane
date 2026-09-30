package com.example.notificationmonitor.notification

interface NotificationPoster {
    fun canPostNotifications(): Boolean

    fun showRepublished(
        entityId: Long,
        title: String,
        message: String,
        bigText: String?,
        subText: String?,
        category: String?
    ): Boolean
}
