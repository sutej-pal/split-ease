package com.splitease.app.data.sync

import com.splitease.app.domain.model.SyncStatus

/**
 * Pull-side merge rules for offline-first rows that carry [updatedAtEpochMs].
 *
 * **Last-write-wins** on `updatedAtEpochMs`. Local `PENDING` / `LOCAL_ONLY` rows are
 * never replaced by an equal-or-older remote row so an unflushed edit is not clobbered
 * by a stale cloud copy (e.g. Realtime refresh before flush, or two devices editing).
 */
object SyncConflictPolicy {
    /**
     * Whether the remote snapshot should replace the local row on pull.
     *
     * @param localUpdatedAtEpochMs Local `updatedAtEpochMs`, or null if no local row.
     * @param localSyncStatus Local sync bookmark, or null if no local row.
     * @param remoteUpdatedAtEpochMs Remote `updated_at` / DTO timestamp.
     * @return true to apply the remote row; false to keep local as-is.
     */
    fun shouldApplyRemote(
        localUpdatedAtEpochMs: Long?,
        localSyncStatus: SyncStatus?,
        remoteUpdatedAtEpochMs: Long,
    ): Boolean {
        if (localUpdatedAtEpochMs == null || localSyncStatus == null) return true
        return when (localSyncStatus) {
            SyncStatus.PENDING, SyncStatus.LOCAL_ONLY ->
                remoteUpdatedAtEpochMs > localUpdatedAtEpochMs
            SyncStatus.SYNCED ->
                remoteUpdatedAtEpochMs >= localUpdatedAtEpochMs
        }
    }
}
