package com.splitease.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitease.app.domain.model.GroupType
import com.splitease.app.domain.model.SyncStatus

/**
 * Room row for [com.splitease.app.domain.model.Group].
 */
@Entity(
    tableName = "groups",
    indices = [Index("createdByUserId")],
)
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val defaultCurrencyCode: String,
    val groupType: GroupType = GroupType.OTHER,
    val createdByUserId: String,
    val remoteId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
