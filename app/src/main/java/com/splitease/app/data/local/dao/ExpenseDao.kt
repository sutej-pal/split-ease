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

    /** @param ids Expense ids. @return Matching expense rows. */
    @Query("SELECT * FROM expenses WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ExpenseEntity>

    /** @param id Local UUID. @return Flow of matching rows (0 or 1). */
    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<List<ExpenseEntity>>

    /** @param groupId Group filter. @return Group expense rows. */
    @Query("SELECT * FROM expenses WHERE groupId = :groupId")
    suspend fun getByGroupId(groupId: String): List<ExpenseEntity>

    /**
     * Non-group expenses shared between [userId] and [otherUserId].
     */
    @Query(
        """
        SELECT DISTINCT e.* FROM expenses e
        WHERE e.groupId IS NULL
          AND (
            (
              EXISTS (
                SELECT 1 FROM expense_splits s1
                WHERE s1.expenseId = e.id AND s1.userId = :userId
              )
              AND EXISTS (
                SELECT 1 FROM expense_splits s2
                WHERE s2.expenseId = e.id AND s2.userId = :otherUserId
              )
            )
            OR (
              e.paidByUserId = :userId
              AND EXISTS (
                SELECT 1 FROM expense_splits s
                WHERE s.expenseId = e.id AND s.userId = :otherUserId
              )
            )
            OR (
              e.paidByUserId = :otherUserId
              AND EXISTS (
                SELECT 1 FROM expense_splits s
                WHERE s.expenseId = e.id AND s.userId = :userId
              )
            )
          )
        """,
    )
    suspend fun getFriendshipExpenses(userId: String, otherUserId: String): List<ExpenseEntity>

    /** Inserts or replaces [expense]. */
    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    /** Inserts or replaces [expenses]. */
    @Upsert
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

    /** Inserts or replaces [splits]. */
    @Upsert
    suspend fun upsertSplits(splits: List<ExpenseSplitEntity>)

    /** Deletes existing splits for [expenseId]. */
    @Query("DELETE FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun deleteSplitsForExpense(expenseId: String)

    /** Deletes existing splits for many expenses. */
    @Query("DELETE FROM expense_splits WHERE expenseId IN (:expenseIds)")
    suspend fun deleteSplitsForExpenses(expenseIds: List<String>)

    /** Deletes expense [id] (splits cascade). */
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Remaps expense category references (legacy default id → stable id).
     *
     * @param fromId Old category id.
     * @param toId New category id.
     */
    @Query("UPDATE expenses SET categoryId = :toId WHERE categoryId = :fromId")
    suspend fun remapCategoryId(fromId: String, toId: String)

    /** @param expenseId Parent expense. @return Flow of splits. */
    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId")
    fun observeSplits(expenseId: String): Flow<List<ExpenseSplitEntity>>

    /** @param expenseId Parent expense. @return Split rows. */
    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun getSplits(expenseId: String): List<ExpenseSplitEntity>

    /**
     * Loads split rows for many expenses in one query.
     *
     * @param expenseIds Parent expense ids.
     * @return Matching split rows (unordered).
     */
    @Query("SELECT * FROM expense_splits WHERE expenseId IN (:expenseIds)")
    suspend fun getSplitsForExpenses(expenseIds: List<String>): List<ExpenseSplitEntity>

    /**
     * Observes all splits for expenses in [groupId].
     *
     * @param groupId Group filter.
     * @return Flow of split rows (unordered).
     */
    @Query(
        """
        SELECT s.* FROM expense_splits s
        INNER JOIN expenses e ON e.id = s.expenseId
        WHERE e.groupId = :groupId
        """,
    )
    fun observeSplitsByGroup(groupId: String): Flow<List<ExpenseSplitEntity>>

    /**
     * Observes splits for the given expense ids (reactive).
     *
     * @param expenseIds Parent expense ids.
     * @return Flow of matching split rows (unordered).
     */
    @Query("SELECT * FROM expense_splits WHERE expenseId IN (:expenseIds)")
    fun observeSplitsForExpenses(expenseIds: List<String>): Flow<List<ExpenseSplitEntity>>

    /**
     * Observes non-group expenses shared between [userId] and [otherUserId].
     *
     * @param userId First participant.
     * @param otherUserId Second participant.
     * @return Flow of matching expenses newest-first.
     */
    @Query(
        """
        SELECT e.* FROM expenses e
        WHERE e.groupId IS NULL
          AND (
            (
              EXISTS (
                SELECT 1 FROM expense_splits s1
                WHERE s1.expenseId = e.id AND s1.userId = :userId
              )
              AND EXISTS (
                SELECT 1 FROM expense_splits s2
                WHERE s2.expenseId = e.id AND s2.userId = :otherUserId
              )
            )
            OR (
              e.paidByUserId = :userId
              AND EXISTS (
                SELECT 1 FROM expense_splits s
                WHERE s.expenseId = e.id AND s.userId = :otherUserId
              )
            )
            OR (
              e.paidByUserId = :otherUserId
              AND EXISTS (
                SELECT 1 FROM expense_splits s
                WHERE s.expenseId = e.id AND s.userId = :userId
              )
            )
          )
        ORDER BY e.expenseDateEpochMs DESC
        """,
    )
    fun observeBetweenUsers(userId: String, otherUserId: String): Flow<List<ExpenseEntity>>

    /**
     * Observes expenses where [userId] is payer or a split participant.
     *
     * @param userId User id.
     * @return Flow of expenses newest-first.
     */
    @Query(
        """
        SELECT DISTINCT e.* FROM expenses e
        LEFT JOIN expense_splits s ON s.expenseId = e.id
        WHERE e.paidByUserId = :userId OR s.userId = :userId
        ORDER BY e.expenseDateEpochMs DESC
        """,
    )
    fun observeInvolvingUser(userId: String): Flow<List<ExpenseEntity>>

    /**
     * Newest [limit] expenses where [userId] is payer or a split participant.
     *
     * @param userId User id.
     * @param limit Max rows (UI feeds).
     */
    @Query(
        """
        SELECT DISTINCT e.* FROM expenses e
        LEFT JOIN expense_splits s ON s.expenseId = e.id
        WHERE e.paidByUserId = :userId OR s.userId = :userId
        ORDER BY e.expenseDateEpochMs DESC
        LIMIT :limit
        """,
    )
    fun observeRecentInvolvingUser(
        userId: String,
        limit: Int,
    ): Flow<List<ExpenseEntity>>

    /**
     * Newest [limit] expenses where both users are payer or split participants
     * (any group, including non-group).
     */
    @Query(
        """
        SELECT DISTINCT e.* FROM expenses e
        WHERE (
          e.paidByUserId = :userId OR EXISTS (
            SELECT 1 FROM expense_splits s
            WHERE s.expenseId = e.id AND s.userId = :userId
          )
        )
        AND (
          e.paidByUserId = :otherUserId OR EXISTS (
            SELECT 1 FROM expense_splits s
            WHERE s.expenseId = e.id AND s.userId = :otherUserId
          )
        )
        ORDER BY e.expenseDateEpochMs DESC
        LIMIT :limit
        """,
    )
    fun observeRecentSharedWithUser(
        userId: String,
        otherUserId: String,
        limit: Int,
    ): Flow<List<ExpenseEntity>>

    /**
     * Newest [limit] non-group expenses involving [userId].
     */
    @Query(
        """
        SELECT DISTINCT e.* FROM expenses e
        LEFT JOIN expense_splits s ON s.expenseId = e.id
        WHERE e.groupId IS NULL
          AND (e.paidByUserId = :userId OR s.userId = :userId)
        ORDER BY e.expenseDateEpochMs DESC
        LIMIT :limit
        """,
    )
    fun observeRecentNonGroupInvolvingUser(
        userId: String,
        limit: Int,
    ): Flow<List<ExpenseEntity>>

    /**
     * Newest [limit] expenses in [groupId].
     */
    @Query(
        """
        SELECT * FROM expenses
        WHERE groupId = :groupId
        ORDER BY expenseDateEpochMs DESC
        LIMIT :limit
        """,
    )
    fun observeRecentByGroup(
        groupId: String,
        limit: Int,
    ): Flow<List<ExpenseEntity>>

    /**
     * Searches expenses by description or notes (case-insensitive).
     *
     * @param query Substring; empty returns newest expenses.
     */
    @Query(
        """
        SELECT * FROM expenses
        WHERE :query = ''
           OR description LIKE '%' || :query || '%' COLLATE NOCASE
           OR IFNULL(notes, '') LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY expenseDateEpochMs DESC
        LIMIT 200
        """,
    )
    fun search(query: String): Flow<List<ExpenseEntity>>

    /**
     * Expenses awaiting cloud upload.
     */
    @Query(
        """
        SELECT * FROM expenses
        WHERE syncStatus = 'PENDING' OR syncStatus = 'LOCAL_ONLY'
        ORDER BY updatedAtEpochMs ASC
        """,
    )
    suspend fun getPendingSync(): List<ExpenseEntity>

    /**
     * SYNCED expense ids for [groupId] (candidates for remote-delete prune).
     *
     * @param groupId Group filter.
     */
    @Query(
        """
        SELECT id FROM expenses
        WHERE groupId = :groupId AND syncStatus = 'SYNCED'
        """,
    )
    suspend fun getSyncedIdsByGroup(groupId: String): List<String>

    /**
     * SYNCED non-group expense ids involving [userId] as payer or split participant.
     *
     * @param userId User id.
     */
    @Query(
        """
        SELECT DISTINCT e.id FROM expenses e
        LEFT JOIN expense_splits s ON s.expenseId = e.id
        WHERE e.groupId IS NULL
          AND e.syncStatus = 'SYNCED'
          AND (e.paidByUserId = :userId OR s.userId = :userId)
        """,
    )
    suspend fun getSyncedNonGroupIdsInvolvingUser(userId: String): List<String>

    /**
     * Expenses in an inclusive date window.
     *
     * @param fromEpochMs Inclusive start.
     * @param toEpochMs Inclusive end.
     */
    @Query(
        """
        SELECT * FROM expenses
        WHERE expenseDateEpochMs >= :fromEpochMs
          AND expenseDateEpochMs <= :toEpochMs
        ORDER BY expenseDateEpochMs DESC
        """,
    )
    fun observeInPeriod(fromEpochMs: Long, toEpochMs: Long): Flow<List<ExpenseEntity>>

    /**
     * Remaps split participant ids (invite placeholder → real user).
     *
     * @param fromUserId Old user id.
     * @param toUserId New user id.
     */
    @Query("UPDATE expense_splits SET userId = :toUserId WHERE userId = :fromUserId")
    suspend fun remapSplitUserId(fromUserId: String, toUserId: String)

    /**
     * Remaps payer ids (invite placeholder → real user).
     *
     * @param fromUserId Old user id.
     * @param toUserId New user id.
     */
    @Query("UPDATE expenses SET paidByUserId = :toUserId WHERE paidByUserId = :fromUserId")
    suspend fun remapPaidByUserId(fromUserId: String, toUserId: String)

    /**
     * Recurring templates due for generation.
     *
     * @param nowEpochMs Current wall clock.
     */
    @Query(
        """
        SELECT * FROM expenses
        WHERE isRecurring = 1
          AND recurrenceFrequency != 'NONE'
          AND nextOccurrenceEpochMs IS NOT NULL
          AND nextOccurrenceEpochMs <= :nowEpochMs
        ORDER BY nextOccurrenceEpochMs ASC
        """,
    )
    suspend fun getDueRecurringTemplates(nowEpochMs: Long): List<ExpenseEntity>

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

    /**
     * Replaces many expenses and their splits atomically.
     *
     * @param expenses Parent rows.
     * @param splits Split rows for [expenses] (ids not in [expenses] are ignored by callers).
     */
    @Transaction
    suspend fun upsertExpensesWithSplits(
        expenses: List<ExpenseEntity>,
        splits: List<ExpenseSplitEntity>,
    ) {
        if (expenses.isEmpty()) return
        upsertAll(expenses)
        expenses.map { it.id }.chunked(SQLITE_IN_CHUNK).forEach { chunk ->
            deleteSplitsForExpenses(chunk)
        }
        if (splits.isNotEmpty()) {
            upsertSplits(splits)
        }
    }
}

/** Stay under SQLite's 999-bind limit for `IN` lists. */
private const val SQLITE_IN_CHUNK = 400
