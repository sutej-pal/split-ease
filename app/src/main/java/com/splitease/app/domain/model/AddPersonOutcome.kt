package com.splitease.app.domain.model

/**
 * Result of adding someone by email — either an existing user or a pending invite.
 *
 * @property friend Local friendship row (may use a placeholder [Friend.friendUserId] until accepted).
 * @property inviteShareText Body text for the system share sheet when the invite was not emailed;
 *   null when already connected or when [inviteEmailSent] is true.
 * @property isInvitePending True when an invite was created instead of linking an existing account.
 * @property inviteEmailSent True when the invite link was delivered by email.
 */
data class AddPersonOutcome(
    val friend: Friend,
    val inviteShareText: String? = null,
    val isInvitePending: Boolean = false,
    val inviteEmailSent: Boolean = false,
)
