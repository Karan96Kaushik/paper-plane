package com.example.notificationmonitor.sync

import com.example.notificationmonitor.settings.SupabaseConfig
import com.example.notificationmonitor.settings.SupabaseSession
import java.util.Base64

internal sealed class AuthSessionParse {
    data class Success(val session: SupabaseSession) : AuthSessionParse()
    data class Failure(val userMessage: String, val logDetail: String) : AuthSessionParse()
}

internal fun parseAuthSession(
    body: String?,
    nowMillis: Long,
    fallbackEmail: String = ""
): AuthSessionParse {
    if (body.isNullOrBlank()) {
        return AuthSessionParse.Failure(
            userMessage = "Supabase returned an empty sign-in response.",
            logDetail = "empty body"
        )
    }
    val trimmed = body.trim().removePrefix("\uFEFF")
    if (trimmed.startsWith("<")) {
        return AuthSessionParse.Failure(
            userMessage = "Could not read Supabase auth response. Check the project URL.",
            logDetail = "html response prefix=${trimmed.take(40)}"
        )
    }
    val fields = parseJsonObject(trimmed)
    if (fields == null) {
        return AuthSessionParse.Failure(
            userMessage = "Supabase returned a response this app could not read.",
            logDetail = "json parse failed preview=${authBodyPreview(trimmed)}"
        )
    }
    authJsonUserMessage(fields)?.let { message ->
        if (stringField(tokenFieldMap(fields), "access_token") == null) {
            return AuthSessionParse.Failure(
                userMessage = message,
                logDetail = "auth error json preview=${authBodyPreview(trimmed)}"
            )
        }
    }
    if (isPendingEmailConfirmation(fields)) {
        return AuthSessionParse.Failure(
            userMessage = "Confirm your email in Supabase, then sign in again.",
            logDetail = "email confirmation required user=${stringField(fields, "email")}"
        )
    }
    when (stringField(fields, "error_code")) {
        "email_not_confirmed" -> return AuthSessionParse.Failure(
            userMessage = "Confirm your email in Supabase, then sign in again.",
            logDetail = "error_code=email_not_confirmed"
        )
        "mfa_challenge_not_verified", "insufficient_aal" -> return AuthSessionParse.Failure(
            userMessage = "This account requires multi-factor authentication, which this app does not support yet.",
            logDetail = "error_code=${stringField(fields, "error_code")}"
        )
    }
    return buildSessionFromTokenFields(tokenFieldMap(fields), nowMillis, fallbackEmail)
        ?: AuthSessionParse.Failure(
            userMessage = buildUnexpectedSignInMessage(fields),
            logDetail = "missing session fields preview=${authBodyPreview(trimmed)}"
        )
}

private fun buildUnexpectedSignInMessage(fields: Map<String, JsonValue>): String {
    val extra = authJsonUserMessage(fields)
    val base = "Supabase did not return a sign-in session. If email confirmation is enabled, confirm your email first."
    return if (extra == null) base else "$base $extra"
}

private fun tokenFieldMap(fields: Map<String, JsonValue>): Map<String, JsonValue> {
    val nested = fields["session"] as? JsonValue.Obj
    return nested?.fields ?: fields
}

private fun buildSessionFromTokenFields(
    fields: Map<String, JsonValue>,
    nowMillis: Long,
    fallbackEmail: String
): AuthSessionParse.Success? {
    val access = stringField(fields, "access_token") ?: return null
    val refresh = stringField(fields, "refresh_token") ?: return null
    if (SupabaseConfig.normalizedSecret(access) == null) return null
    if (SupabaseConfig.normalizedSecret(refresh) == null) return null

    val user = (fields["user"] as? JsonValue.Obj)?.fields
    val userId = user?.let { stringField(it, "id") }?.trim()
        ?: jwtClaim(access, "sub")
    if (userId == null || !USER_ID_PATTERN.matches(userId)) return null

    val email = user?.let { stringField(it, "email") }?.trim().orEmpty()
        .ifBlank { jwtClaim(access, "email").orEmpty() }
        .ifBlank { fallbackEmail.trim() }

    return AuthSessionParse.Success(
        SupabaseSession(
            userId = userId,
            email = email,
            accessToken = access.trim(),
            refreshToken = refresh.trim(),
            expiresAtMillis = expiresAtMillis(fields, nowMillis)
        )
    )
}

private fun isPendingEmailConfirmation(fields: Map<String, JsonValue>): Boolean {
    if (stringField(tokenFieldMap(fields), "access_token") != null) return false
    val id = stringField(fields, "id") ?: return false
    val email = stringField(fields, "email") ?: return false
    return USER_ID_PATTERN.matches(id.trim()) && email.isNotBlank()
}

internal fun authJsonUserMessage(fields: Map<String, JsonValue>): String? {
    listOf("msg", "message", "error_description", "error").forEach { key ->
        stringField(fields, key)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

internal fun authBodyPreview(body: String, maxLen: Int = 220): String {
    val redacted = body
        .replace(Regex(""""(access_token|refresh_token)"\s*:\s*"[^"]*""""), """"$1":"…"""")
        .replace(Regex("\\s+"), " ")
        .trim()
    return redacted.take(maxLen)
}

private fun stringField(fields: Map<String, JsonValue>, key: String): String? {
    return (fields[key] as? JsonValue.Str)?.value
}

private fun jwtClaim(accessToken: String, claim: String): String? {
    val parts = accessToken.split('.')
    if (parts.size < 2) return null
    val payload = parts[1]
    val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
    val bytes = try {
        Base64.getUrlDecoder().decode(padded)
    } catch (_: IllegalArgumentException) {
        return null
    }
    val claims = parseJsonObject(String(bytes, Charsets.UTF_8)) ?: return null
    return stringField(claims, claim)
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
