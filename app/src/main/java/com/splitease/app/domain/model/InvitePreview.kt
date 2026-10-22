package com.splitease.app.domain.model

/**
 * Public preview of a pending invite for the deep-link landing screen.
 *
 * @property token Opaque invite token from the link.
 * @property kind Friend vs group invite.
 * @property email Email the invite was originally addressed to (prefill hint).
 * @property inviterName Display name of the sender from `profiles.display_name`, or null when missing.
 * @property groupId Target group when [kind] is [InviteKind.GROUP].
 * @property groupName Target group name when applicable.
 * @property groupPhotoUrl Public Storage URL for the group list avatar when available.
 * @property members Existing / pending people shown for context.
 * @property inviteeName Name from the invite's own friend row, when the landing token is a person invite.
 * @property phoneCountryCode Dial code when the invite contact is a phone placeholder.
 * @property phoneNumber National digits when the invite contact is a phone placeholder.
 */
data class InvitePreview(
    val token: String,
    val kind: InviteKind,
    val email: String,
    val inviterName: String?,
    val groupId: String? = null,
    val groupName: String? = null,
    val groupPhotoUrl: String? = null,
    val members: List<InvitePreviewMember> = emptyList(),
    val inviteeName: String? = null,
    val phoneCountryCode: String? = null,
    val phoneNumber: String? = null,
)

/**
 * One row on the invite landing member list.
 *
 * @property displayName Name shown on the landing screen.
 * @property alreadyJoined True when the person already has a real membership.
 * @property inviteToken Person-invite token for a pending member. Null for people who already joined.
 */
data class InvitePreviewMember(
    val displayName: String,
    val alreadyJoined: Boolean,
    val inviteToken: String? = null,
)

/**
 * Navigation target after the invite is accepted (group id or Friends tab sentinel).
 *
 * @return Group id when present; otherwise [com.splitease.app.domain.settings.AppSettingsRepository.PENDING_INVITE_OPEN_FRIENDS].
 */
fun InvitePreview.pendingOpenTarget(): String =
    groupId?.trim()?.takeIf { it.isNotEmpty() }
        ?: com.splitease.app.domain.settings.AppSettingsRepository.PENDING_INVITE_OPEN_FRIENDS
