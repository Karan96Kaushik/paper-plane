package com.barontech.paperplane.notification

import com.barontech.paperplane.database.NotificationEntity
import com.barontech.paperplane.database.RepublishRuleEntity
import com.barontech.paperplane.database.WorkflowEntity

data class RepublishPlan(
    val republish: Boolean,
    val titlePrefix: String = ""
)

object WorkflowEngine {

    fun matches(notification: NotificationEntity, workflow: WorkflowEntity): Boolean {
        if (workflow.packageName.isNotBlank() && workflow.packageName != notification.packageName) {
            return false
        }
        if (notification.isOngoing && !workflow.includeOngoing) return false
        if (!workflow.matchesType(notification.category)) return false
        return StringMatcher.matches(
            values = workflow.field().values(notification),
            pattern = workflow.pattern,
            mode = workflow.mode(),
            caseSensitive = workflow.caseSensitive
        )
    }

    /**
     * A matching "don't republish" workflow wins over app rules and republish workflows.
     * Otherwise the first matching republish workflow supplies an optional title prefix.
     * If no workflow matches, the per-app republish rule is used.
     */
    fun decide(
        notification: NotificationEntity,
        appRule: RepublishRuleEntity?,
        workflows: List<WorkflowEntity>
    ): RepublishPlan {
        val enabled = workflows.filter { it.enabled }
        if (enabled.any { it.action() == WorkflowAction.SKIP && matches(notification, it) }) {
            return RepublishPlan(republish = false)
        }
        val republishWorkflow = enabled.firstOrNull {
            it.action() == WorkflowAction.REPUBLISH && matches(notification, it)
        }
        if (republishWorkflow != null) {
            return RepublishPlan(
                republish = true,
                titlePrefix = republishWorkflow.titlePrefix.trim()
            )
        }
        val appMatches = appRule != null &&
            RepublishMatcher.matches(notification, appRule, requireEnabled = true)
        return RepublishPlan(republish = appMatches)
    }
}
