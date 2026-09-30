package com.example.notificationmonitor.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Query("SELECT * FROM notifications ORDER BY postedAt DESC")
    fun observeNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY postedAt DESC LIMIT :limit")
    fun observeNotifications(limit: Int): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY postedAt DESC")
    fun observeNotificationsByPackage(packageName: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY postedAt DESC LIMIT :limit")
    fun observeNotificationsByPackage(packageName: String, limit: Int): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE id = :id")
    fun observeById(id: Long): Flow<NotificationEntity?>

    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun getById(id: Long): NotificationEntity?

    @Query("SELECT COUNT(*) FROM notifications")
    fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM notifications
        WHERE postedAt >= :startOfDayMillis
        """
    )
    fun observeCountSince(startOfDayMillis: Long): Flow<Int>

    @Query("SELECT DISTINCT packageName FROM notifications ORDER BY packageName ASC")
    fun observeDistinctPackageNames(): Flow<List<String>>

    @Query("SELECT * FROM notifications ORDER BY postedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getNotificationsPaged(limit: Int, offset: Int): List<NotificationEntity>

    @Query(
        """
        SELECT * FROM notifications
        WHERE packageName = :packageName
        ORDER BY postedAt DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getNotificationsByPackagePaged(
        packageName: String,
        limit: Int,
        offset: Int
    ): List<NotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity): Long

    @Delete
    suspend fun delete(notification: NotificationEntity)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("DELETE FROM notifications WHERE postedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    @Query("UPDATE notifications SET republishedAt = :republishedAt WHERE id = :id")
    suspend fun markRepublished(id: Long, republishedAt: Long)

    @Query(
        """
        SELECT COUNT(*) FROM notifications
        WHERE notificationKey = :notificationKey
          AND republishedAt IS NOT NULL
          AND republishedAt >= :since
        """
    )
    suspend fun countRepublishedKeySince(notificationKey: String, since: Long): Int

    @Query(
        """
        SELECT * FROM notifications
        WHERE packageName = :packageName
        ORDER BY postedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentByPackage(packageName: String, limit: Int): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<NotificationEntity>
}
