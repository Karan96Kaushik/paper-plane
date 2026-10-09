package com.example.notificationmonitor.sync

import com.example.notificationmonitor.settings.SupabaseConfig
import com.example.notificationmonitor.settings.SupabaseSession
import org.json.JSONException
import org.json.JSONObject
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
    val json = try {
        JSONObject(trimmed)
    } catch (error: JSONException) {
        return AuthSessionParse.Failure(
            userMessage = "Supabase returned a response this app could not read.",
            logDetail = "json exception=${error.message} len=${trimmed.length} preview=${authBodyPreview(trimmed)}"
        )
    }
    authJsonUserMessage(json)?.let { message ->
        if (!json.hasNonBlank("access_token") && !tokenRoot(json).hasNonBlank("access_token")) {
            return AuthSessionParse.Failure(
                userMessage = message,
                logDetail = "auth error json preview=${authBodyPreview(trimmed)}"
            )
        }
    }
    if (isPendingEmailConfirmation(json)) {
        return AuthSessionParse.Failure(
            userMessage = "Confirm your email in Supabase, then sign in again.",
            logDetail = "email confirmation required user=${json.optString("email")}"
        )
    }
    when (json.optString("error_code")) {
        "email_not_confirmed" -> return AuthSessionParse.Failure(
            userMessage = "Confirm your email in Supabase, then sign in again.",
            logDetail = "error_code=email_not_confirmed"
        )
        "mfa_challenge_not_verified", "insufficient_aal" -> return AuthSessionParse.Failure(
            userMessage = "This account requires multi-factor authentication, which this app does not support yet.",
            logDetail = "error_code=${json.optString("error_code")}"
        )
    }
    return buildSessionFromTokenJson(tokenRoot(json), nowMillis, fallbackEmail)
        ?: AuthSessionParse.Failure(
            userMessage = buildUnexpectedSignInMessage(json),
            logDetail = "missing session fields len=${trimmed.length} preview=${authBodyPreview(trimmed)}"
        )
}

private fun buildUnexpectedSignInMessage(json: JSONObject): String {
    val extra = authJsonUserMessage(json)
    val base = "Supabase did not return a sign-in session. If email confirmation is enabled, confirm your email first."
    return if (extra == null) base else "$base $extra"
}

private fun tokenRoot(json: JSONObject): JSONObject {
    return json.optJSONObject("session") ?: json
}

private fun buildSessionFromTokenJson(
    json: JSONObject,
    nowMillis: Long,
    fallbackEmail: String
): AuthSessionParse.Success? {
    val access = json.optString("access_token").trim().takeIf { it.isNotEmpty() } ?: return null
    val refresh = json.optString("refresh_token").trim().takeIf { it.isNotEmpty() } ?: return null
    if (SupabaseConfig.normalizedSecret(access) == null) return null
    if (SupabaseConfig.normalizedSecret(refresh) == null) return null

    val user = json.optJSONObject("user")
    val userId = user?.optString("id")?.trim()?.takeIf { USER_ID_PATTERN.matches(it) }
        ?: jwtClaim(access, "sub")
    if (userId == null || !USER_ID_PATTERN.matches(userId)) return null

    val email = user?.optString("email")?.trim().orEmpty()
        .ifBlank { jwtClaim(access, "email").orEmpty() }
        .ifBlank { fallbackEmail.trim() }

    return AuthSessionParse.Success(
        SupabaseSession(
            userId = userId,
            email = email,
            accessToken = access,
            refreshToken = refresh,
            expiresAtMillis = expiresAtMillis(json, nowMillis)
        )
    )
}

private fun isPendingEmailConfirmation(json: JSONObject): Boolean {
    if (tokenRoot(json).hasNonBlank("access_token")) return false
    val id = json.optString("id").trim()
    val email = json.optString("email").trim()
    return id.isNotEmpty() && email.isNotEmpty() && USER_ID_PATTERN.matches(id)
}

internal fun authJsonUserMessage(json: JSONObject): String? {
    listOf("msg", "message", "error_description", "error").forEach { key ->
        json.optString(key).trim().takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

internal fun authJsonUserMessage(fields: Map<String, JsonValue>): String? {
    listOf("msg", "message", "error_description", "error").forEach { key ->
        (fields[key] as? JsonValue.Str)?.value?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
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

private fun JSONObject.hasNonBlank(key: String): Boolean {
    return optString(key).trim().isNotEmpty()
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
    return try {
        JSONObject(String(bytes, Charsets.UTF_8)).optString(claim).trim().takeIf { it.isNotEmpty() }
    } catch (_: JSONException) {
        null
    }
}

private fun expiresAtMillis(json: JSONObject, nowMillis: Long): Long {
    if (json.has("expires_at") && !json.isNull("expires_at")) {
        val expiresAt = json.optLong("expires_at")
        if (expiresAt > 0L) {
            return if (expiresAt > 10_000_000_000L) expiresAt else expiresAt * 1000
        }
    }
    if (json.has("expires_in") && !json.isNull("expires_in")) {
        val expiresIn = json.optLong("expires_in")
        if (expiresIn > 0L) return nowMillis + expiresIn * 1000
    }
    return nowMillis + 3_600_000
}

private val USER_ID_PATTERN =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
