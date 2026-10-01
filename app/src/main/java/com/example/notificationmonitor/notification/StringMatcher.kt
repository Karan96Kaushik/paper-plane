package com.example.notificationmonitor.notification

import com.example.notificationmonitor.database.NotificationEntity

enum class MatchField(val label: String) {
    ANY("Title or text"),
    TITLE("Title"),
    TEXT("Text");

    fun values(notification: NotificationEntity): List<String> = when (this) {
        TITLE -> listOfNotNull(notification.title)
        TEXT -> listOfNotNull(notification.text, notification.bigText, notification.subText)
        ANY -> listOfNotNull(
            notification.title,
            notification.text,
            notification.bigText,
            notification.subText
        )
    }
}

enum class MatchMode(val label: String) {
    CONTAINS("Contains"),
    EQUALS("Equals"),
    STARTS_WITH("Starts with"),
    ENDS_WITH("Ends with"),
    REGEX("Regular expression")
}

enum class WorkflowAction(val label: String) {
    REPUBLISH("Republish"),
    SKIP("Don't republish")
}

object StringMatcher {

    fun matches(
        values: List<String>,
        pattern: String,
        mode: MatchMode,
        caseSensitive: Boolean
    ): Boolean {
        val needle = pattern.trim()
        if (needle.isEmpty()) return true
        val candidates = values.map { it.trim() }.filter { it.isNotEmpty() }
        if (candidates.isEmpty()) return false
        return candidates.any { matchesValue(it, needle, mode, caseSensitive) }
    }

    fun regexError(pattern: String): String? {
        val needle = pattern.trim()
        if (needle.isEmpty()) return null
        return try {
            Regex(needle)
            null
        } catch (_: Exception) {
            "This regular expression is not valid."
        }
    }

    private fun matchesValue(
        value: String,
        pattern: String,
        mode: MatchMode,
        caseSensitive: Boolean
    ): Boolean {
        if (mode == MatchMode.REGEX) {
            return try {
                val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(pattern, options).containsMatchIn(value)
            } catch (_: Exception) {
                false
            }
        }
        val left = if (caseSensitive) value else value.lowercase()
        val right = if (caseSensitive) pattern else pattern.lowercase()
        return when (mode) {
            MatchMode.CONTAINS -> left.contains(right)
            MatchMode.EQUALS -> left == right
            MatchMode.STARTS_WITH -> left.startsWith(right)
            MatchMode.ENDS_WITH -> left.endsWith(right)
            MatchMode.REGEX -> false
        }
    }
}
