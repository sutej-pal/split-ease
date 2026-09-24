package com.splitease.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase `notification_prefs` row (mute all / muted groups).
 */
@Serializable
data class NotificationPrefsDto(
    @SerialName("user_id") val userId: String,
    @SerialName("mute_all") val muteAll: Boolean = false,
    @SerialName("muted_group_ids") val mutedGroupIds: List<String> = emptyList(),
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long,
)
