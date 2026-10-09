package com.example.notificationmonitor.sync

import com.example.notificationmonitor.settings.SupabaseConfig
import com.example.notificationmonitor.settings.SupabaseSession

internal class SupabaseAuth(
    private val transport: SupabaseTransport
) {
    fun signIn(
        config: SupabaseConfig,
        email: String,
        password: String,
        nowMillis: Long
    ): AuthAttempt {
        val projectError = config.projectError()
        if (projectError != null) return AuthAttempt.Failure(projectError)
        val normalizedEmail = SupabaseConfig.normalizeEmail(email)
            ?: return AuthAttempt.Failure("Enter the account email.")
        if (password.isEmpty() || password.any { it.isISOControl() }) {
            return AuthAttempt.Failure("Enter the password.")
        }
        val url = config.normalizedUrl() ?: return AuthAttempt.Failure("Enter the Supabase project URL.")
        val apiKey = config.normalizedApiKey() ?: return AuthAttempt.Failure("Enter the anon key.")
        val response = transport.post(
            url = "$url/auth/v1/token?grant_type=password",
            apiKey = apiKey,
            bearerToken = apiKey,
            body = jsonObject(
                "email" to normalizedEmail,
                "password" to password
            ),
            preferMinimal = false
        )
        if (!response.isSuccessful) {
            return AuthAttempt.Failure(signInFailure(response))
        }
        val session = parseSession(response.body, nowMillis)
            ?: return AuthAttempt.Failure("Supabase returned an unexpected sign-in response.")
        return AuthAttempt.Success(session.copy(email = session.email.ifBlank { normalizedEmail }))
    }

    fun refresh(config: SupabaseConfig, nowMillis: Long): AuthAttempt {
        val session = config.session()
            ?: return AuthAttempt.Failure("Sign in with the Supabase user that should receive notifications.")
        val url = config.normalizedUrl() ?: return AuthAttempt.Failure("Enter the Supabase project URL.")
        val apiKey = config.normalizedApiKey() ?: return AuthAttempt.Failure("Enter the anon key.")
        val response = transport.post(
            url = "$url/auth/v1/token?grant_type=refresh_token",
            apiKey = apiKey,
            bearerToken = apiKey,
            body = jsonObject("refresh_token" to session.refreshToken),
            preferMinimal = false
        )
        if (!response.isSuccessful) {
            return AuthAttempt.Failure("Sign in again. The saved session expired.")
        }
        val refreshed = parseSession(response.body, nowMillis)
            ?: return AuthAttempt.Failure("Sign in again. The saved session expired.")
        return AuthAttempt.Success(
            refreshed.copy(email = refreshed.email.ifBlank { session.email })
        )
    }

    private fun signInFailure(response: SupabaseHttpResponse): String {
        return when (response.statusCode) {
            0 -> "Could not reach Supabase."
            400, 401 -> "Email or password was rejected."
            else -> SupabasePublisher.failureMessage(response.statusCode, response.body)
        }
    }

    companion object {
        internal fun parseSession(body: String?, nowMillis: Long): SupabaseSession? {
            val fields = body?.let(::parseJsonObject) ?: return null
            val access = (fields["access_token"] as? JsonValue.Str)?.value ?: return null
            val refresh = (fields["refresh_token"] as? JsonValue.Str)?.value ?: return null
            val user = (fields["user"] as? JsonValue.Obj)?.fields ?: return null
            val userId = (user["id"] as? JsonValue.Str)?.value ?: return null
            if (SupabaseConfig.normalizedSecret(access) == null) return null
            if (SupabaseConfig.normalizedSecret(refresh) == null) return null
            val normalizedUser = userId.trim()
            if (!USER_ID_PATTERN.matches(normalizedUser)) return null
            val email = (user["email"] as? JsonValue.Str)?.value?.trim().orEmpty()
            return SupabaseSession(
                userId = normalizedUser,
                email = email,
                accessToken = access.trim(),
                refreshToken = refresh.trim(),
                expiresAtMillis = expiresAtMillis(fields, nowMillis)
            )
        }

        private fun expiresAtMillis(fields: Map<String, JsonValue>, nowMillis: Long): Long {
            val expiresAt = (fields["expires_at"] as? JsonValue.Num)?.value?.toLongOrNull()
            if (expiresAt != null) {
                return if (expiresAt > 10_000_000_000L) expiresAt else expiresAt * 1000
            }
            val expiresIn = (fields["expires_in"] as? JsonValue.Num)?.value?.toLongOrNull()
            if (expiresIn != null) return nowMillis + expiresIn * 1000
            return nowMillis + 3_600_000
        }

        private val USER_ID_PATTERN =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}

internal sealed class AuthAttempt {
    data class Success(val session: SupabaseSession) : AuthAttempt()
    data class Failure(val message: String) : AuthAttempt()
}
