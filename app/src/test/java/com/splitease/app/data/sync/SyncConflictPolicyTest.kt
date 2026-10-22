package com.splitease.app.data.sync

import com.splitease.app.domain.model.SyncStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncConflictPolicyTest {
    @Test
    fun noLocalRow_appliesRemote() {
        assertTrue(
            SyncConflictPolicy.shouldApplyRemote(
                localUpdatedAtEpochMs = null,
                localSyncStatus = null,
                remoteUpdatedAtEpochMs = 100L,
            ),
        )
    }

    @Test
    fun synced_appliesEqualOrNewerRemote() {
        assertTrue(
            SyncConflictPolicy.shouldApplyRemote(
                localUpdatedAtEpochMs = 100L,
                localSyncStatus = SyncStatus.SYNCED,
                remoteUpdatedAtEpochMs = 100L,
            ),
        )
        assertTrue(
            SyncConflictPolicy.shouldApplyRemote(
                localUpdatedAtEpochMs = 100L,
                localSyncStatus = SyncStatus.SYNCED,
                remoteUpdatedAtEpochMs = 101L,
            ),
        )
    }

    @Test
    fun synced_skipsOlderRemote() {
        assertFalse(
            SyncConflictPolicy.shouldApplyRemote(
                localUpdatedAtEpochMs = 100L,
                localSyncStatus = SyncStatus.SYNCED,
                remoteUpdatedAtEpochMs = 99L,
            ),
        )
    }

    @Test
    fun pending_skipsEqualOrOlderRemote() {
        assertFalse(
            SyncConflictPolicy.shouldApplyRemote(
                localUpdatedAtEpochMs = 100L,
                localSyncStatus = SyncStatus.PENDING,
                remoteUpdatedAtEpochMs = 100L,
            ),
        )
        assertFalse(
            SyncConflictPolicy.shouldApplyRemote(
                localUpdatedAtEpochMs = 100L,
                localSyncStatus = SyncStatus.PENDING,
                remoteUpdatedAtEpochMs = 99L,
            ),
        )
    }

    @Test
    fun pending_appliesStrictlyNewerRemote() {
        assertTrue(
            SyncConflictPolicy.shouldApplyRemote(
                localUpdatedAtEpochMs = 100L,
                localSyncStatus = SyncStatus.PENDING,
                remoteUpdatedAtEpochMs = 101L,
            ),
        )
    }

    @Test
    fun localOnly_sameAsPending() {
        assertFalse(
            SyncConflictPolicy.shouldApplyRemote(
                localUpdatedAtEpochMs = 50L,
                localSyncStatus = SyncStatus.LOCAL_ONLY,
                remoteUpdatedAtEpochMs = 50L,
            ),
        )
        assertTrue(
            SyncConflictPolicy.shouldApplyRemote(
                localUpdatedAtEpochMs = 50L,
                localSyncStatus = SyncStatus.LOCAL_ONLY,
                remoteUpdatedAtEpochMs = 51L,
            ),
        )
    }
}
