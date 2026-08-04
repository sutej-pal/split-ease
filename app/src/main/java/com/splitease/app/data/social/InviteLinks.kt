package com.splitease.app.data.social

import com.splitease.app.BuildConfig

/**
 * Single source of truth for invite URLs and share copy.
 *
 * **Outbound (copy / share / UI):** always [urlFor] — https mail-service (or fallback) page that
 * redirects into the app.
 *
 * **Inbound:** still accepts custom-scheme / intent / https / bare tokens via [tokenFromUri],
 * [tokenFromPastedText], and [tokenFromInstallReferrer].
 */
object InviteLinks {
    /** Fallback when [BuildConfig.MAIL_SERVICE_BASE_URL] is unset. */
    private const val FALLBACK_WEB_BASE = "https://splitease.app/invite"

    /** Custom-scheme for Android intent-filters (`splitease://invite/{token}`). Inbound only. */
    const val DEEP_LINK_SCHEME = "splitease"

    /** Custom-scheme host for invite deep links. Inbound only. */
    const val DEEP_LINK_HOST = "invite"

    /** Play Install Referrer query key set by the mail-service Play Store fallback. */
    const val INSTALL_REFERRER_TOKEN_KEY = "invite_token"

    /** Public invite landing base (https redirect page). */
    val BASE_URL: String
        get() {
            val mail = BuildConfig.MAIL_SERVICE_BASE_URL.trim().trimEnd('/')
            return if (mail.isNotEmpty()) "$mail/invite" else FALLBACK_WEB_BASE
        }

    private val CUSTOM_SCHEME_TOKEN =
        Regex("""splitease://invite/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val HTTPS_TOKEN =
        Regex(
            """https?://[^/\s]+/invite/([A-Za-z0-9_-]+)""",
            RegexOption.IGNORE_CASE,
        )
    private val INTENT_TOKEN =
        Regex(
            """intent://invite/([A-Za-z0-9_-]+)#Intent;""",
            RegexOption.IGNORE_CASE,
        )
    private val BARE_TOKEN = Regex("""^[A-Za-z0-9_-]{16,64}$""")
    private val TOKEN_LOOSE = Regex("""^[A-Za-z0-9_-]{8,128}$""")

    /**
     * Canonical https invite URL for [token] — use for copy, share, and on-screen display.
     *
     * @param token Opaque invite token.
     * @return Absolute invite URL on the mail-service redirect page.
     */
    fun urlFor(token: String): String = "$BASE_URL/$token"

    /**
     * Invite URL for clipboard / UI — same as [urlFor].
     *
     * @param token Opaque invite token.
     * @return https invite URL.
     */
    fun clipboardLink(token: String): String = urlFor(token)

    /**
     * Custom-scheme URI for Android deep-link intake tests / diagnostics.
     * Do **not** use for share or clipboard — prefer [urlFor].
     *
     * @param token Opaque invite token.
     * @return `splitease://invite/{token}`.
     */
    fun deepLinkUri(token: String): String = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/$token"

    /**
     * Chrome-friendly Intent URI that opens the installed app, with https fallback.
     *
     * @param token Opaque invite token.
     * @param fallbackUrl Browser fallback when the app is not installed (defaults to [urlFor]).
     * @return `intent://invite/{token}#Intent;…;end`.
     */
    fun intentUri(
        token: String,
        fallbackUrl: String = urlFor(token),
    ): String {
        val encodedFallback =
            java.net.URLEncoder
                .encode(fallbackUrl, Charsets.UTF_8.name())
                .replace("+", "%20")
        return "intent://$DEEP_LINK_HOST/$token#Intent;" +
            "scheme=$DEEP_LINK_SCHEME;" +
            "package=com.splitease.app;" +
            "S.browser_fallback_url=$encodedFallback;" +
            "end"
    }

    /**
     * Extracts an invite token from a deep-link [uri], or null if not an invite link.
     *
     * @param uri Incoming intent data.
     * @return Token path segment, or null.
     */
    fun tokenFromUri(uri: android.net.Uri?): String? = tokenFromUriString(uri?.toString())

    /**
     * Extracts an invite token from a URI string (scheme://host/path…).
     *
     * @param uriString Raw URI text.
     * @return Token path segment, or null.
     */
    fun tokenFromUriString(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        val trimmed = uriString.trim()
        val schemeSep = trimmed.indexOf("://")
        if (schemeSep <= 0) return null
        val scheme = trimmed.substring(0, schemeSep)
        val rest = trimmed.substring(schemeSep + 3)
        val slash = rest.indexOf('/')
        val host = if (slash >= 0) rest.substring(0, slash) else rest
        val path = if (slash >= 0) rest.substring(slash + 1).trim('/') else ""
        return when {
            scheme.equals(DEEP_LINK_SCHEME, ignoreCase = true) &&
                host.equals(DEEP_LINK_HOST, ignoreCase = true) ->
                path.substringBefore('/').takeIf { it.isNotBlank() }

            scheme.equals("intent", ignoreCase = true) &&
                host.equals(DEEP_LINK_HOST, ignoreCase = true) ->
                path.substringBefore('#').substringBefore('/').takeIf { it.isNotBlank() }

            scheme.equals("https", ignoreCase = true) ||
                scheme.equals("http", ignoreCase = true) ->
                when {
                    path.startsWith("invite/") ->
                        path.removePrefix("invite/").substringBefore('/').takeIf { it.isNotBlank() }
                    // Some clients strip path to host=invite when rewriting.
                    host.equals(DEEP_LINK_HOST, ignoreCase = true) ->
                        path.substringBefore('/').takeIf { it.isNotBlank() }
                    else -> null
                }

            else -> null
        }
    }

