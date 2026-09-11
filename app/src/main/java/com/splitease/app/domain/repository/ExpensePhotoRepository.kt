package com.splitease.app.domain.repository

import com.splitease.app.domain.model.ExpensePhoto
import kotlinx.coroutines.flow.Flow

/** Offline-first access to expense receipt photos. */
interface ExpensePhotoRepository {
    fun observeForExpense(expenseId: String): Flow<List<ExpensePhoto>>

    suspend fun getForExpense(expenseId: String): List<ExpensePhoto>

    /**
     * Loads photos for many expenses.
     *
     * @param expenseIds Parent expense ids.
     * @return Matching photo rows (order not guaranteed across expenses).
     */
    suspend fun getForExpenses(expenseIds: List<String>): List<ExpensePhoto>

    suspend fun upsert(photo: ExpensePhoto)

    suspend fun upsertAll(photos: List<ExpensePhoto>)

    suspend fun getPendingSync(): List<ExpensePhoto>

    suspend fun deleteForExpense(expenseId: String)

    suspend fun getById(id: String): ExpensePhoto?
}
