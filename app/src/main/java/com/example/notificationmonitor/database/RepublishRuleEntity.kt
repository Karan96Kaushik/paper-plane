package com.example.notificationmonitor.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.notificationmonitor.notification.NotificationType

@Entity(tableName = "republish_rules")
data class RepublishRuleEntity(
    @PrimaryKey
    val packageName: String,
    val enabled: Boolean = false,
    val allTypes: Boolean = false,
    val categoriesCsv: String = "",
    val includeOngoing: Boolean = false
) {
    fun selectedTypes(): Set<NotificationType> {
        if (allTypes) return NotificationType.entries.toSet()
        if (categoriesCsv.isBlank()) return emptySet()
        val keys = categoriesCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return NotificationType.entries.filter { it.storageKey in keys }.toSet()
    }

    fun withSelection(types: Set<NotificationType>): RepublishRuleEntity {
        val ordered = NotificationType.entries.filter { it in types }
        val all = ordered.size == NotificationType.entries.size
        return copy(
            allTypes = all,
            categoriesCsv = if (all) "" else ordered.joinToString(",") { it.storageKey }
        )
    }

    fun toggleType(type: NotificationType): RepublishRuleEntity {
        val current = selectedTypes()
        val next = if (type in current) current - type else current + type
        return withSelection(next)
    }

    fun selectionSummary(): String {
        val types = when {
            allTypes -> "All types"
            else -> {
                val selected = selectedTypes()
                if (selected.isEmpty()) "No types selected" else selected.joinToString(", ") { it.label }
            }
        }
        val ongoing = if (includeOngoing) " · ongoing included" else ""
        return if (enabled) "$types$ongoing" else "Off · $types$ongoing"
    }
}
