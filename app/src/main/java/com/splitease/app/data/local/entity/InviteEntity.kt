package com.splitease.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitease.app.domain.model.InviteKind
import com.splitease.app.domain.model.InviteStatus
import com.splitease.app.domain.model.SyncStatus

/**
 * Room row for [com.splitease.app.domain.model.Invite].
 */
@Entity(
    tableName = "invites",
    indices = [
        Index(value = ["token"], unique = true),
        Index("email"),
        Index("inviterUserId"),
        Index("status"),
    ],
)
data class InviteEntity(
    @PrimaryKey val id: String,
    val token: String,
    val inviterUserId: String,
    val email: String,
    val kind: InviteKind,
    val groupId: String? = null,
    val friendRowId: String? = null,
    val status: InviteStatus = InviteStatus.PENDING,
    val createdAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
