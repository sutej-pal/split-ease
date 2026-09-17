package com.splitease.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Incremental Room migrations for [SplitEaseDatabase] (exported schemas 1–17).
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

    /** Adds currency conversion snapshot columns to `expenses` (Phase 7 FX). */
    val MIGRATION_14_15 =
        object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `originalAmount` TEXT")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `originalCurrencyCode` TEXT")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `rateToDefaultCurrency` TEXT")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `rateSource` TEXT")
            }
        }

    /** Adds sync and seen status to activity_events without rewriting existing rows. */
    val MIGRATION_15_16 =
        object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `remoteId` TEXT")
                db.execSQL(
                    "ALTER TABLE `activity_events` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'",
                )
                // Existing rows are already known to this device — do not badge history.
                db.execSQL(
                    "ALTER TABLE `activity_events` ADD COLUMN `isSeen` INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

    /** Adds snapshot fields to activity_events and removes foreign key constraint from expense_comments. */
    val MIGRATION_16_17 =
        object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `snapshotDescription` TEXT")
                db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `snapshotAmount` TEXT")
                db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `snapshotCurrency` TEXT")
                db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `snapshotGroupId` TEXT")
                db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `snapshotGroupName` TEXT")
                db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `snapshotCreatorUserId` TEXT")
                db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `snapshotCreatedAtEpochMs` INTEGER")
                db.execSQL("ALTER TABLE `activity_events` ADD COLUMN `snapshotParticipantUserIds` TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expense_comments_new` (
                        `id` TEXT NOT NULL,
                        `expenseId` TEXT NOT NULL,
                        `authorUserId` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT INTO `expense_comments_new` (`id`, `expenseId`, `authorUserId`, `body`, `kind`, `createdAtEpochMs`, `syncStatus`) SELECT `id`, `expenseId`, `authorUserId`, `body`, `kind`, `createdAtEpochMs`, `syncStatus` FROM `expense_comments`",
                )
                db.execSQL("DROP TABLE `expense_comments`")
                db.execSQL("ALTER TABLE `expense_comments_new` RENAME TO `expense_comments`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_comments_expenseId` ON `expense_comments` (`expenseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_comments_createdAtEpochMs` ON `expense_comments` (`createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_comments_syncStatus` ON `expense_comments` (`syncStatus`)")
            }
        }

    private val STABLE_DEFAULT_CATEGORIES =
        listOf(
            Triple("cat_general", "General", "category_general"),
            Triple("cat_food", "Food", "category_food"),
            Triple("cat_travel", "Travel", "category_travel"),
            Triple("cat_rent", "Rent", "category_rent"),
            Triple("cat_utilities", "Utilities", "category_utilities"),
            Triple("cat_entertainment", "Entertainment", "category_entertainment"),
        )

    /** All migrations from version 1 through [SplitEaseDatabase] version 17. */
    val ALL =
        arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
        )
}
