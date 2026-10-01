package com.splitease.app.domain.model

/**
 * Kind of social invite.
 */
enum class InviteKind {
    FRIEND,
    GROUP,
}

/**
 * Lifecycle of an invite.
 */
enum class InviteStatus {
    PENDING,
    ACCEPTED,
    CANCELLED,
}

/**
 * Email invite to join SplitEase (and optionally a group) before the recipient has an account.
 *
 * @property id Local/remote UUID.
 * @property token Opaque token embedded in the invite link.
 * @property inviterUserId User who sent the invite.
 * @property email Recipient email.
 * @property kind Friend vs group invite.
 * @property groupId Target group when [kind] is [InviteKind.GROUP].
 * @property friendRowId Related local friendship row when applicable.
 * @property status Pending until the recipient signs up / accepts.
 * @property createdAtEpochMs Created-at timestamp.
 * @property syncStatus Offline sync bookmark.
 */
data class Invite(
    val id: String,
    val token: String,
    val inviterUserId: String,
    val email: String,
    val kind: InviteKind,
    val groupId: String? = null,
    val friendRowId: String? = null,
    val status: InviteStatus = InviteStatus.PENDING,
    val createdAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
