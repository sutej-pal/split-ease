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

    /**
     * Remaps legacy random default category ids to stable `cat_*` ids so co-members
     * share the same `expenses.category_id` over the wire.
     */
    val MIGRATION_11_12 =
        object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")
                STABLE_DEFAULT_CATEGORIES.forEach { (stableId, name, iconKey) ->
                    db.execSQL(
                        """
                        UPDATE expenses
                        SET categoryId = ?
                        WHERE categoryId IN (
                            SELECT id FROM categories
                            WHERE lower(name) = lower(?)
                              AND id != ?
                              AND isDefault = 1
                        )
                        """.trimIndent(),
                        arrayOf(stableId, name, stableId),
                    )
                    db.execSQL(
                        """
                        DELETE FROM categories
                        WHERE lower(name) = lower(?)
                          AND id != ?
                          AND isDefault = 1
                        """.trimIndent(),
                        arrayOf(name, stableId),
                    )
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO categories (id, name, iconKey, isDefault, syncStatus)
                        VALUES (?, ?, ?, 1, 'LOCAL_ONLY')
                        """.trimIndent(),
                        arrayOf(stableId, name, iconKey),
                    )
                }
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

    /** Drops unused `groups.coverUrl` (header cover photo feature removed). */
    val MIGRATION_12_13 =
        object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `groups_new` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `defaultCurrencyCode` TEXT NOT NULL,
                        `groupType` TEXT NOT NULL,
                        `photoUrl` TEXT,
                        `createdByUserId` TEXT NOT NULL,
                        `remoteId` TEXT,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `groups_new` (
                        `id`, `name`, `defaultCurrencyCode`, `groupType`, `photoUrl`,
                        `createdByUserId`, `remoteId`, `createdAtEpochMs`, `updatedAtEpochMs`,
                        `syncStatus`
                    )
                    SELECT
                        `id`, `name`, `defaultCurrencyCode`, `groupType`, `photoUrl`,
                        `createdByUserId`, `remoteId`, `createdAtEpochMs`, `updatedAtEpochMs`,
                        `syncStatus`
                    FROM `groups`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `groups`")
                db.execSQL("ALTER TABLE `groups_new` RENAME TO `groups`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_groups_createdByUserId` ON `groups` (`createdByUserId`)",
                )
                db.execSQL("PRAGMA foreign_keys=ON")
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

    /** All migrations from version 1 through [SplitEaseDatabase] version 13. */
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
            MIGRATION_11_12,
            MIGRATION_12_13,
        )
}
