package com.example.notificationmonitor.notification

import com.example.notificationmonitor.database.NotificationEntity

data class RepublishContent(
    val title: String,
    val message: String,
    val bigText: String?,
    val subText: String?
) {
    companion object {
        fun from(entity: NotificationEntity): RepublishContent? {
            val title = entity.title?.trim()?.takeIf { it.isNotEmpty() }
            val body = entity.bigText?.trim()?.takeIf { it.isNotEmpty() }
                ?: entity.text?.trim()?.takeIf { it.isNotEmpty() }
            val sub = entity.subText?.trim()?.takeIf { it.isNotEmpty() }
            val app = entity.appName?.trim()?.takeIf { it.isNotEmpty() } ?: entity.packageName
            if (title == null && body == null && sub == null) return null

            val headline = title ?: body ?: sub ?: return null
            val message = when {
                title != null && body != null -> body
                title != null && sub != null -> sub
                body != null && title == null -> app
                else -> app
            }
            val big = listOfNotNull(title, body, sub)
                .distinct()
                .joinToString("\n")
                .takeIf { it.isNotBlank() }
            return RepublishContent(
                title = headline,
                message = message,
                bigText = big,
                subText = app
            )
        }
    }
}
