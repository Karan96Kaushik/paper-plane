package com.barontech.paperplane.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["postedAt"]),
        Index(value = ["packageName"]),
        Index(value = ["notificationKey"])
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String?,
    val title: String?,
    val text: String?,
    val subText: String?,
    val bigText: String?,
    val category: String?,
    val notificationKey: String?,
    val postedAt: Long,
    val receivedAt: Long,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val republishedAt: Long? = null,
    val supabaseSyncedAt: Long? = null,
    val supabaseExcludedAt: Long? = null
)
