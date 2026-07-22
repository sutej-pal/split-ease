package com.splitease.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitease.app.domain.model.SyncStatus
import java.math.BigDecimal

/**
 * Room row for [com.splitease.app.domain.model.Payment].
 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromUserId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["toUserId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("fromUserId"),
        Index("toUserId"),
        Index("groupId"),
        Index("paidAtEpochMs"),
    ],
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val fromUserId: String,
    val toUserId: String,
    val amount: BigDecimal,
    val currencyCode: String,
    val groupId: String? = null,
    val note: String? = null,
    val paidAtEpochMs: Long,
    val remoteId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
