package com.barontech.paperplane.settings

enum class DedupeWindow(val millis: Long) {
    OFF(0L),
    THIRTY_SECONDS(30_000L),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(5 * 60_000L),
    FIFTEEN_MINUTES(15 * 60_000L);

    val label: String
        get() = when (this) {
            OFF -> "Off"
            THIRTY_SECONDS -> "30 seconds"
            ONE_MINUTE -> "1 minute"
            FIVE_MINUTES -> "5 minutes"
            FIFTEEN_MINUTES -> "15 minutes"
        }

    companion object {
        fun fromStorage(value: String?): DedupeWindow =
            entries.firstOrNull { it.name == value } ?: ONE_MINUTE
    }
}
