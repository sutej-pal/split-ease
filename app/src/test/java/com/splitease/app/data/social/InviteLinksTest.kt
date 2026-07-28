package com.splitease.app.data.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `tokenFromUriString parses mail-service https invite path`() {
        assertEquals(
            "5159bf4f5b9e4834a7362c9dfba81809",
            InviteLinks.tokenFromUriString(
                "https://mail-service-7rzy.onrender.com/invite/5159bf4f5b9e4834a7362c9dfba81809",
            ),
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

    @Test
    fun `urlFor uses https invite path`() {
        val url = InviteLinks.urlFor("tok123")
        assertTrue(url.startsWith("https://"), url)
        assertTrue(url.endsWith("/invite/tok123"), url)
    }

    @Test
    fun `tokenFromPastedText finds link inside share body`() {
        val body =
            InviteLinks.groupShareText("sutej hotmail", "group 2", "5159bf4f5b9e4834a7362c9dfba81809")
        assertEquals(
            "5159bf4f5b9e4834a7362c9dfba81809",
            InviteLinks.tokenFromPastedText(body),
        )
    }

    @Test
    fun `tokenFromPastedText accepts bare token`() {
        assertEquals(
            "5159bf4f5b9e4834a7362c9dfba81809",
            InviteLinks.tokenFromPastedText("5159bf4f5b9e4834a7362c9dfba81809"),
        )
    }

    @Test
    fun `intentUri builds chrome friendly link`() {
        assertEquals(
            "intent://invite/tok123#Intent;scheme=splitease;package=com.splitease.app;end",
            InviteLinks.intentUri("tok123"),
        )
    }

    @Test
    fun `tokenFromInstallReferrer parses invite_token param`() {
        assertEquals(
            "5159bf4f5b9e4834a7362c9dfba81809",
            InviteLinks.tokenFromInstallReferrer(
                "invite_token=5159bf4f5b9e4834a7362c9dfba81809",
            ),
        )
    }

    @Test
    fun `tokenFromInstallReferrer parses among other params`() {
        assertEquals(
            "abc12345token99",
            InviteLinks.tokenFromInstallReferrer(
                "utm_source=invite&invite_token=abc12345token99&utm_medium=play",
            ),
        )
    }

    @Test
    fun `tokenFromInstallReferrer returns null when missing`() {
        assertNull(InviteLinks.tokenFromInstallReferrer("utm_source=google-play"))
        assertNull(InviteLinks.tokenFromInstallReferrer(null))
        assertNull(InviteLinks.tokenFromInstallReferrer(""))
    }
}
