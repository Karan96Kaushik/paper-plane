package com.barontech.paperplane.notification

/**
 * Notification categories the user can select for republishing.
 * Storage keys match [android.app.Notification] category constants.
 */
enum class NotificationType(
    val storageKey: String,
    val label: String
) {
    MESSAGE("msg", "Messages"),
    EMAIL("email", "Email"),
    CALL("call", "Calls"),
    ALARM("alarm", "Alarms"),
    REMINDER("reminder", "Reminders"),
    EVENT("event", "Events"),
    SOCIAL("social", "Social"),
    PROMO("promo", "Promotions"),
    NAVIGATION("navigation", "Navigation"),
    TRANSPORT("transport", "Transport"),
    PROGRESS("progress", "Progress"),
    STATUS("status", "Status"),
    ERROR("err", "Errors"),
    SERVICE("service", "Service"),
    RECOMMENDATION("recommendation", "Recommendations"),
    SYSTEM("sys", "System"),
    UNCATEGORIZED("uncategorized", "Uncategorized"),
    OTHER("other", "Other");

    companion object {
        private val knownByKey = entries
            .filter { it != UNCATEGORIZED && it != OTHER }
            .associateBy { it.storageKey }

        fun fromCategory(category: String?): NotificationType {
            if (category.isNullOrBlank()) return UNCATEGORIZED
            return knownByKey[category] ?: OTHER
        }
    }
}
