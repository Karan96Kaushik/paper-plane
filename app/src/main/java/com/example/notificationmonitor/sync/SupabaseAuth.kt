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
        if (projectError != null) {
            SupabaseLog.signInBlocked("project: $projectError")
            return AuthAttempt.Failure(projectError)
        }
        val normalizedEmail = SupabaseConfig.normalizeEmail(email)
        if (normalizedEmail == null) {
            SupabaseLog.signInBlocked("invalid email")
            return AuthAttempt.Failure("Enter the account email.")
        }
        val normalizedPassword = SupabaseConfig.normalizeSignInPassword(password)
        if (normalizedPassword == null) {
            SupabaseLog.signInBlocked(
                when {
                    password.isEmpty() -> "password missing"
                    password.trim().isEmpty() -> "password whitespace only"
                    password.any { it.isISOControl() } -> "password has control characters"
                    else -> "password invalid"
                }
            )
            return AuthAttempt.Failure(SupabaseConfig.signInPasswordError(password))
        }
        val url = config.normalizedUrl() ?: return AuthAttempt.Failure("Enter the Supabase project URL.")
        val publishableKey = config.normalizedPublishableKey()
            ?: return AuthAttempt.Failure("Enter the publishable key.")
        val response = transport.post(
            url = "$url/auth/v1/token?grant_type=password",
            publishableKey = publishableKey,
            body = jsonObject(
                "email" to normalizedEmail,
                "password" to normalizedPassword
            ),
            authorizationBearer = config.authAuthorizationBearer(),
            preferMinimal = false
        )
        if (!response.isSuccessful) {
            SupabaseLog.authHttpFailed(response.statusCode, "password")
            return AuthAttempt.Failure(signInFailure(response))
        }
        val session = parseSession(response.body, nowMillis)
        if (session == null) {
            SupabaseLog.unexpectedAuthResponse("password grant body could not be parsed")
            return AuthAttempt.Failure("Supabase returned an unexpected sign-in response.")
        }
        return AuthAttempt.Success(session.copy(email = session.email.ifBlank { normalizedEmail }))
    }

    fun refresh(config: SupabaseConfig, nowMillis: Long): AuthAttempt {
        val session = config.session()
        if (session == null) {
            SupabaseLog.sessionRefreshBlocked("no saved session")
            return AuthAttempt.Failure(config.validationError() ?: "Sign in again.")
        }
        val url = config.normalizedUrl() ?: return AuthAttempt.Failure("Enter the Supabase project URL.")
        val publishableKey = config.normalizedPublishableKey()
            ?: return AuthAttempt.Failure("Enter the publishable key.")
        val response = transport.post(
            url = "$url/auth/v1/token?grant_type=refresh_token",
            publishableKey = publishableKey,
            body = jsonObject("refresh_token" to session.refreshToken),
            authorizationBearer = config.authAuthorizationBearer(),
            preferMinimal = false
        )
        if (!response.isSuccessful) {
            SupabaseLog.sessionRefreshHttpFailed(response.statusCode)
            return AuthAttempt.Failure("Sign in again. The saved session expired.")
        }
        val refreshed = parseSession(response.body, nowMillis)
        if (refreshed == null) {
            SupabaseLog.unexpectedAuthResponse("refresh grant body could not be parsed")
            return AuthAttempt.Failure("Sign in again. The saved session expired.")
        }
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
