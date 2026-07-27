package com.splitease.app.domain.model

/**
 * Public preview of a pending invite for the deep-link landing screen.
 *
 * @property token Opaque invite token from the link.
 * @property kind Friend vs group invite.
 * @property email Email the invite was originally addressed to (prefill hint).
 * @property inviterName Display name of the sender.
 * @property groupId Target group when [kind] is [InviteKind.GROUP].
 * @property groupName Target group name when applicable.
 * @property members Existing / pending people shown for context.
 */
data class InvitePreview(
    val token: String,
    val kind: InviteKind,
    val email: String,
    val inviterName: String,
    val groupId: String? = null,
    val groupName: String? = null,
    val members: List<InvitePreviewMember> = emptyList(),
)

/**
 * One row on the invite landing member list.
 *
 * @property displayName Name shown on the landing screen.
 * @property alreadyJoined True when the person already has a real membership.
 */
data class InvitePreviewMember(
    val displayName: String,
    val alreadyJoined: Boolean,
)
