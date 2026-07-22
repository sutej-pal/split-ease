package com.splitease.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitease.app.domain.model.SyncStatus

/**
 * Room row for [com.splitease.app.domain.model.Category].
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String? = null,
    val isDefault: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
