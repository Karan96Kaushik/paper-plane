package com.example.notificationmonitor.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NotificationEntity::class,
        MonitoredAppEntity::class,
        RepublishRuleEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun republishRuleDao(): RepublishRuleDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notification_monitor.db"
                )
                    .addMigrations(DatabaseMigrations.MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
