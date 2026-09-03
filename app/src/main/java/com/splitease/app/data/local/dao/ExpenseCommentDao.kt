package com.splitease.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.splitease.app.data.local.entity.ExpenseCommentEntity
import kotlinx.coroutines.flow.Flow

/** DAO for [ExpenseCommentEntity]. */
@Dao
interface ExpenseCommentDao {
    @Query(
        """
        SELECT * FROM expense_comments
        WHERE expenseId = :expenseId
        ORDER BY createdAtEpochMs ASC
        """,
    )
    fun observeForExpense(expenseId: String): Flow<List<ExpenseCommentEntity>>

    @Upsert
    suspend fun upsert(comment: ExpenseCommentEntity)

    @Upsert
    suspend fun upsertAll(comments: List<ExpenseCommentEntity>)

    @Query(
        """
        SELECT * FROM expense_comments
        WHERE syncStatus = 'PENDING'
        ORDER BY createdAtEpochMs ASC
        """,
    )
    suspend fun getPendingSync(): List<ExpenseCommentEntity>

    @Query("DELETE FROM expense_comments WHERE expenseId = :expenseId")
    suspend fun deleteForExpense(expenseId: String)
}
