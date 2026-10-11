package com.barontech.paperplane.notification

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime

enum class CurtisStyle {
    DEFAULT,
    BIG_TEXT,
    BIG_PICTURE,
    PROGRESS
}

enum class CurtisActionId(val wire: String) {
    OPEN("open"),
    SNOOZE("snooze"),
    MARK_DONE("mark_done"),
    OPEN_DRAFT("open_draft"),
    VIEW_ISSUE("view_issue");

    val opensUi: Boolean
        get() = this == OPEN || this == OPEN_DRAFT || this == VIEW_ISSUE

    companion object {
        fun fromWire(value: String?): CurtisActionId? {
            val wire = value?.trim().orEmpty()
            return entries.firstOrNull { it.wire == wire }
        }
    }
}

data class CurtisAction(
    val id: CurtisActionId,
    val label: String
)

data class CurtisPayload(
    val version: String,
    val title: String,
    val body: String,
    val subtitle: String?,
    val expandedBody: String?,
    val category: String?,
    val channel: String?,
    val channelId: String,
    val urgency: String,
    val silent: Boolean,
    val notificationKey: String?,
    val groupKey: String?,
    val deepLink: String?,
    val imageUrl: String?,
    val largeIconUrl: String?,
    val relatedNotificationId: String?,
    val whenMillis: Long?,
    val expiresAtMillis: Long?,
    val onlyAlertOnce: Boolean,
    val style: CurtisStyle,
    val progressCurrent: Int?,
    val progressMax: Int?,
    val progressIndeterminate: Boolean,
    val shortCriticalText: String?,
    val ongoing: Boolean,
    val requestPromoted: Boolean,
    val actions: List<CurtisAction>,
    val extras: Map<String, String>,
    val raw: Map<String, String>,
    val notificationId: Int
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val expiresAt = expiresAtMillis ?: return false
        return expiresAt <= nowMillis
    }

    companion object {
        const val VERSION = "1"
        const val CHANNEL_URGENT = "curtis_urgent"
        const val CHANNEL_DEFAULT = "curtis_default"
        const val CHANNEL_DIGEST = "curtis_digest"
        const val CHANNEL_PROGRESS = "curtis_progress"
        const val CHANNEL_QUIET = "curtis_quiet"

        val KNOWN_CHANNELS = setOf(
            CHANNEL_URGENT,
            CHANNEL_DEFAULT,
            CHANNEL_DIGEST,
            CHANNEL_PROGRESS,
            CHANNEL_QUIET
        )

        fun parse(data: Map<String, String>): CurtisPayload? {
            if (data["curtis_v"]?.trim() != VERSION) return null
            val title = data["curtis_title"]?.trim().orEmpty()
            val body = data["curtis_body"]?.trim().orEmpty()
            if (title.isEmpty() || body.isEmpty()) return null

            val silent = flag(data["curtis_silent"])
            val requestedChannel = data["curtis_channel_id"]?.trim().orEmpty()
            val channelId = when {
                silent -> CHANNEL_QUIET
                requestedChannel in KNOWN_CHANNELS -> requestedChannel
                else -> CHANNEL_DEFAULT
            }
            val style = when (data["curtis_style"]?.trim()) {
                "big_text" -> CurtisStyle.BIG_TEXT
                "big_picture" -> CurtisStyle.BIG_PICTURE
                "progress" -> CurtisStyle.PROGRESS
                else -> CurtisStyle.DEFAULT
            }
            val progressCurrent = data["curtis_progress_current"]?.trim()?.toIntOrNull()
            val progressMax = data["curtis_progress_max"]?.trim()?.toIntOrNull()
            val progressIndeterminate = style == CurtisStyle.PROGRESS && (
                flag(data["curtis_progress_indeterminate"]) ||
                    progressCurrent == null ||
                    progressCurrent < 0 ||
                    progressMax == null ||
                    progressMax < 1
                )
            val key = data["curtis_notification_key"]?.trim()?.takeIf { it.isNotEmpty() }
            val extras = data.filterKeys { !it.startsWith("curtis_") }
            val identity = listOf(
                title,
                body,
                data["curtis_deep_link"].orEmpty(),
                extras["run_id"].orEmpty(),
                extras["jobId"].orEmpty()
            ).joinToString("\u0000")

            return CurtisPayload(
                version = VERSION,
                title = title,
                body = body,
                subtitle = blankToNull(data["curtis_subtitle"]),
                expandedBody = blankToNull(data["curtis_expanded_body"]),
                category = androidCategory(data["curtis_category"]),
                channel = blankToNull(data["curtis_channel"]),
                channelId = channelId,
                urgency = when (data["curtis_urgency"]?.trim()) {
                    "low" -> "low"
                    "high" -> "high"
                    else -> "normal"
                },
                silent = silent,
                notificationKey = key,
                groupKey = blankToNull(data["curtis_group_key"]),
                deepLink = blankToNull(data["curtis_deep_link"]),
                imageUrl = httpsUrl(data["curtis_image_url"]),
                largeIconUrl = httpsUrl(data["curtis_large_icon_url"]),
                relatedNotificationId = blankToNull(data["curtis_related_notification_id"]),
                whenMillis = parseIsoMillis(data["curtis_when"]),
                expiresAtMillis = parseIsoMillis(data["curtis_expires_at"]),
                onlyAlertOnce = flag(data["curtis_only_alert_once"]),
                style = style,
                progressCurrent = progressCurrent?.takeIf { it >= 0 },
                progressMax = progressMax?.takeIf { it >= 1 },
                progressIndeterminate = progressIndeterminate,
                shortCriticalText = blankToNull(data["curtis_short_critical_text"])?.take(8),
                ongoing = flag(data["curtis_ongoing"]),
                requestPromoted = flag(data["curtis_request_promoted"]),
                actions = parseActions(data["curtis_actions"]),
                extras = extras,
                raw = data.toMap(),
                notificationId = notificationIdFor(key, identity)
            )
        }

        fun notificationIdFor(key: String?, identity: String): Int {
            val source = key?.takeIf { it.isNotBlank() } ?: identity
            val hash = source.hashCode() and Int.MAX_VALUE
            return if (hash == 0) 1 else hash
        }

        fun requestCode(notificationId: Int, action: String): Int {
            val hash = "$notificationId:$action".hashCode() and Int.MAX_VALUE
            return if (hash == 0) 1 else hash
        }

        fun summaryId(groupKey: String): Int {
            val hash = "curtis-summary:$groupKey".hashCode() and Int.MAX_VALUE
            return if (hash == 0) 1 else hash
        }
    }
}

data class CurtisCommand(
    val payload: CurtisPayload,
    val action: CurtisActionId
)

private fun parseActions(json: String?): List<CurtisAction> {
    if (json.isNullOrBlank()) return emptyList()
    return CurtisJson.parseActions(json)
        .mapNotNull { (id, label) ->
            val actionId = CurtisActionId.fromWire(id) ?: return@mapNotNull null
            val trimmed = label.trim()
            if (trimmed.isEmpty()) null else CurtisAction(actionId, trimmed.take(40))
        }
        .distinctBy { it.id }
        .take(3)
}

private fun androidCategory(raw: String?): String? {
    return when (raw?.trim()) {
        "msg", "email", "reminder", "progress", "recommendation", "status", "promo" -> raw.trim()
        null, "" -> null
        else -> "status"
    }
}

private fun flag(raw: String?): Boolean = raw?.trim() == "1"

private fun blankToNull(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

private fun httpsUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (!value.startsWith("https://", ignoreCase = true)) return null
    if (value.any { it.isWhitespace() || it.isISOControl() }) return null
    return value
}

internal fun parseIsoMillis(raw: String?): Long? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
}
