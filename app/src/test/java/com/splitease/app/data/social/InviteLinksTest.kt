package com.splitease.app.data.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
                "https://splitease-server-eight.vercel.app/invite/5159bf4f5b9e4834a7362c9dfba81809",
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
    fun `clipboardLink matches urlFor`() {
        assertEquals(InviteLinks.urlFor("tok123"), InviteLinks.clipboardLink("tok123"))
        assertFalse(InviteLinks.clipboardLink("tok123").startsWith("splitease://"))
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
    fun `tokenFromPastedText still accepts legacy custom scheme`() {
        assertEquals(
            "cb00d1b224714109a8e46755728c7cd5",
            InviteLinks.tokenFromPastedText(
                "splitease://invite/cb00d1b224714109a8e46755728c7cd5",
            ),
        )
    }

    @Test
    fun `share text uses single https link only`() {
        val body = InviteLinks.friendShareText("Ada", "tok123abc")
        assertFalse(body.contains("splitease://"), body)
        assertTrue(body.contains(InviteLinks.urlFor("tok123abc")), body)
        assertEquals(1, Regex("""https?://\S+/invite/tok123abc""").findAll(body).count())
    }

    @Test
    fun `group share html uses https only`() {
        val html = InviteLinks.groupShareHtml("Ada", "Trip", "tok123abc")
        assertFalse(html.contains("splitease://"), html)
        assertFalse(html.contains("intent://"), html)
        assertTrue(html.contains(InviteLinks.urlFor("tok123abc")), html)
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
