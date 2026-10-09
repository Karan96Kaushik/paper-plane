package com.example.notificationmonitor.settings

import java.net.URI

data class SupabaseConfig(
    val enabled: Boolean = false,
    val projectUrl: String = "",
    val publishableKey: String = "",
    val table: String = DEFAULT_TABLE,
    val email: String = "",
    val accountEmail: String = "",
    val userId: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val accessTokenExpiresAt: Long = 0L
) {
    fun normalizedUrl(): String? = normalizeProjectUrl(projectUrl)

    fun normalizedTable(): String? {
        val value = table.trim().ifBlank { DEFAULT_TABLE }
        return value.takeIf { TABLE_PATTERN.matches(it) }
    }

    fun normalizedPublishableKey(): String? {
        val value = publishableKey.trim()
        if (value.isEmpty() || value.any { it.isISOControl() }) return null
        if (value.startsWith("sb_secret_", ignoreCase = true)) return null
        return value
    }

    /** Legacy JWT-based anon keys still need Bearer on auth token requests. */
    fun authAuthorizationBearer(): String? {
        val key = normalizedPublishableKey() ?: return null
        return key.takeIf { isLegacyJwtApiKey(it) }
    }

    /** Project URL plus table. Null when either value is not usable. */
    fun destination(): String? {
        val url = normalizedUrl() ?: return null
        val tableName = normalizedTable() ?: return null
        return "$url/rest/v1/$tableName"
    }

    fun normalizedUserId(): String? {
        val value = userId.trim()
        return value.takeIf { USER_ID_PATTERN.matches(it) }
    }

    fun normalizedEmail(): String? = normalizeEmail(email)

    fun session(): SupabaseSession? {
        val id = normalizedUserId() ?: return null
        val access = normalizedSecret(accessToken) ?: return null
        val refresh = normalizedSecret(refreshToken) ?: return null
        return SupabaseSession(
            userId = id,
            email = accountEmail.trim().ifBlank { email.trim() },
            accessToken = access,
            refreshToken = refresh,
            expiresAtMillis = accessTokenExpiresAt
        )
    }

    fun withSession(session: SupabaseSession): SupabaseConfig = copy(
        email = session.email.ifBlank { email },
        accountEmail = session.email.ifBlank { accountEmail },
        userId = session.userId,
        accessToken = session.accessToken,
        refreshToken = session.refreshToken,
        accessTokenExpiresAt = session.expiresAtMillis
    )

    fun clearedSession(): SupabaseConfig = copy(
        accountEmail = "",
        userId = "",
        accessToken = "",
        refreshToken = "",
        accessTokenExpiresAt = 0L
    )

    fun isReady(): Boolean = enabled && validationError() == null

    fun projectError(): String? {
        if (projectUrl.isBlank()) {
            return "Enter the Supabase project URL."
        }
        if (normalizedUrl() == null) {
            return "Use an https project URL like https://your-project.supabase.co."
        }
        if (publishableKey.isBlank()) {
            return "Enter the publishable key."
        }
        if (publishableKey.trim().startsWith("sb_secret_", ignoreCase = true)) {
            return "Use the publishable key (sb_publishable_...), not a secret key."
        }
        if (normalizedPublishableKey() == null) {
            return "Enter the publishable key."
        }
        if (normalizedTable() == null) {
            return "Table name can only use letters, numbers, and underscores."
        }
        return null
    }

    /**
     * Why this config cannot push a notification.
     * Enabling the switch is not required; callers check [enabled] separately.
     */
    fun validationError(): String? {
        projectError()?.let { return it }
        if (session() == null) {
            return "Sign in with the Supabase user that should receive notifications."
        }
        return null
    }

    companion object {
        const val DEFAULT_TABLE = "notifications"
        private val TABLE_PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
        private val USER_ID_PATTERN =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun normalizeEmail(raw: String): String? {
            val value = raw.trim()
            if (value.length > 320 || value.any { it.isWhitespace() || it.isISOControl() }) return null
            val at = value.indexOf('@')
            if (at <= 0 || at != value.lastIndexOf('@') || at >= value.lastIndex) return null
            return value
        }

        fun normalizedSecret(raw: String): String? {
            val value = raw.trim()
            if (value.isEmpty() || value.any { it.isISOControl() }) return null
            return value
        }

        fun isLegacyJwtApiKey(key: String): Boolean {
            return key.startsWith("eyJ") && key.count { it == '.' } >= 2
        }

        fun normalizeProjectUrl(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val uri = try {
                URI(trimmed)
            } catch (_: Exception) {
                return null
            }
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (uri.host.isNullOrBlank()) return null
            if (uri.userInfo != null) return null
            if (!uri.rawQuery.isNullOrEmpty() || !uri.rawFragment.isNullOrEmpty()) return null
            val path = uri.path.orEmpty().trim('/')
            if (path.isNotEmpty()) return null
            return trimmed
        }
    }
}
