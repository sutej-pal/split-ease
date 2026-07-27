package com.splitease.app.data.social

/**
 * Builds shareable invite links and email bodies (MVP: share/mailto; Edge Function later).
 */
object InviteLinks {
    /** Public invite landing base (https App Link / web placeholder). */
    const val BASE_URL = "https://splitease.app/invite"

    /** Custom-scheme host for invite deep links (`splitease://invite/{token}`). */
    const val DEEP_LINK_SCHEME = "splitease"

    /** Custom-scheme host for invite deep links. */
    const val DEEP_LINK_HOST = "invite"

    /**
     * Builds an invite URL for [token].
     *
     * @param token Opaque invite token.
     * @return Absolute invite URL.
     */
    fun urlFor(token: String): String = "$BASE_URL/$token"

    /**
     * Builds a custom-scheme invite URI for [token].
     *
     * @param token Opaque invite token.
     * @return `splitease://invite/{token}`.
     */
    fun deepLinkUri(token: String): String = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/$token"

    /**
     * Plain invite URL for clipboard (custom scheme opens the installed app).
     *
     * @param token Opaque invite token.
     * @return `splitease://invite/{token}`.
     */
    fun clipboardLink(token: String): String = deepLinkUri(token)

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

            scheme.equals("https", ignoreCase = true) &&
                (
                    host.equals("splitease.app", ignoreCase = true) ||
                        host.equals("www.splitease.app", ignoreCase = true)
                ) &&
                path.startsWith("invite/") ->
                path.removePrefix("invite/").substringBefore('/').takeIf { it.isNotBlank() }

            else -> null
        }
    }

    /**
     * Share body when inviting a friend.
     *
     * @param inviterName Display name of the sender.
     * @param token Invite token.
     * @return Plain-text message.
     */
    fun friendShareText(inviterName: String, token: String): String {
        val appLink = deepLinkUri(token)
        val webLink = urlFor(token)
        return "$inviterName invited you to SplitEase.\n\n" +
            "Open this link on your phone to join in the app:\n$appLink\n\n" +
            "Or open:\n$webLink"
    }

    /**
     * Share body when inviting someone into a group.
     *
     * @param inviterName Display name of the sender.
     * @param groupName Group name.
     * @param token Invite token.
     * @return Plain-text message.
     */
    fun groupShareText(inviterName: String, groupName: String, token: String): String {
        val appLink = deepLinkUri(token)
        val webLink = urlFor(token)
        return "$inviterName invited you to join \"$groupName\" on SplitEase.\n\n" +
            "Open this link on your phone to join the group in the app:\n$appLink\n\n" +
            "Or open:\n$webLink"
    }
}
