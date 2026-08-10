package com.splitease.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitease.app.domain.model.SyncStatus

/**
 * Room row for [com.splitease.app.domain.model.ExpensePhoto].
 */
@Entity(
    tableName = "expense_photos",
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
data class ExpensePhotoEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val createdByUserId: String,
    val localPath: String? = null,
    val remoteUrl: String? = null,
    val createdAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
