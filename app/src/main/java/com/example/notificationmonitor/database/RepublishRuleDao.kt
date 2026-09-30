package com.example.notificationmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RepublishRuleDao {

    @Query("SELECT * FROM republish_rules")
    fun observeAll(): Flow<List<RepublishRuleEntity>>

    @Query("SELECT * FROM republish_rules WHERE packageName = :packageName LIMIT 1")
    fun observeByPackage(packageName: String): Flow<RepublishRuleEntity?>

    @Query("SELECT * FROM republish_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): RepublishRuleEntity?

    @Query("SELECT * FROM republish_rules WHERE enabled = 1")
    suspend fun getEnabled(): List<RepublishRuleEntity>

    @Query("SELECT COUNT(*) FROM republish_rules WHERE enabled = 1")
    fun observeEnabledCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RepublishRuleEntity)
}
