package com.splitease.app.domain.repository

import com.splitease.app.domain.model.ExpenseComment
import kotlinx.coroutines.flow.Flow

/** Offline-first access to expense comment threads. */
interface ExpenseCommentRepository {
    fun observeForExpense(expenseId: String): Flow<List<ExpenseComment>>

    suspend fun upsert(comment: ExpenseComment)

    suspend fun upsertAll(comments: List<ExpenseComment>)

    suspend fun getPendingSync(): List<ExpenseComment>

    suspend fun deleteForExpense(expenseId: String)
}
