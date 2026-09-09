package com.splitease.app.data.remote

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MissingFxColumnTest {
    @Test
    fun detects_postgrest_unknown_column() {
        assertTrue(isMissingFxColumn(RuntimeException("PGRST204 Could not find the 'original_amount' column")))
        assertTrue(isMissingFxColumn(IllegalStateException("Could not find the 'rate_to_default_currency' column of 'expenses' in the schema cache")))
        assertFalse(isMissingFxColumn(RuntimeException("network error")))
    }

    @Test
    fun walks_cause_chain() {
        val nested = RuntimeException("wrapper", IllegalStateException("PGRST204 original_amount"))
        assertTrue(isMissingFxColumn(nested))
    }
}
