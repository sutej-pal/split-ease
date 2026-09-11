package com.splitease.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitease.app.domain.model.SyncStatus

/**
 * Room row for [com.splitease.app.domain.model.ExpenseComment].
 */
@Entity(
    tableName = "expense_comments",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("expenseId"),
        Index("createdAtEpochMs"),
        Index("syncStatus"),
    ],
)
data class ExpenseCommentEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val authorUserId: String,
    val body: String,
    val kind: String,
    val createdAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
