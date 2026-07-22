package com.splitease.app.domain.model

import java.math.BigDecimal

/**
 * A settlement payment from one user to another that reduces outstanding balances.
 *
 * @property id Stable local UUID.
 * @property fromUserId Payer.
 * @property toUserId Payee.
 * @property amount Amount transferred; always [BigDecimal].
 * @property currencyCode ISO 4217 code.
 * @property groupId Optional group context for the settlement.
 * @property note Optional memo (e.g. `"Cash"` / `"UPI"`).
 * @property paidAtEpochMs When the payment occurred.
 * @property remoteId Cloud id when synced.
 * @property createdAtEpochMs Creation timestamp.
 * @property updatedAtEpochMs Last mutation timestamp.
 * @property syncStatus Offline-first sync bookmark.
 */
data class Payment(
    val id: String,
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
