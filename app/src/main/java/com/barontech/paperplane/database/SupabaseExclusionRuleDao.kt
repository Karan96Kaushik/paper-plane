package com.barontech.paperplane.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SupabaseExclusionRuleDao {

    @Query("SELECT * FROM supabase_exclusion_rules ORDER BY id ASC")
    fun observeAll(): Flow<List<SupabaseExclusionRuleEntity>>

    @Query("SELECT * FROM supabase_exclusion_rules WHERE enabled = 1 ORDER BY id ASC")
    suspend fun getEnabled(): List<SupabaseExclusionRuleEntity>

    @Query("SELECT * FROM supabase_exclusion_rules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SupabaseExclusionRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: SupabaseExclusionRuleEntity): Long

    @Query("DELETE FROM supabase_exclusion_rules WHERE id = :id")
    suspend fun delete(id: Long)
}
