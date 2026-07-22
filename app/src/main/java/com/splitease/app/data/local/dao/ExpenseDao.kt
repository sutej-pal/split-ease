package com.splitease.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.splitease.app.data.local.entity.ExpenseEntity
import com.splitease.app.data.local.entity.ExpenseSplitEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for expenses and expense splits.
 */
@Dao
interface ExpenseDao {
    /** @return Flow of all expenses newest-first. */
    @Query("SELECT * FROM expenses ORDER BY expenseDateEpochMs DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    /** @param groupId Group filter. @return Flow of group expenses newest-first. */
    @Query(
        """
        SELECT * FROM expenses
        WHERE groupId = :groupId
        ORDER BY expenseDateEpochMs DESC
        """,
    )
    fun observeByGroup(groupId: String): Flow<List<ExpenseEntity>>

    /** @param id Local UUID. @return Expense or null. */
    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ExpenseEntity?

    /** Inserts or replaces [expense]. */
    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    /** Inserts or replaces [splits]. */
    @Upsert
    suspend fun upsertSplits(splits: List<ExpenseSplitEntity>)

    /** Deletes existing splits for [expenseId]. */
    @Query("DELETE FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun deleteSplitsForExpense(expenseId: String)

    /** Deletes expense [id] (splits cascade). */
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: String)

    /** @param expenseId Parent expense. @return Flow of splits. */
    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId")
    fun observeSplits(expenseId: String): Flow<List<ExpenseSplitEntity>>

    /** @param expenseId Parent expense. @return Split rows. */
    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun getSplits(expenseId: String): List<ExpenseSplitEntity>

    /**
     * Replaces an expense and its splits atomically.
     *
     * @param expense Parent row.
     * @param splits New split rows for [expense].
     */
    @Transaction
    suspend fun upsertExpenseWithSplits(
        expense: ExpenseEntity,
        splits: List<ExpenseSplitEntity>,
    ) {
        upsert(expense)
        deleteSplitsForExpense(expense.id)
        if (splits.isNotEmpty()) {
            upsertSplits(splits)
        }
    }
}
