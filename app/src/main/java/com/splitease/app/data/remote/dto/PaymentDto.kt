package com.splitease.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PostgREST row for `payments`.
 */
@Serializable
data class PaymentDto(
    val id: String,
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("to_user_id") val toUserId: String,
    val amount: String,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("group_id") val groupId: String? = null,
    val note: String? = null,
    @SerialName("paid_at_epoch_ms") val paidAtEpochMs: Long,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long,
)
