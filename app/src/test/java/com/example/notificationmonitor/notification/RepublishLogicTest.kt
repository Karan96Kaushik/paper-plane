package com.example.notificationmonitor.notification

import com.example.notificationmonitor.database.NotificationEntity
import com.example.notificationmonitor.database.RepublishRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepublishLogicTest {

    @Test
    fun allTypesMatchEveryCategoryExceptOngoingByDefault() {
        val rule = RepublishRuleEntity(packageName = "com.chat", enabled = true, allTypes = true)
        assertTrue(RepublishMatcher.matches(sample(category = "msg"), rule, requireEnabled = true))
        assertTrue(RepublishMatcher.matches(sample(category = null), rule, requireEnabled = true))
        assertTrue(RepublishMatcher.matches(sample(category = "custom"), rule, requireEnabled = true))
        assertFalse(
            RepublishMatcher.matches(
                sample(category = "msg", ongoing = true),
                rule,
                requireEnabled = true
            )
        )
        assertTrue(
            RepublishMatcher.matches(
                sample(category = "msg", ongoing = true),
                rule.copy(includeOngoing = true),
                requireEnabled = true
            )
        )
    }

    @Test
    fun selectedTypesMatchOnlyThoseCategories() {
        val rule = RepublishRuleEntity(packageName = "com.chat", enabled = true)
            .withSelection(setOf(NotificationType.MESSAGE, NotificationType.EMAIL))
        assertTrue(RepublishMatcher.matches(sample(category = "msg"), rule, requireEnabled = true))
        assertTrue(RepublishMatcher.matches(sample(category = "email"), rule, requireEnabled = true))
        assertFalse(RepublishMatcher.matches(sample(category = "alarm"), rule, requireEnabled = true))
        assertFalse(RepublishMatcher.matches(sample(category = null), rule, requireEnabled = true))
        assertFalse(RepublishMatcher.matches(sample(category = "weird"), rule, requireEnabled = true))
    }

    @Test
    fun pastRepublishCanMatchWhenAutomaticRepublishIsOff() {
        val rule = RepublishRuleEntity(packageName = "com.chat", enabled = false, allTypes = true)
        assertFalse(RepublishMatcher.matches(sample(), rule, requireEnabled = true))
        assertTrue(RepublishMatcher.matches(sample(), rule, requireEnabled = false))
    }

    @Test
    fun emptySelectionMatchesNothing() {
        val rule = RepublishRuleEntity(packageName = "com.chat", enabled = true)
        assertFalse(RepublishMatcher.matches(sample(), rule, requireEnabled = false))
    }

    @Test
    fun differentPackageDoesNotMatch() {
        val rule = RepublishRuleEntity(packageName = "com.other", enabled = true, allTypes = true)
        assertFalse(RepublishMatcher.matches(sample(), rule, requireEnabled = false))
    }

    @Test
    fun togglingAllTypesDropsASingleType() {
        val all = RepublishRuleEntity(packageName = "com.chat", allTypes = true)
        val next = all.toggleType(NotificationType.MESSAGE)
        assertFalse(next.allTypes)
        assertFalse(NotificationType.MESSAGE in next.selectedTypes())
        assertTrue(NotificationType.EMAIL in next.selectedTypes())
    }

    @Test
    fun contentUsesTitleBodyAndAppName() {
        val content = RepublishContent.from(
            sample(title = "John", text = "Hello", appName = "Chat")
        )
        assertEquals("John", content?.title)
        assertEquals("Hello", content?.message)
        assertEquals("Chat", content?.subText)
    }

    @Test
    fun blankNotificationHasNoContent() {
        assertNull(
            RepublishContent.from(
                sample(title = " ", text = null, subText = null, bigText = null)
            )
        )
    }

    @Test
    fun batchMessageCoversEmptyAndPermissionCases() {
        assertEquals(
            "No matching notifications to republish.",
            RepublishBatchResult().userMessage()
        )
        assertEquals(
            "Cannot post notifications. Grant notification permission in system settings.",
            RepublishBatchResult(permissionDenied = true).userMessage()
        )
        assertEquals(
            "Republished 2, 1 already republished",
            RepublishBatchResult(posted = 2, skippedAlready = 1).userMessage()
        )
    }

    private fun sample(
        packageName: String = "com.chat",
        title: String? = "Title",
        text: String? = "Body",
        subText: String? = null,
        bigText: String? = null,
        category: String? = "msg",
        appName: String? = "Chat",
        ongoing: Boolean = false
    ) = NotificationEntity(
        packageName = packageName,
        appName = appName,
        title = title,
        text = text,
        subText = subText,
        bigText = bigText,
        category = category,
        notificationKey = "key",
        postedAt = 1L,
        receivedAt = 1L,
        isOngoing = ongoing,
        isClearable = !ongoing
    )
}
