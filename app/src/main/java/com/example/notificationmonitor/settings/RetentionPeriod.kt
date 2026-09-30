package com.example.notificationmonitor.settings

enum class RetentionPeriod(val days: Int?) {
    ONE_DAY(1),
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    FOREVER(null);

    fun cutoffMillis(now: Long): Long? {
        val dayCount = days ?: return null
        return now - dayCount * 24L * 60L * 60L * 1000L
    }

    val label: String
        get() = when (this) {
            ONE_DAY -> "1 day"
            SEVEN_DAYS -> "7 days"
            THIRTY_DAYS -> "30 days"
            FOREVER -> "Forever"
        }

    companion object {
        fun fromStorage(value: String?): RetentionPeriod =
            entries.firstOrNull { it.name == value } ?: SEVEN_DAYS
    }
}
