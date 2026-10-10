package com.barontech.paperplane.sync

import com.barontech.paperplane.database.NotificationEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardMatcherTest {

    @Test
    fun exactMatchIgnoresCase() {
        assertTrue(WildcardMatcher.matches("Delivery", "delivery"))
        assertFalse(WildcardMatcher.matches("Deliveries", "delivery"))
    }

    @Test
    fun starMatchesAnySequence() {
        assertTrue(WildcardMatcher.matches("Your OTP is 1234", "*otp*"))
        assertTrue(WildcardMatcher.matches("Promo ends today", "promo*"))
        assertTrue(WildcardMatcher.matches("Daily digest", "*digest"))
        assertFalse(WildcardMatcher.matches("Hello", "*otp*"))
    }

    @Test
    fun questionMarkMatchesOneCharacter() {
        assertTrue(WildcardMatcher.matches("a1c", "a?c"))
        assertFalse(WildcardMatcher.matches("ac", "a?c"))
        assertFalse(WildcardMatcher.matches("abbc", "a?c"))
    }

    @Test
    fun literalCharactersAreNotRegex() {
        assertTrue(WildcardMatcher.matches("a.b", "a.b"))
        assertFalse(WildcardMatcher.matches("axb", "a.b"))
        assertTrue(WildcardMatcher.matches("100%", "100%"))
    }

    @Test
    fun blankPatternNeverMatches() {
        assertFalse(WildcardMatcher.matches("anything", "   "))
        assertFalse(WildcardMatcher.matches(null, ""))
    }

    @Test
    fun starMatchesAMissingField() {
        assertTrue(WildcardMatcher.matches(null, "*"))
        assertFalse(WildcardMatcher.matches(null, "chat"))
    }

    @Test
    fun ruleUsesTheSelectedField() {
        val notification = sample(
            title = "Hello",
            text = "code 42",
            subText = "Inbox",
            category = "msg",
            appName = "Chat"
        )
        assertTrue(rule(SupabaseFilterField.TITLE, "hello").matches(notification))
        assertTrue(rule(SupabaseFilterField.TEXT, "*42").matches(notification))
        assertTrue(rule(SupabaseFilterField.SUB_TEXT, "in*").matches(notification))
        assertTrue(rule(SupabaseFilterField.CATEGORY, "msg").matches(notification))
        assertTrue(rule(SupabaseFilterField.APP_NAME, "c?at").matches(notification))
        assertFalse(rule(SupabaseFilterField.TITLE, "*42").matches(notification))
        assertFalse(rule(SupabaseFilterField.TEXT, "hello", enabled = false).matches(notification))
    }

    @Test
    fun anyEnabledRuleExcludes() {
        val notification = sample(title = "Ship notice", appName = "Mail")
        val rules = listOf(
            rule(SupabaseFilterField.TITLE, "*invoice*"),
            rule(SupabaseFilterField.APP_NAME, "mail")
        )
        assertTrue(rules.excludes(notification))
        assertFalse(listOf(rule(SupabaseFilterField.TITLE, "*invoice*")).excludes(notification))
    }

    private fun rule(
        field: SupabaseFilterField,
        pattern: String,
        enabled: Boolean = true
    ) = SupabaseExclusionRule(id = 1, enabled = enabled, field = field, pattern = pattern)

    private fun sample(
        title: String? = null,
        text: String? = null,
        subText: String? = null,
        category: String? = null,
        appName: String? = null
    ) = NotificationEntity(
        packageName = "com.chat",
        appName = appName,
        title = title,
        text = text,
        subText = subText,
        bigText = null,
        category = category,
        notificationKey = null,
        postedAt = 1,
        receivedAt = 1,
        isOngoing = false,
        isClearable = true
    )
}
