package com.splitease.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitease.app.domain.model.RecurrenceFrequency
import com.splitease.app.domain.model.SplitType
import com.splitease.app.domain.model.SyncStatus
import java.math.BigDecimal

/**
 * Room row for [com.splitease.app.domain.model.Expense].
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["paidByUserId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("paidByUserId"),
        Index("groupId"),
        Index("categoryId"),
        Index("expenseDateEpochMs"),
    ],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val description: String,
    val amount: BigDecimal,
    val currencyCode: String,
    val categoryId: String? = null,
    val paidByUserId: String,
    val groupId: String? = null,
    val expenseDateEpochMs: Long,
    val splitType: SplitType,
    val isRecurring: Boolean = false,
    val recurrenceFrequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val nextOccurrenceEpochMs: Long? = null,
    val recurringTemplateId: String? = null,
    val notes: String? = null,
    val remoteId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
