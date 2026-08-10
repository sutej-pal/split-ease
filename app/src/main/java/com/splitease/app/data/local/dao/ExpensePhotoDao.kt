package com.splitease.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.splitease.app.data.local.entity.ExpensePhotoEntity
import kotlinx.coroutines.flow.Flow

/** DAO for [ExpensePhotoEntity]. */
@Dao
interface ExpensePhotoDao {
    @Query(
        """
        SELECT * FROM expense_photos
        WHERE expenseId = :expenseId
        ORDER BY createdAtEpochMs ASC
        """,
    )
    fun observeForExpense(expenseId: String): Flow<List<ExpensePhotoEntity>>

    @Query("SELECT * FROM expense_photos WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ExpensePhotoEntity?

    @Upsert
    suspend fun upsert(photo: ExpensePhotoEntity)

    @Upsert
    suspend fun upsertAll(photos: List<ExpensePhotoEntity>)

    @Query(
        """
        SELECT * FROM expense_photos
        WHERE syncStatus = 'PENDING'
        ORDER BY createdAtEpochMs ASC
        """,
    )
    suspend fun getPendingSync(): List<ExpensePhotoEntity>

    @Query("DELETE FROM expense_photos WHERE expenseId = :expenseId")
    suspend fun deleteForExpense(expenseId: String)
}
