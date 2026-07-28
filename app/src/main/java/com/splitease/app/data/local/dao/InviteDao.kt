package com.splitease.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.splitease.app.data.local.entity.InviteEntity
import com.splitease.app.domain.model.InviteStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for the `invites` table.
 */
@Dao
interface InviteDao {
    /** @param inviterUserId Inviter. @return Flow of invites they sent. */
    @Query(
        """
        SELECT * FROM invites
        WHERE inviterUserId = :inviterUserId
        ORDER BY createdAtEpochMs DESC
        """,
    )
    fun observeByInviter(inviterUserId: String): Flow<List<InviteEntity>>

    /** @param email Recipient email. @param status Status filter. @return Matching invites. */
    @Query(
        """
        SELECT * FROM invites
        WHERE lower(email) = lower(:email) AND status = :status
        """,
    )
    suspend fun getByEmailAndStatus(email: String, status: InviteStatus): List<InviteEntity>

    /** @param token Invite token. @return Invite or null. */
    @Query("SELECT * FROM invites WHERE token = :token LIMIT 1")
    suspend fun getByToken(token: String): InviteEntity?

    /** @param friendRowId Related friendship id. @return Invite or null. */
    @Query("SELECT * FROM invites WHERE friendRowId = :friendRowId LIMIT 1")
    suspend fun getByFriendRowId(friendRowId: String): InviteEntity?

    /**
     * Generic group share-link invites (no per-person friendship row).
     *
     * @param groupId Target group.
     * @param status Status filter (typically [InviteStatus.PENDING]).
     * @return Matching invites, newest first.
     */
    @Query(
        """
        SELECT * FROM invites
        WHERE groupId = :groupId
          AND status = :status
          AND kind = 'GROUP'
          AND friendRowId IS NULL
        ORDER BY createdAtEpochMs DESC
        """,
    )
    suspend fun getGroupShareInvites(
        groupId: String,
        status: InviteStatus,
    ): List<InviteEntity>

    /** Invites awaiting cloud upsert. */
    @Query(
        """
        SELECT * FROM invites
        WHERE syncStatus IN ('PENDING', 'LOCAL_ONLY')
        ORDER BY createdAtEpochMs ASC
        """,
    )
    suspend fun getPendingSync(): List<InviteEntity>

    /** Inserts or replaces [invite]. */
    @Upsert
    suspend fun upsert(invite: InviteEntity)

    /** Inserts or replaces many invites. */
    @Upsert
    suspend fun upsertAll(invites: List<InviteEntity>)
}
