package com.barontech.paperplane.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {

    @Query("SELECT * FROM monitored_apps ORDER BY appName ASC, packageName ASC")
    fun observeAll(): Flow<List<MonitoredAppEntity>>

    @Query("SELECT * FROM monitored_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): MonitoredAppEntity?

    @Query("SELECT COUNT(*) FROM monitored_apps WHERE enabled = 1")
    fun observeEnabledCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: MonitoredAppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(app: MonitoredAppEntity)

    @Query("UPDATE monitored_apps SET enabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query("SELECT enabled FROM monitored_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun isEnabled(packageName: String): Boolean?
}
