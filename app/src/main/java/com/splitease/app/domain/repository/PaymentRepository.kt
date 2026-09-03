package com.splitease.app.domain.repository

import com.splitease.app.domain.model.Payment
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to settlement payments.
 */
interface PaymentRepository {
    /**
     * Observes payments, optionally scoped to a group.
     *
     * @param groupId When non-null, only payments for that group; when null, all payments.
     * @return Cold [Flow] ordered by paid-at descending.
     */
    fun observePayments(groupId: String? = null): Flow<List<Payment>>

    /**
     * Observes non-group payments between two users.
     *
     * @param userId First user.
     * @param otherUserId Second user.
     */
    fun observeBetweenUsers(userId: String, otherUserId: String): Flow<List<Payment>>

    /**
     * Observes payments involving [userId] as payer or payee.
     *
     * @param userId User id.
     */
    fun observeInvolvingUser(userId: String): Flow<List<Payment>>

    /**
     * Newest [limit] payments involving [userId] (UI feeds).
     */
    fun observeRecentInvolvingUser(
        userId: String,
        limit: Int = FeedQueryLimits.UI_FEED,
    ): Flow<List<Payment>>

    /**
     * Newest [limit] payments between two users (either direction, any group).
     */
    fun observeRecentSharedWithUser(
        userId: String,
        otherUserId: String,
        limit: Int = FeedQueryLimits.UI_FEED,
    ): Flow<List<Payment>>

    /**
     * Newest [limit] non-group payments involving [userId].
     */
    fun observeRecentNonGroupInvolvingUser(
        userId: String,
        limit: Int = FeedQueryLimits.UI_FEED,
    ): Flow<List<Payment>>

    /**
     * Newest [limit] payments for [groupId] (ledger UI).
     */
    fun observeRecentByGroup(
        groupId: String,
        limit: Int = FeedQueryLimits.UI_FEED,
    ): Flow<List<Payment>>

    /**
     * Loads a payment by id.
     *
     * @param id Local UUID.
     * @return The payment, or null.
     */
    suspend fun getById(id: String): Payment?

    /**
     * Inserts or replaces a payment.
     *
     * @param payment Domain payment.
     */
    suspend fun upsert(payment: Payment)

    /**
     * Deletes a payment by id.
     *
     * @param id Local UUID.
     */
    suspend fun deleteById(id: String)

    /**
     * Loads payments that still need cloud sync.
     */
    suspend fun getPendingSync(): List<Payment>

    /**
     * SYNCED payment ids for [groupId] (used to prune remote deletes).
     *
     * @param groupId Group filter.
     */
    suspend fun getSyncedIdsByGroup(groupId: String): List<String>

    /**
     * SYNCED non-group payment ids involving [userId] (used to prune remote deletes).
     *
     * @param userId User id.
     */
    suspend fun getSyncedNonGroupIdsInvolvingUser(userId: String): List<String>
}
