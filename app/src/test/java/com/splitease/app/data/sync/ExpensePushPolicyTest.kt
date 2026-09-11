package com.splitease.app.data.sync

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExpensePushPolicyTest {
    @Test
    fun marks_synced_when_local_row_is_gone() {
        assertTrue(ExpensePushPolicy.shouldMarkSynced(null, 100L))
    }

    @Test
    fun marks_synced_when_local_timestamp_matches_push() {
        assertTrue(ExpensePushPolicy.shouldMarkSynced(100L, 100L))
    }

    @Test
    fun keeps_pending_when_local_edit_is_newer() {
        assertFalse(ExpensePushPolicy.shouldMarkSynced(200L, 100L))
    }
}
