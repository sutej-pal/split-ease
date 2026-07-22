package com.splitease.app.domain.model

/**
 * A one-way friendship from [ownerUserId] toward [friendUserId].
 *
 * @property id Stable local UUID.
 * @property ownerUserId The local user who added the friend.
 * @property friendUserId The other user's id (may be a placeholder until invite accepted).
 * @property emailSnapshot Email used when the friend was added.
 * @property displayNameSnapshot Display name cached at add-time for offline lists.
 * @property remoteId Cloud id when synced.
 * @property createdAtEpochMs Creation timestamp.
 * @property updatedAtEpochMs Last mutation timestamp.
 * @property syncStatus Offline-first sync bookmark.
 */
data class Friend(
    val id: String,
    val ownerUserId: String,
    val friendUserId: String,
    val emailSnapshot: String,
    val displayNameSnapshot: String,
    val remoteId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
