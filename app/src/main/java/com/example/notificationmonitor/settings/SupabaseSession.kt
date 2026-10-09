package com.example.notificationmonitor.settings

data class SupabaseSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
) {
    fun refreshDue(nowMillis: Long): Boolean {
        return expiresAtMillis <= 0L || nowMillis >= expiresAtMillis - REFRESH_SKEW_MILLIS
    }

    companion object {
        private const val REFRESH_SKEW_MILLIS = 60_000L
    }
}
