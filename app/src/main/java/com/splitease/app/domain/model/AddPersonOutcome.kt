package com.splitease.app.domain.model

/**
 * Result of adding someone by email — either an existing user or a pending invite.
 *
 * @property friend Local friendship row (may use a placeholder [Friend.friendUserId] until accepted).
 * @property inviteShareText Body text for email/share when the recipient is not on SplitEase yet; null if already connected.
 * @property isInvitePending True when an invite was created instead of linking an existing account.
 */
data class AddPersonOutcome(
    val friend: Friend,
    val inviteShareText: String? = null,
    val isInvitePending: Boolean = false,
)
