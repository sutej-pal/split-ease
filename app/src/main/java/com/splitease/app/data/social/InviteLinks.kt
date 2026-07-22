package com.splitease.app.data.social

/**
 * Builds shareable invite links and email bodies (MVP: share/mailto; Edge Function later).
 */
object InviteLinks {
    /** Public invite landing base (deep link / web placeholder). */
    const val BASE_URL = "https://splitease.app/invite"

    /**
     * Builds an invite URL for [token].
     *
     * @param token Opaque invite token.
     * @return Absolute invite URL.
     */
    fun urlFor(token: String): String = "$BASE_URL/$token"

    /**
     * Share body when inviting a friend.
     *
     * @param inviterName Display name of the sender.
     * @param token Invite token.
     * @return Plain-text message.
     */
    fun friendShareText(inviterName: String, token: String): String {
        val link = urlFor(token)
        return "$inviterName invited you to SplitEase.\n\n" +
            "Join with this link, then sign up using the same email they used:\n$link"
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
        val link = urlFor(token)
        return "$inviterName invited you to join \"$groupName\" on SplitEase.\n\n" +
            "Open the link and sign up with the invited email to join the group:\n$link"
    }
}
