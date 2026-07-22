package com.splitease.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitease.app.domain.model.SyncStatus

/**
 * Room row for [com.splitease.app.domain.model.Friend].
 */
@Entity(
    tableName = "friends",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerUserId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("ownerUserId"),
        Index(value = ["ownerUserId", "friendUserId"], unique = true),
    ],
)
data class FriendEntity(
    @PrimaryKey val id: String,
    val ownerUserId: String,
    val friendUserId: String,
    val emailSnapshot: String,
    val displayNameSnapshot: String,
    val remoteId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
