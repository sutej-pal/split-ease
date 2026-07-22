package com.splitease.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitease.app.domain.model.SyncStatus
import java.math.BigDecimal

/**
 * Room row for [com.splitease.app.domain.model.ExpenseSplit].
 */
@Entity(
    tableName = "expense_splits",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("expenseId"),
        Index("userId"),
        Index(value = ["expenseId", "userId"], unique = true),
    ],
)
data class ExpenseSplitEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val userId: String,
    val owedAmount: BigDecimal,
    val percentage: BigDecimal? = null,
    val shares: Int? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
