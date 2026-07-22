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

    /** @param id Local UUID. @return Payment or null. */
    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PaymentEntity?

    /** Inserts or replaces [payment]. */
    @Upsert
    suspend fun upsert(payment: PaymentEntity)

    /** Deletes payment [id]. */
    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deleteById(id: String)
}
