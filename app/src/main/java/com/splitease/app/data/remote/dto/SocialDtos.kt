package com.splitease.app.data.remote.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase `profiles` row.
 */
@Serializable
data class ProfileDto(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("phone_country_code") val phoneCountryCode: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("preferred_currency") val preferredCurrency: String? = null,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

/**
 * Supabase `friends` row.
 */
@Serializable
data class FriendDto(
    val id: String,
    @SerialName("owner_user_id") val ownerUserId: String,
    @SerialName("friend_user_id") val friendUserId: String,
    @SerialName("email_snapshot") val emailSnapshot: String,
    @SerialName("display_name_snapshot") val displayNameSnapshot: String,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

/**
 * Supabase `groups` row.
 */
@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    @SerialName("default_currency_code") val defaultCurrencyCode: String,
    @SerialName("created_by_user_id") val createdByUserId: String,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long,
    /**
     * Public Storage URL for the group detail banner.
     * Omitted when null so name/currency upserts do not wipe an existing cover.
     */
    @SerialName("cover_url")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val coverUrl: String? = null,
    /**
     * Public Storage URL for the square list/settings avatar.
     * Omitted when null so name/currency upserts do not wipe an existing photo.
     */
    @SerialName("photo_url")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val photoUrl: String? = null,
)

/**
 * Partial update used to clear or set [cover_url] without rewriting other group fields.
 */
@Serializable
data class GroupCoverUrlPatch(
    @SerialName("cover_url") val coverUrl: String?,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

/**
 * Partial update used to clear or set [photo_url] without rewriting other group fields.
 */
@Serializable
data class GroupPhotoUrlPatch(
    @SerialName("photo_url") val photoUrl: String?,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

/**
 * Supabase `group_members` row.
 */
@Serializable
data class GroupMemberDto(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("joined_at_epoch_ms") val joinedAtEpochMs: Long,
)

/**
 * Supabase `invites` row.
 */
@Serializable
data class InviteDto(
    val id: String,
    val token: String,
    @SerialName("inviter_user_id") val inviterUserId: String,
    val email: String,
    val kind: String,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("friend_row_id") val friendRowId: String? = null,
    val status: String,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
)

/**
 * Supabase `expenses` row (amount/owed stored as plain decimal strings).
 */
@Serializable
data class ExpenseDto(
    val id: String,
    val description: String,
    val amount: String,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("paid_by_user_id") val paidByUserId: String,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("expense_date_epoch_ms") val expenseDateEpochMs: Long,
    @SerialName("split_type") val splitType: String,
    val notes: String? = null,
    @SerialName("updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

/**
 * Supabase `expense_splits` row.
 */
@Serializable
data class ExpenseSplitDto(
    val id: String,
    @SerialName("expense_id") val expenseId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("owed_amount") val owedAmount: String,
    val percentage: String? = null,
    val shares: Int? = null,
    @SerialName("paid_amount") val paidAmount: String? = null,
    @SerialName("adjustment_amount") val adjustmentAmount: String? = null,
)

/**
 * Supabase `expense_comments` row.
 */
@Serializable
data class ExpenseCommentDto(
    val id: String,
    @SerialName("expense_id") val expenseId: String,
    @SerialName("author_user_id") val authorUserId: String,
    val body: String,
    val kind: String,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
)

/**
 * Supabase `expense_photos` row.
 */
@Serializable
data class ExpensePhotoDto(
    val id: String,
    @SerialName("expense_id") val expenseId: String,
    @SerialName("created_by_user_id") val createdByUserId: String,
    @SerialName("remote_url") val remoteUrl: String? = null,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
)
