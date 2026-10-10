package com.barontech.paperplane.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NotificationEntity::class,
        MonitoredAppEntity::class,
        RepublishRuleEntity::class,
        WorkflowEntity::class,
        SupabaseExclusionRuleEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun republishRuleDao(): RepublishRuleDao
    abstract fun workflowDao(): WorkflowDao
    abstract fun supabaseExclusionRuleDao(): SupabaseExclusionRuleDao

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
                    .addMigrations(
                        DatabaseMigrations.MIGRATION_1_2,
                        DatabaseMigrations.MIGRATION_2_3,
                        DatabaseMigrations.MIGRATION_3_4,
                        DatabaseMigrations.MIGRATION_4_5
                    )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
