package com.splitease.app.data.sync

/**
 * Guards marking an expense SYNCED after a cloud upsert.
 *
 * A newer local [updatedAt] means the row we pushed is stale; leave it PENDING.
 */
internal object ExpensePushPolicy {
    fun shouldMarkSynced(
        currentUpdatedAtEpochMs: Long?,
        pushedUpdatedAtEpochMs: Long,
    ): Boolean = currentUpdatedAtEpochMs == null || currentUpdatedAtEpochMs <= pushedUpdatedAtEpochMs
}
