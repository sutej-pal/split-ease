package com.splitease.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.splitease.app.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for the `payments` table.
 */
@Dao
interface PaymentDao {
    /** @return Flow of all payments newest-first. */
    @Query("SELECT * FROM payments ORDER BY paidAtEpochMs DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    /** @param groupId Group filter. @return Flow of group payments. */
    @Query(
        """
        SELECT * FROM payments
        WHERE groupId = :groupId
        ORDER BY paidAtEpochMs DESC
        """,
    )
    fun observeByGroup(groupId: String): Flow<List<PaymentEntity>>

    /**
     * Non-group payments between two users (either direction).
     *
     * @param userId First user.
     * @param otherUserId Second user.
     */
    @Query(
        """
        SELECT * FROM payments
        WHERE groupId IS NULL
          AND (
            (fromUserId = :userId AND toUserId = :otherUserId)
            OR (fromUserId = :otherUserId AND toUserId = :userId)
          )
        ORDER BY paidAtEpochMs DESC
        """,
    )
    fun observeBetweenUsers(userId: String, otherUserId: String): Flow<List<PaymentEntity>>

    /**
     * Payments where [userId] is payer or payee.
     *
     * @param userId User id.
     */
    @Query(
        """
        SELECT * FROM payments
        WHERE fromUserId = :userId OR toUserId = :userId
        ORDER BY paidAtEpochMs DESC
        """,
    )
    fun observeInvolvingUser(userId: String): Flow<List<PaymentEntity>>

    /** @param id Local UUID. @return Payment or null. */
    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PaymentEntity?

    /** Inserts or replaces [payment]. */
    @Upsert
    suspend fun upsert(payment: PaymentEntity)

    /** Deletes payment [id]. */
    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Payments awaiting cloud upload.
     */
    @Query(
        """
        SELECT * FROM payments
        WHERE syncStatus = 'PENDING' OR syncStatus = 'LOCAL_ONLY'
        ORDER BY updatedAtEpochMs ASC
        """,
    )
    suspend fun getPendingSync(): List<PaymentEntity>

    /**
     * SYNCED payment ids for [groupId] (candidates for remote-delete prune).
     *
     * @param groupId Group filter.
     */
    @Query(
        """
        SELECT id FROM payments
        WHERE groupId = :groupId AND syncStatus = 'SYNCED'
        """,
    )
    suspend fun getSyncedIdsByGroup(groupId: String): List<String>

    /**
     * SYNCED non-group payment ids involving [userId] as payer or payee.
     *
     * @param userId User id.
     */
    @Query(
        """
        SELECT id FROM payments
        WHERE groupId IS NULL
          AND syncStatus = 'SYNCED'
          AND (fromUserId = :userId OR toUserId = :userId)
        """,
    )
    suspend fun getSyncedNonGroupIdsInvolvingUser(userId: String): List<String>
}
