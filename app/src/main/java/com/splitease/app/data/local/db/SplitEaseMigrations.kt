package com.splitease.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Incremental Room migrations for [SplitEaseDatabase] (exported schemas 1–4).
 *
 * Replaces destructive upgrade for users moving between historical versions.
 */
object SplitEaseMigrations {
    /** Adds `invites` table + indices (Phase 3b). */
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `invites` (
                        `id` TEXT NOT NULL,
                        `token` TEXT NOT NULL,
                        `inviterUserId` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `groupId` TEXT,
                        `friendRowId` TEXT,
                        `status` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_invites_token` ON `invites` (`token`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_invites_email` ON `invites` (`email`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_invites_inviterUserId` ON `invites` (`inviterUserId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_invites_status` ON `invites` (`status`)",
                )
            }
        }

    /** Adds `groups.groupType` (Phase 3 UI types). */
    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `groups` ADD COLUMN `groupType` TEXT NOT NULL DEFAULT 'OTHER'",
                )
            }
        }

    /** Adds recurring schedule columns on `expenses` (Phase 6). */
    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `expenses` ADD COLUMN `nextOccurrenceEpochMs` INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE `expenses` ADD COLUMN `recurringTemplateId` TEXT",
                )
            }
        }

    /** All migrations from version 1 through [SplitEaseDatabase] version 4. */
    val ALL =
        arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        )
}
