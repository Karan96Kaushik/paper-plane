package com.barontech.paperplane.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.barontech.paperplane.sync.SupabaseExclusionRule
import com.barontech.paperplane.sync.SupabaseFilterField

@Entity(tableName = "supabase_exclusion_rules")
data class SupabaseExclusionRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val enabled: Boolean = true,
    val matchField: String,
    val pattern: String
) {
    fun toRule(): SupabaseExclusionRule? {
        val field = SupabaseFilterField.fromStorage(matchField) ?: return null
        return SupabaseExclusionRule(
            id = id,
            enabled = enabled,
            field = field,
            pattern = pattern
        )
    }

    companion object {
        fun create(field: SupabaseFilterField, pattern: String): SupabaseExclusionRuleEntity =
            SupabaseExclusionRuleEntity(
                enabled = true,
                matchField = field.name,
                pattern = pattern.trim()
            )
    }
}
