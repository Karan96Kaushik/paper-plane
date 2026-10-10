package com.barontech.paperplane.sync

import com.barontech.paperplane.database.NotificationEntity

enum class SupabaseFilterField(val label: String) {
    TITLE("Title"),
    TEXT("Text"),
    SUB_TEXT("Sub text"),
    CATEGORY("Category"),
    APP_NAME("App name");

    fun read(notification: NotificationEntity): String? = when (this) {
        TITLE -> notification.title
        TEXT -> notification.text
        SUB_TEXT -> notification.subText
        CATEGORY -> notification.category
        APP_NAME -> notification.appName
    }

    companion object {
        fun fromStorage(value: String): SupabaseFilterField? =
            entries.firstOrNull { it.name == value }
    }
}

data class SupabaseExclusionRule(
    val id: Long,
    val enabled: Boolean,
    val field: SupabaseFilterField,
    val pattern: String
) {
    fun matches(notification: NotificationEntity): Boolean {
        if (!enabled) return false
        return WildcardMatcher.matches(field.read(notification), pattern)
    }
}

fun List<SupabaseExclusionRule>.excludes(notification: NotificationEntity): Boolean =
    any { it.matches(notification) }

/**
 * Whole-value wildcard match. `*` is any sequence of characters, `?` is one character.
 * Matching ignores case. A blank pattern never matches.
 */
object WildcardMatcher {
    fun matches(value: String?, pattern: String): Boolean {
        val needle = pattern.trim()
        if (needle.isEmpty()) return false
        return globRegex(needle).matches(value.orEmpty())
    }

    private fun globRegex(pattern: String): Regex {
        val body = buildString {
            pattern.forEach { character ->
                when (character) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(character.toString()))
                }
            }
        }
        return Regex(body, RegexOption.IGNORE_CASE)
    }
}
