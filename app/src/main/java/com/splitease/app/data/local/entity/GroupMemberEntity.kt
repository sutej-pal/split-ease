package com.splitease.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitease.app.domain.model.MemberRole
import com.splitease.app.domain.model.SyncStatus

/**
 * Room row for [com.splitease.app.domain.model.GroupMember].
 */
@Entity(
    tableName = "group_members",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
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
        Index("groupId"),
        Index("userId"),
        Index(value = ["groupId", "userId"], unique = true),
    ],
)
data class GroupMemberEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val userId: String,
    val role: MemberRole,
    val joinedAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