    /**
     * Extracts `invite_token` from a Play Install Referrer campaign string.
     *
     * Expected form (as set by the mail-service Play Store fallback):
     * `invite_token={token}` optionally combined with other `&`-separated params.
     *
     * @param referrer Raw `InstallReferrerClient.installReferrer` value.
     * @return Opaque invite token, or null when missing / invalid.
     */
    fun tokenFromInstallReferrer(referrer: String?): String? {
        if (referrer.isNullOrBlank()) return null
        val decoded =
            runCatching { android.net.Uri.decode(referrer.trim()) }.getOrDefault(referrer.trim())
        for (part in decoded.split('&')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val key =
                runCatching { android.net.Uri.decode(part.substring(0, eq)) }
                    .getOrDefault(part.substring(0, eq))
                    .trim()
            if (!key.equals(INSTALL_REFERRER_TOKEN_KEY, ignoreCase = true)) continue
            val value =
                runCatching { android.net.Uri.decode(part.substring(eq + 1)) }
                    .getOrDefault(part.substring(eq + 1))
                    .trim()
            if (BARE_TOKEN.matches(value) || TOKEN_LOOSE.matches(value)) return value
        }
        return null
    }

    /**
     * Extracts an invite token from pasted share text, a URI, or a bare token.
     *
     * @param text Clipboard / user-entered text.
     * @return Token, or null when none could be parsed.
     */
    fun tokenFromPastedText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        tokenFromUriString(trimmed)?.let { return it }
        CUSTOM_SCHEME_TOKEN
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        HTTPS_TOKEN
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        INTENT_TOKEN
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        if (BARE_TOKEN.matches(trimmed)) return trimmed
        return null
    }

    /**
     * Share body when inviting a friend.
     *
     * @param inviterName Display name of the sender.
     * @param token Invite token.
     * @return Plain-text message with a single https invite link.
     */
    fun friendShareText(inviterName: String, token: String): String {
        val link = urlFor(token)
        return "$inviterName invited you to SplitEase.\n\nJoin here:\n$link"
    }

    /**
     * HTML share body when inviting a friend.
     *
     * @param inviterName Display name of the sender.
     * @param token Invite token.
     * @return HTML fragment with a single https invite link.
     */
    fun friendShareHtml(inviterName: String, token: String): String {
        val link = urlFor(token)
        return "<p>${escapeHtml(inviterName)} invited you to SplitEase.</p>" +
            "<p><a href=\"$link\">Join on SplitEase</a></p>" +
            "<p>${escapeHtml(link)}</p>"
    }

    /**
     * Share body when inviting someone into a group.
     *
     * @param inviterName Display name of the sender.
     * @param groupName Group name.
     * @param token Invite token.
     * @return Plain-text message with a single https invite link.
     */
    fun groupShareText(inviterName: String, groupName: String, token: String): String {
        val link = urlFor(token)
        return "$inviterName invited you to join \"$groupName\" on SplitEase.\n\nJoin here:\n$link"
    }

    /**
     * HTML share body when inviting someone into a group.
     *
     * @param inviterName Display name of the sender.
     * @param groupName Group name.
     * @param token Invite token.
     * @return HTML fragment with a single https invite link.
     */
    fun groupShareHtml(inviterName: String, groupName: String, token: String): String {
        val link = urlFor(token)
        return "<p>${escapeHtml(inviterName)} invited you to join " +
            "&quot;${escapeHtml(groupName)}&quot; on SplitEase.</p>" +
            "<p><a href=\"$link\">Join on SplitEase</a></p>" +
            "<p>${escapeHtml(link)}</p>"
    }

    /**
     * Builds HTML for an existing plain share body by extracting its token.
     *
     * @param plainShareText Output of [friendShareText] / [groupShareText] (or older format).
     * @return HTML with a clickable https invite link, or null when no token is found.
     */
    fun htmlForShareText(plainShareText: String): String? {
        val token = tokenFromPastedText(plainShareText) ?: return null
        return if (plainShareText.contains("join \"", ignoreCase = true) ||
            plainShareText.contains("join the group", ignoreCase = true)
        ) {
            val groupName =
                Regex("""join "([^"]+)"""")
                    .find(plainShareText)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: "a group"
            val inviter =
                plainShareText.substringBefore(" invited").trim().ifBlank { "A friend" }
            groupShareHtml(inviter, groupName, token)
        } else {
            val inviter =
                plainShareText.substringBefore(" invited").trim().ifBlank { "A friend" }
            friendShareHtml(inviter, token)
        }
    }

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
