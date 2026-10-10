package com.barontech.paperplane.notification

import com.barontech.paperplane.database.NotificationEntity
import com.barontech.paperplane.database.RepublishRuleEntity
import com.barontech.paperplane.database.WorkflowEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowLogicTest {

    @Test
    fun stringMatchCoversContainsEqualsAffixesAndRegex() {
        val notification = sample(title = "Invoice 42", text = "Payment due")
        assertTrue(matches(notification, pattern = "invoice"))
        assertTrue(matches(notification, pattern = "Invoice 42", mode = MatchMode.EQUALS, field = MatchField.TITLE))
        assertFalse(matches(notification, pattern = "Invoice 42", mode = MatchMode.EQUALS, field = MatchField.TEXT))
        assertTrue(matches(notification, pattern = "pay", mode = MatchMode.STARTS_WITH, field = MatchField.TEXT))
        assertTrue(matches(notification, pattern = "due", mode = MatchMode.ENDS_WITH, field = MatchField.TEXT))
        assertTrue(matches(notification, pattern = "Invoice \\d+", mode = MatchMode.REGEX, field = MatchField.TITLE))
        assertFalse(matches(notification, pattern = "[", mode = MatchMode.REGEX))
        assertTrue(matches(notification, pattern = "   "))
    }

    @Test
    fun caseSensitiveMatchCanRejectADifferentCase() {
        val notification = sample(title = "Hello")
        assertFalse(
            matches(notification, pattern = "hello", field = MatchField.TITLE, caseSensitive = true)
        )
        assertTrue(
            matches(notification, pattern = "hello", field = MatchField.TITLE, caseSensitive = false)
        )
    }

    @Test
    fun workflowFiltersByAppTypeAndOngoing() {
        val message = sample(packageName = "com.chat", category = "msg")
        val email = sample(packageName = "com.mail", category = "email", title = "Hello")
        val ongoing = sample(ongoing = true, title = "Hello")
        val chatOnly = workflow(packageName = "com.chat", pattern = "Hello")
        assertTrue(WorkflowEngine.matches(message, chatOnly))
        assertFalse(WorkflowEngine.matches(email, chatOnly))
        assertFalse(WorkflowEngine.matches(ongoing, chatOnly))
        assertTrue(WorkflowEngine.matches(ongoing, chatOnly.copy(includeOngoing = true)))

        val emails = workflow(allTypes = false, categoriesCsv = NotificationType.EMAIL.storageKey)
        assertFalse(WorkflowEngine.matches(message, emails))
        assertTrue(WorkflowEngine.matches(email, emails))
    }

    @Test
    fun skipWorkflowBlocksAppRulesAndOtherWorkflows() {
        val notification = sample(title = "Code 123")
        val rule = RepublishRuleEntity(packageName = "com.chat", enabled = true, allTypes = true)
        val republish = workflow(pattern = "code", titlePrefix = "Work")
        val skip = workflow(pattern = "123", action = WorkflowAction.SKIP.name)
        val plan = WorkflowEngine.decide(notification, rule, listOf(republish, skip))
        assertFalse(plan.republish)
    }

    @Test
    fun republishWorkflowSuppliesPrefixWithoutAnAppRule() {
        val notification = sample(title = "Invoice", category = "email")
        val workflow = workflow(pattern = "invoice", titlePrefix = " Bills ")
        val plan = WorkflowEngine.decide(notification, appRule = null, workflows = listOf(workflow))
        assertTrue(plan.republish)
        assertEquals("Bills", plan.titlePrefix)
    }

    @Test
    fun appRuleStillRepublishesWhenNoWorkflowMatches() {
        val notification = sample(title = "Ping", category = "msg")
        val rule = RepublishRuleEntity(
            packageName = "com.chat",
            enabled = true,
            allTypes = false,
            categoriesCsv = "msg"
        )
        val plan = WorkflowEngine.decide(
            notification,
            rule,
            listOf(workflow(pattern = "invoice"))
        )
        assertTrue(plan.republish)
        assertEquals("", plan.titlePrefix)
    }

    private fun matches(
        notification: NotificationEntity,
        pattern: String,
        mode: MatchMode = MatchMode.CONTAINS,
        field: MatchField = MatchField.ANY,
        caseSensitive: Boolean = false
    ) = StringMatcher.matches(field.values(notification), pattern, mode, caseSensitive)

    private fun workflow(
        packageName: String = "",
        allTypes: Boolean = true,
        categoriesCsv: String = "",
        pattern: String = "",
        action: String = WorkflowAction.REPUBLISH.name,
        titlePrefix: String = "",
        includeOngoing: Boolean = false
    ) = WorkflowEntity(
        name = "Workflow",
        enabled = true,
        packageName = packageName,
        allTypes = allTypes,
        categoriesCsv = categoriesCsv,
        includeOngoing = includeOngoing,
        pattern = pattern,
        action = action,
        titlePrefix = titlePrefix
    )

    private fun sample(
        packageName: String = "com.chat",
        title: String? = "Hello",
        text: String? = "Body",
        category: String? = "msg",
        ongoing: Boolean = false
    ) = NotificationEntity(
        packageName = packageName,
        appName = "Chat",
        title = title,
        text = text,
        subText = null,
        bigText = null,
        category = category,
        notificationKey = "key",
        postedAt = 1L,
        receivedAt = 1L,
        isOngoing = ongoing,
        isClearable = !ongoing
    )
}
