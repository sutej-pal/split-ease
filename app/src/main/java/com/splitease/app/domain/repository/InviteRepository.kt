package com.splitease.app.domain.repository

import com.splitease.app.domain.model.Invite
import com.splitease.app.domain.model.InviteStatus
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to email invites.
 */
interface InviteRepository {
    /**
     * Observes invites sent by [inviterUserId].
     *
     * @param inviterUserId Inviter user id.
     * @return Cold [Flow] of invites.
     */
    fun observeSentInvites(inviterUserId: String): Flow<List<Invite>>

    /**
     * Loads pending invites for an email address.
     *
     * @param email Recipient email.
     * @return Pending invites.
     */
    suspend fun getPendingByEmail(email: String): List<Invite>

    /**
     * Loads an invite by token.
     *
     * @param token Invite token from the link.
     * @return Invite or null.
     */
    suspend fun getByToken(token: String): Invite?

    /**
     * Loads invite linked to a friendship row.
     *
     * @param friendRowId Friendship id.
     * @return Invite or null.
     */
    suspend fun getByFriendRowId(friendRowId: String): Invite?

    /**
     * Generic group share-link invites (no per-person friendship row).
     *
     * @param groupId Target group.
     * @param status Invite status filter (typically [InviteStatus.PENDING]).
     * @return Matching invites, newest first.
     */
    suspend fun getGroupShareInvites(
        groupId: String,
        status: InviteStatus = InviteStatus.PENDING,
    ): List<Invite>

    /**
     * Loads invites that still need a cloud upsert.
     *
     * @return Pending / local-only invites.
     */
    suspend fun getPendingSync(): List<Invite>

    /**
     * Inserts or replaces an invite.
     *
     * @param invite Domain invite.
     */
    suspend fun upsert(invite: Invite)

    /**
     * Convenience update of status.
     *
     * @param invite Invite with new [InviteStatus].
     */
    suspend fun markStatus(invite: Invite, status: InviteStatus) {
        upsert(invite.copy(status = status))
    }
}
