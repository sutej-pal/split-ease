package com.splitease.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase `device_tokens` row for FCM registration.
 */
@Serializable
data class DeviceTokenDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val token: String,
    val platform: String = "android",
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long,
)
