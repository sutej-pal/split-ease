package com.splitease.app.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SyncNetworkLogTest {
    @Test
    fun describe_strips_apikey_and_keeps_postgrest_filters() {
        val raw =
            "https://xyz.supabase.co/rest/v1/expenses" +
                "?apikey=secret-key&paid_by_user_id=eq.abc&select=%2A"
        val line = SyncNetworkLog.describe("GET", raw)
        assertEquals(
            "GET rest/v1/expenses?paid_by_user_id=eq.abc&select=%2A",
            line,
        )
        assertFalse(line.contains("secret-key"))
    }

    @Test
    fun describe_without_query() {
        assertEquals(
            "POST rest/v1/rpc/accept_pending_invites",
            SyncNetworkLog.describe(
                "POST",
                "https://xyz.supabase.co/rest/v1/rpc/accept_pending_invites",
            ),
        )
    }
}
