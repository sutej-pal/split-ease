package com.splitease.app.data.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InviteLinksTest {
    @Test
    fun `tokenFromUriString parses https invite path`() {
        assertEquals(
            "abc123token",
            InviteLinks.tokenFromUriString("https://splitease.app/invite/abc123token"),
        )
    }

    @Test
    fun `tokenFromUriString parses custom scheme`() {
        assertEquals("tok999", InviteLinks.tokenFromUriString("splitease://invite/tok999"))
    }

    @Test
    fun `tokenFromUriString ignores auth callback`() {
        assertNull(InviteLinks.tokenFromUriString("splitease://auth-callback"))
    }
}
