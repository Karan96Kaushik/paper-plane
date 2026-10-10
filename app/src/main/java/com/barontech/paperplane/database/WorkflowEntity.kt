package com.barontech.paperplane.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.barontech.paperplane.notification.MatchField
import com.barontech.paperplane.notification.MatchMode
import com.barontech.paperplane.notification.NotificationType
import com.barontech.paperplane.notification.WorkflowAction

@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val packageName: String = "",
    val allTypes: Boolean = true,
    val categoriesCsv: String = "",
    val includeOngoing: Boolean = false,
    val matchField: String = MatchField.ANY.name,
    val matchMode: String = MatchMode.CONTAINS.name,
    val pattern: String = "",
    val caseSensitive: Boolean = false,
    val action: String = WorkflowAction.REPUBLISH.name,
    val titlePrefix: String = ""
) {
    fun field(): MatchField =
        MatchField.entries.firstOrNull { it.name == matchField } ?: MatchField.ANY

    fun mode(): MatchMode =
        MatchMode.entries.firstOrNull { it.name == matchMode } ?: MatchMode.CONTAINS

    fun action(): WorkflowAction =
        WorkflowAction.entries.firstOrNull { it.name == action } ?: WorkflowAction.REPUBLISH

    fun selectedTypes(): Set<NotificationType> {
        if (allTypes) return NotificationType.entries.toSet()
        if (categoriesCsv.isBlank()) return emptySet()
        val keys = categoriesCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return NotificationType.entries.filter { it.storageKey in keys }.toSet()
    }

    fun matchesType(category: String?): Boolean {
        if (allTypes) return true
        val selected = selectedTypes()
        if (selected.isEmpty()) return false
        return NotificationType.fromCategory(category) in selected
    }

    fun withSelection(types: Set<NotificationType>): WorkflowEntity {
        val ordered = NotificationType.entries.filter { it in types }
        val all = ordered.size == NotificationType.entries.size
        return copy(
            allTypes = all,
            categoriesCsv = if (all) "" else ordered.joinToString(",") { it.storageKey }
        )
    }

    fun toggleType(type: NotificationType): WorkflowEntity {
        val current = selectedTypes()
        val next = if (type in current) current - type else current + type
        return withSelection(next)
    }

    fun summary(appLabel: String?): String {
        val target = if (packageName.isBlank()) "Any app" else appLabel ?: packageName
        val match = if (pattern.isBlank()) {
            "any text"
        } else {
            "${mode().label.lowercase()} \"$pattern\""
        }
        val verb = action().label
        return "$verb · $target · $match"
    }
}
