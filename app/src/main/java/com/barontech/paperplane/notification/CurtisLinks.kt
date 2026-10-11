package com.barontech.paperplane.notification

import java.net.URI
import java.net.URLDecoder

sealed class CurtisDestination {
    data class InApp(val route: String) : CurtisDestination()
    data class External(val uri: String) : CurtisDestination()
    data class Unresolved(val raw: String?) : CurtisDestination()
}

object CurtisLinks {

    fun resolve(payload: CurtisPayload, action: CurtisActionId): CurtisDestination {
        val candidates = when (action) {
            CurtisActionId.OPEN -> listOfNotNull(
                payload.deepLink,
                payload.extras["link"],
                payload.extras["run_id"]?.trim()?.takeIf(::isSafeId)?.let { "/agents/runs/$it" }
            )
            CurtisActionId.OPEN_DRAFT -> listOfNotNull(
                payload.extras["link"],
                payload.deepLink
            )
            CurtisActionId.VIEW_ISSUE -> listOfNotNull(
                payload.deepLink,
                payload.extras["link"],
                payload.extras["url"],
                payload.extras["issue_url"]
            )
            CurtisActionId.SNOOZE, CurtisActionId.MARK_DONE -> emptyList()
        }
        if (action == CurtisActionId.OPEN && candidates.isEmpty()) {
            return CurtisDestination.InApp("home")
        }
        for (candidate in candidates) {
            resolveRaw(candidate)?.let { return it }
        }
        return CurtisDestination.Unresolved(candidates.firstOrNull())
    }

    private fun resolveRaw(raw: String): CurtisDestination? {
        val value = raw.trim()
        if (value.isEmpty() || value.any { it.isWhitespace() || it.isISOControl() }) return null
        val lower = value.lowercase()
        if (lower.startsWith("mailto:")) {
            val address = value.substringAfter("mailto:")
            if (address.isBlank()) return null
            return CurtisDestination.External(value)
        }
        if (value.startsWith("/")) {
            return inAppPath(value.substringBefore('?').substringBefore('#'))?.let(CurtisDestination::InApp)
        }
        if (!lower.startsWith("https://")) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (!uri.userInfo.isNullOrEmpty()) return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        if (host in APP_LINK_HOSTS) {
            return inAppPath(path)?.let(CurtisDestination::InApp)
        }
        if (host in EXTERNAL_HOSTS || isJiraHost(host)) {
            return CurtisDestination.External(value)
        }
        return null
    }

    private fun inAppPath(path: String): String? {
        val normalized = if (path.length > 1) path.trimEnd('/') else path
        if (normalized.isEmpty() || normalized == "/" || normalized == "/home") return "home"
        val parts = normalized.split('/').filter { it.isNotEmpty() }
        return when {
            parts == listOf("history") -> "history"
            parts == listOf("apps") -> "apps"
            parts == listOf("republish") -> "republish"
            parts == listOf("settings") -> "settings"
            parts == listOf("workflows") -> "workflows"
            parts.size == 2 && parts[0] == "workflow" ->
                parts[1].toLongOrNull()?.takeIf { it >= 0L }?.let { "workflow/$it" }
            parts.size == 2 && (parts[0] == "detail" || parts[0] == "notifications") ->
                parts[1].toLongOrNull()?.takeIf { it > 0L }?.let { "detail/$it" }
            parts.size == 3 && parts[0] == "agents" && parts[1] == "runs" ->
                safeDecoded(parts[2])?.let { "curtis_run/${encodeNavSegment(it)}" }
            parts.size == 2 && parts[0] == "chat" ->
                safeDecoded(parts[1])?.let { "curtis_chat/${encodeNavSegment(it)}" }
            else -> null
        }
    }

    private fun safeDecoded(segment: String): String? {
        val decoded = runCatching { URLDecoder.decode(segment, Charsets.UTF_8.name()) }.getOrNull()
            ?: return null
        return decoded.takeIf(::isSafeId)
    }

    private fun isJiraHost(host: String): Boolean {
        return host == "jira.atlassian.com" || host.endsWith(".atlassian.net")
    }

    companion object {
        private val APP_LINK_HOSTS = setOf("paperplane.app", "www.paperplane.app")
        private val EXTERNAL_HOSTS = setOf(
            "wa.me",
            "api.whatsapp.com",
            "www.whatsapp.com",
            "github.com",
            "www.github.com",
            "gitlab.com",
            "www.gitlab.com",
            "bitbucket.org"
        )
    }
}

internal fun isSafeId(value: String): Boolean {
    if (value.length !in 1..128) return false
    if (value == "." || value == "..") return false
    return value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' || it == '~' }
}

internal fun encodeNavSegment(value: String): String {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val out = StringBuilder(bytes.size)
    for (byte in bytes) {
        val unsigned = byte.toInt() and 0xff
        val char = unsigned.toChar()
        if (char.isLetterOrDigit() || char == '-' || char == '_' || char == '.' || char == '~') {
            out.append(char)
        } else {
            out.append('%')
            out.append("0123456789ABCDEF"[unsigned shr 4])
            out.append("0123456789ABCDEF"[unsigned and 0x0f])
        }
    }
    return out.toString()
}
