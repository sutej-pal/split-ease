package com.splitease.app.domain.model

/**
 * A named collection of members that share expenses.
 *
 * @property id Stable local UUID.
 * @property name Group display name.
 * @property defaultCurrencyCode ISO 4217 code used for new expenses (e.g. `"INR"`).
 * @property groupType Friends / Home / Other category for UI.
 * @property createdByUserId User who created the group.
 * @property remoteId Cloud id when synced.
 * @property createdAtEpochMs Creation timestamp.
 * @property updatedAtEpochMs Last mutation timestamp.
 * @property syncStatus Offline-first sync bookmark.
 */
data class Group(
    val id: String,
    val name: String,
    val defaultCurrencyCode: String,
    val groupType: GroupType = GroupType.OTHER,
    val createdByUserId: String,
    val remoteId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)

/**
 * Membership of a [User] inside a [Group].
 *
 * @property id Stable local UUID.
 * @property groupId Parent group id.
 * @property userId Member user id.
 * @property role Owner vs regular member.
 * @property joinedAtEpochMs When the member was added.
 * @property syncStatus Offline-first sync bookmark.
 */
data class GroupMember(
    val id: String,
    val groupId: String,
    val userId: String,
    val role: MemberRole,
    val joinedAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
