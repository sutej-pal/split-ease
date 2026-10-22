package com.splitease.app.domain.model

/**
 * A person who can participate in expenses, friendships, and groups.
 *
 * @property id Stable local UUID primary key.
 * @property email Login / invite identity; unique when present.
 * @property displayName Human-readable name shown in the UI.
 * @property photoUrl Optional avatar URL (local path or remote).
 * @property phoneCountryCode Optional dialing code (e.g. `+91`).
 * @property phoneNumber Optional national phone number digits.
 * @property preferredCurrency Optional ISO 4217 default currency from signup/profile.
 * @property remoteId Firestore document id once synced; null if local-only.
 * @property createdAtEpochMs Creation timestamp (UTC epoch millis).
 * @property updatedAtEpochMs Last local mutation timestamp (UTC epoch millis).
 * @property syncStatus Offline-first sync bookmark.
 */
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val phoneCountryCode: String? = null,
    val phoneNumber: String? = null,
    val preferredCurrency: String? = null,
    val remoteId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)
