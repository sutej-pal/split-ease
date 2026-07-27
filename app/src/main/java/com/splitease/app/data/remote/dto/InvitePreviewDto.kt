package com.splitease.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Decoded payload from [public.get_invite_preview].
 */
@Serializable
data class InvitePreviewDto(
    val token: String,
    val kind: String,
    val email: String,
    @SerialName("inviter_name") val inviterName: String,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("group_name") val groupName: String? = null,
    val members: List<InvitePreviewMemberDto> = emptyList(),
)

/**
 * Member row inside an invite preview.
 */
@Serializable
data class InvitePreviewMemberDto(
    @SerialName("display_name") val displayName: String,
    @SerialName("already_joined") val alreadyJoined: Boolean = false,
)
