package com.example.notificationmonitor.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    /**
     * Adds republish bookkeeping and per-app republish rules.
     * Column SQL is kept aligned with the Room schema for [NotificationEntity]
     * and [RepublishRuleEntity].
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `notifications` ADD COLUMN `republishedAt` INTEGER"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `republish_rules` (
                    `packageName` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `allTypes` INTEGER NOT NULL,
                    `categoriesCsv` TEXT NOT NULL,
                    `includeOngoing` INTEGER NOT NULL,
                    PRIMARY KEY(`packageName`)
                )
                """.trimIndent()
            )
        }
    }
}
