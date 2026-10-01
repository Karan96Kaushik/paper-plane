package com.example.notificationmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowDao {

    @Query("SELECT * FROM workflows ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<WorkflowEntity?>

    @Query("SELECT * FROM workflows WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WorkflowEntity?

    @Query("SELECT * FROM workflows WHERE enabled = 1 ORDER BY id ASC")
    suspend fun getEnabled(): List<WorkflowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workflow: WorkflowEntity): Long

    @Query("DELETE FROM workflows WHERE id = :id")
    suspend fun delete(id: Long)
}
