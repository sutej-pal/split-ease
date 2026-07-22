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
     * Remaps payer and split participant ids after an invite placeholder becomes a real user.
     *
     * @param fromUserId Placeholder user id.
     * @param toUserId Real auth user id.
     */
    suspend fun remapUserId(fromUserId: String, toUserId: String)
}
