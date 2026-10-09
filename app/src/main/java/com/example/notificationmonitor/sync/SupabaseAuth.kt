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
            authorizationBearer = config.authRequestAuthorizationBearer(),
            preferMinimal = false
        )
        if (!response.isSuccessful) {
            SupabaseLog.authHttpFailed(
                response.statusCode,
                "password",
                authBodyPreview(response.body.orEmpty())
            )
            return AuthAttempt.Failure(signInFailure(response))
        }
        return when (
            val parsed = parseAuthSession(response.body, nowMillis, normalizedEmail)
        ) {
            is AuthSessionParse.Success ->
                AuthAttempt.Success(parsed.session.copy(email = parsed.session.email.ifBlank { normalizedEmail }))
            is AuthSessionParse.Failure -> {
                SupabaseLog.unexpectedAuthResponse(
                    "password grant status=${response.statusCode} bytes=${response.body?.length ?: 0} ${parsed.logDetail}"
                )
                AuthAttempt.Failure(parsed.userMessage)
            }
        }
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
            authorizationBearer = config.authRequestAuthorizationBearer(),
            preferMinimal = false
        )
        if (!response.isSuccessful) {
            SupabaseLog.sessionRefreshHttpFailed(
                response.statusCode,
                authBodyPreview(response.body.orEmpty())
            )
            return AuthAttempt.Failure(refreshFailure(response))
        }
        return when (val parsed = parseAuthSession(response.body, nowMillis, session.email)) {
            is AuthSessionParse.Success -> AuthAttempt.Success(
                parsed.session.copy(email = parsed.session.email.ifBlank { session.email })
            )
            is AuthSessionParse.Failure -> {
                SupabaseLog.unexpectedAuthResponse("refresh grant: ${parsed.logDetail}")
                AuthAttempt.Failure(parsed.userMessage.ifBlank { "Sign in again. The saved session expired." })
            }
        }
    }

    private fun signInFailure(response: SupabaseHttpResponse): String {
        response.body?.let(::parseJsonObject)?.let(::authJsonUserMessage)?.let { detail ->
            SupabaseLog.signInBlocked("auth rejected (HTTP ${response.statusCode}): $detail")
        }
        return when (response.statusCode) {
            0 -> "Could not reach Supabase."
            400, 401 -> "Email or password was rejected."
            else -> SupabasePublisher.failureMessage(response.statusCode, response.body)
        }
    }

    private fun refreshFailure(response: SupabaseHttpResponse): String {
        val detail = response.body?.let(::parseJsonObject)?.let(::authJsonUserMessage)
        return detail ?: "Sign in again. The saved session expired."
    }

    companion object {
        internal fun parseSession(body: String?, nowMillis: Long): SupabaseSession? {
            return when (val parsed = parseAuthSession(body, nowMillis)) {
                is AuthSessionParse.Success -> parsed.session
                is AuthSessionParse.Failure -> null
            }
        }
    }
}

internal sealed class AuthAttempt {
    data class Success(val session: SupabaseSession) : AuthAttempt()
    data class Failure(val message: String) : AuthAttempt()
}
