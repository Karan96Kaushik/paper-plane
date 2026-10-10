package com.barontech.paperplane.database

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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `workflows` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `packageName` TEXT NOT NULL,
                    `allTypes` INTEGER NOT NULL,
                    `categoriesCsv` TEXT NOT NULL,
                    `includeOngoing` INTEGER NOT NULL,
                    `matchField` TEXT NOT NULL,
                    `matchMode` TEXT NOT NULL,
                    `pattern` TEXT NOT NULL,
                    `caseSensitive` INTEGER NOT NULL,
                    `action` TEXT NOT NULL,
                    `titlePrefix` TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `notifications` ADD COLUMN `supabaseSyncedAt` INTEGER"
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `notifications` ADD COLUMN `supabaseExcludedAt` INTEGER"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `supabase_exclusion_rules` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `matchField` TEXT NOT NULL,
                    `pattern` TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }
}
