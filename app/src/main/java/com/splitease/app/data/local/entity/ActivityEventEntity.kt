package com.splitease.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for local activity-feed events.
 */
@Entity(
    tableName = "activity_events",
    indices = [
        Index(value = ["sortEpochMs"]),
        Index(value = ["relatedExpenseId"]),
        Index(value = ["actorUserId"]),
    ],
)
data class ActivityEventEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val subtitle: String,
    val amountLabel: String,
    val actorUserId: String,
    val relatedExpenseId: String?,
    val involvedUserIds: String,
    val sortEpochMs: Long,
)
