package com.barontech.paperplane.notification

import com.barontech.paperplane.database.NotificationEntity
import com.barontech.paperplane.database.RepublishRuleEntity

object RepublishMatcher {

    fun matches(
        notification: NotificationEntity,
        rule: RepublishRuleEntity,
        requireEnabled: Boolean
    ): Boolean {
        if (notification.packageName != rule.packageName) return false
        if (requireEnabled && !rule.enabled) return false
        if (notification.isOngoing && !rule.includeOngoing) return false
        if (rule.allTypes) return true
        val selected = rule.selectedTypes()
        if (selected.isEmpty()) return false
        return NotificationType.fromCategory(notification.category) in selected
    }
}
