package com.splitease.app.data.local.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Verifies Room migrations from schema v1 through v4 without destructive wipe.
 */
@RunWith(AndroidJUnit4::class)
class SplitEaseMigrationsTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            SplitEaseDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        helper.createDatabase(dbName, 1).close()
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SplitEaseDatabase::class.java,
            dbName,
        ).addMigrations(*SplitEaseMigrations.ALL)
            .build()
            .apply {
                openHelper.writableDatabase
                close()
            }
        helper.runMigrationsAndValidate(dbName, 5, true, *SplitEaseMigrations.ALL)
    }
}
