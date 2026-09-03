package com.splitease.app.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Verifies Room migrations from the oldest exported schema (v5) through current (v15).
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
        helper.createDatabase(dbName, 5).close()
        helper.runMigrationsAndValidate(dbName, 15, true, *SplitEaseMigrations.ALL)
    }
}
