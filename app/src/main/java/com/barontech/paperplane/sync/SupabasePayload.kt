package com.barontech.paperplane.sync

import com.barontech.paperplane.database.NotificationEntity

internal fun notificationPayload(
    notification: NotificationEntity,
    deviceId: String,
    userId: String
): String {
    return jsonObject(
        "local_id" to notification.id,
        "user_id" to userId,
        "device_id" to deviceId,
        "package_name" to notification.packageName,
        "app_name" to notification.appName,
        "title" to notification.title,
        "text" to notification.text,
        "sub_text" to notification.subText,
        "big_text" to notification.bigText,
        "category" to notification.category,
        "notification_key" to notification.notificationKey,
        "posted_at" to notification.postedAt,
        "received_at" to notification.receivedAt,
        "is_ongoing" to notification.isOngoing,
        "is_clearable" to notification.isClearable
    )
}

internal fun notificationPayloadArray(
    notifications: List<NotificationEntity>,
    deviceId: String,
    userId: String
): String {
    return notifications.joinToString(prefix = "[", postfix = "]") { notification ->
        notificationPayload(notification, deviceId, userId)
    }
}

internal const val DEVICE_TOKENS_TABLE = "device_tokens"
internal const val DEVICE_TOKEN_UPSERT_PREFER = "resolution=merge-duplicates,return=minimal"

internal fun normalizedFcmToken(raw: String): String? {
    val value = raw.trim()
    if (value.length !in 32..4096) return null
    if (value.any { it.isISOControl() }) return null
    return value
}

internal fun normalizedDeviceId(raw: String): String? {
    val value = raw.trim()
    if (value.length !in 1..200) return null
    if (value.any { it.isISOControl() }) return null
    return value
}

internal fun deviceTokenPayload(
    userId: String,
    token: String,
    deviceId: String?,
    updatedAt: String
): String {
    return jsonObject(
        "user_id" to userId,
        "token" to token,
        "platform" to "android",
        "device_id" to deviceId,
        "updated_at" to updatedAt
    )
}

internal fun jsonObject(vararg fields: Pair<String, Any?>): String {
    return fields.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "${jsonString(key)}:${jsonValue(value)}"
    }
}

internal fun jsonValue(value: Any?): String = when (value) {
    null -> "null"
    is Boolean -> if (value) "true" else "false"
    is Int -> value.toString()
    is Long -> value.toString()
    is String -> jsonString(value)
    else -> throw IllegalArgumentException("Unsupported JSON value ${value.javaClass.name}")
}

internal fun jsonString(value: String): String {
    val out = StringBuilder(value.length + 2)
    out.append('"')
    value.forEach { char ->
        when (char) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (char.code < 0x20) {
                out.append("\\u")
                out.append(char.code.toString(16).padStart(4, '0'))
            } else {
                out.append(char)
            }
        }
    }
    out.append('"')
    return out.toString()
}
