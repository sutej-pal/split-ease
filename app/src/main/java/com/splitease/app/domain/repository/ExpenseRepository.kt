package com.splitease.app.domain.repository

import com.splitease.app.domain.model.Expense
import com.splitease.app.domain.model.ExpenseSplit
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to expenses and their splits.
 */
interface ExpenseRepository {
    /**
     * Observes expenses, optionally filtered by group.
     *
     * @param groupId When non-null, only expenses for that group; when null, all expenses.
     * @return Cold [Flow] ordered by expense date descending.
     */
    fun observeExpenses(groupId: String? = null): Flow<List<Expense>>

    /**
     * Loads an expense by id.
     *
     * @param id Local UUID.
     * @return The expense, or null.
     */
    suspend fun getExpenseById(id: String): Expense?

    /**
     * Observes a single expense by id.
     *
     * @param id Local UUID.
     */
    fun observeExpenseById(id: String): Flow<Expense?>

    /**
     * Persists an expense together with its split lines in one transaction.
     *
     * @param expense Parent expense.
     * @param splits Participant split rows; must reference [expense].id.
     */
    suspend fun upsertExpenseWithSplits(
        expense: Expense,
        splits: List<ExpenseSplit>,
    )

    /**
     * Deletes an expense and cascaded splits.
     *
     * @param id Local UUID.
     */
    suspend fun deleteExpenseById(id: String)

    /**
     * Observes split lines for an expense.
     *
     * @param expenseId Parent expense id.
     * @return Cold [Flow] of splits.
     */
    fun observeSplits(expenseId: String): Flow<List<ExpenseSplit>>

    /**
     * Loads splits for an expense once.
     *
     * @param expenseId Parent expense id.
     * @return Current split rows.
     */
    suspend fun getSplits(expenseId: String): List<ExpenseSplit>

    /**
     * Observes 1:1 (non-group) expenses between two users.
     *
     * @param userId First user.
     * @param otherUserId Second user.
     * @return Cold [Flow] of expenses.
     */
    fun observeBetweenUsers(userId: String, otherUserId: String): Flow<List<Expense>>

    /**
     * Observes expenses where [userId] is the payer or a split participant.
     *
     * @param userId User id.
     * @return Cold [Flow] of expenses newest-first.
     */
    fun observeInvolvingUser(userId: String): Flow<List<Expense>>

    /**
     * Loads split rows for many expenses.
     *
     * @param expenseIds Parent expense ids.
     * @return Map of expenseId → splits (missing ids map to empty lists).
     */
    suspend fun getSplitsForExpenses(expenseIds: List<String>): Map<String, List<ExpenseSplit>>

    /**
     * Observes splits for all expenses in [groupId].
     *
     * @param groupId Group filter.
     * @return Flow of expenseId → splits.
     */
    fun observeSplitsByGroup(groupId: String): Flow<Map<String, List<ExpenseSplit>>>

    /**
     * Observes splits for [expenseIds]. Empty [expenseIds] emits an empty map.
     *
     * @param expenseIds Parent expense ids.
     * @return Flow of expenseId → splits.
     */
    fun observeSplitsForExpenses(expenseIds: List<String>): Flow<Map<String, List<ExpenseSplit>>>

    /**
     * Remaps payer and split participant ids after an invite placeholder becomes a real user.
     *
     * @param fromUserId Placeholder user id.
     * @param toUserId Real auth user id.
     */
    suspend fun remapUserId(fromUserId: String, toUserId: String)

    /**
     * Loads recurring templates whose next occurrence is due.
     *
     * @param nowEpochMs Current time.
     * @return Due template expenses.
     */
    suspend fun getDueRecurringTemplates(nowEpochMs: Long): List<Expense>

    /**
     * Observes expenses matching [query] in description or notes.
     *
     * @param query Search substring.
     */
    fun search(query: String): Flow<List<Expense>>

    /**
     * Observes expenses in a date window.
     *
     * @param fromEpochMs Inclusive start.
     * @param toEpochMs Inclusive end.
     */
    fun observeInPeriod(fromEpochMs: Long, toEpochMs: Long): Flow<List<Expense>>

    /**
     * Loads expenses that still need cloud sync.
     */
    suspend fun getPendingSync(): List<Expense>

    /**
     * SYNCED expense ids for [groupId] (used to prune remote deletes).
     *
     * @param groupId Group filter.
     */
    suspend fun getSyncedIdsByGroup(groupId: String): List<String>

    /**
     * SYNCED non-group expense ids involving [userId] (used to prune remote deletes).
     *
     * @param userId User id.
     */
    suspend fun getSyncedNonGroupIdsInvolvingUser(userId: String): List<String>
}
