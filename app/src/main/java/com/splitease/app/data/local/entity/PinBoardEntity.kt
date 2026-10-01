package com.splitease.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitease.app.domain.model.SyncStatus

/**
 * Room row for a group's Pin Board content.
 */
@Entity(
    tableName = "pin_boards",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("groupId")
    ],
)
data class PinBoardEntity(
    @PrimaryKey val groupId: String,
    val content: String,
    val updatedByUserId: String?,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
