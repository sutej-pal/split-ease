package com.splitease.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Incremental Room migrations for [SplitEaseDatabase] (exported schemas 1–5).
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

    /** Adds `activity_events` for expense create/update/delete feed entries. */
    val MIGRATION_4_5 =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_events` (
                        `id` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `subtitle` TEXT NOT NULL,
                        `amountLabel` TEXT NOT NULL,
                        `actorUserId` TEXT NOT NULL,
                        `relatedExpenseId` TEXT,
                        `involvedUserIds` TEXT NOT NULL,
                        `sortEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_activity_events_sortEpochMs` ON `activity_events` (`sortEpochMs`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_activity_events_relatedExpenseId` ON `activity_events` (`relatedExpenseId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_activity_events_actorUserId` ON `activity_events` (`actorUserId`)",
                )
            }
        }

    /** Adds phone + preferred currency on `users` (signup profile). */
    val MIGRATION_5_6 =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `users` ADD COLUMN `phoneCountryCode` TEXT")
                db.execSQL("ALTER TABLE `users` ADD COLUMN `phoneNumber` TEXT")
                db.execSQL("ALTER TABLE `users` ADD COLUMN `preferredCurrency` TEXT")
            }
        }

    /** Adds optional custom image path on `groups`. */
    val MIGRATION_6_7 =
        object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `photoUrl` TEXT")
            }
        }

    /** Adds optional header cover image path on `groups`. */
    val MIGRATION_7_8 =
        object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `coverUrl` TEXT")
            }
        }

    /** Adds optional multi-payer paid amount on expense splits. */
    val MIGRATION_8_9 =
        object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expense_splits` ADD COLUMN `paidAmount` TEXT")
            }
        }

    /** Adds optional adjustment amount on expense splits. */
    val MIGRATION_9_10 =
        object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expense_splits` ADD COLUMN `adjustmentAmount` TEXT")
            }
        }

    /** Adds expense comments + receipt photos (detail screen thread). */
    val MIGRATION_10_11 =
        object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expense_comments` (
                        `id` TEXT NOT NULL,
                        `expenseId` TEXT NOT NULL,
                        `authorUserId` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expense_comments_expenseId` ON `expense_comments` (`expenseId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expense_comments_createdAtEpochMs` ON `expense_comments` (`createdAtEpochMs`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expense_comments_syncStatus` ON `expense_comments` (`syncStatus`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expense_photos` (
                        `id` TEXT NOT NULL,
                        `expenseId` TEXT NOT NULL,
                        `createdByUserId` TEXT NOT NULL,
                        `localPath` TEXT,
                        `remoteUrl` TEXT,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expense_photos_expenseId` ON `expense_photos` (`expenseId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expense_photos_createdAtEpochMs` ON `expense_photos` (`createdAtEpochMs`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expense_photos_syncStatus` ON `expense_photos` (`syncStatus`)",
                )
            }
        }

    /** All migrations from version 1 through [SplitEaseDatabase] version 11. */
    val ALL =
        arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
        )
}
