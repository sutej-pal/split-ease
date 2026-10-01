package com.splitease.app.domain.model

/**
 * A receipt / attachment image on an [Expense].
 *
 * @property id Stable UUID.
 * @property expenseId Parent expense.
 * @property createdByUserId Who attached the photo.
 * @property localPath Absolute path under app files when present on this device.
 * @property remoteUrl Public Supabase Storage URL when uploaded.
 * @property createdAtEpochMs When the photo was attached.
 * @property syncStatus Offline-first sync bookmark.
 */
data class ExpensePhoto(
    val id: String,
    val expenseId: String,
    val createdByUserId: String,
    val localPath: String? = null,
    val remoteUrl: String? = null,
    val createdAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
) {
    /** Prefer a local file when available; otherwise the cloud URL. */
    fun displayUri(): String? = localPath?.takeIf { it.isNotBlank() } ?: remoteUrl?.takeIf { it.isNotBlank() }
}
