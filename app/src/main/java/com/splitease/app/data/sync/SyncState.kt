package com.splitease.app.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Progress of the **post-login initial hydrate** (full groups/expenses/payments pull).
 *
 * Regular app opens and pull-to-refresh never enter [IN_PROGRESS] once a hydrate has
 * completed for the signed-in user on this device — those paths keep showing cached
 * totals immediately.
 */
enum class SyncState {
    /** No first-login hydrate in flight (cached data, or process just started). */
    IDLE,

    /** First-login full sync is running; UI must not observe live Room balances. */
    IN_PROGRESS,

    /** First-login full sync finished successfully this process. */
    COMPLETE,

    /** First-login full sync failed; UI should offer retry, not partial totals. */
    FAILED,
}

/**
 * Whether Home totals and the Activity list must stay off the live DB
 * (skeleton or error instead of incrementing partial writes).
 */
val SyncState.shouldFreezeBalances: Boolean
    get() = this == SyncState.IN_PROGRESS || this == SyncState.FAILED

/**
 * In-memory sync phase plus optional persisted "hydrate already done for this user".
 *
 * Persistence is how subsequent process starts skip the skeleton: Room already has
 * data, and [hasCompleted] is true even though [state] starts at [SyncState.IDLE].
 *
 * @param loadCompletedUserId Reads the user id whose initial hydrate finished, or null.
 * @param persistCompletedUserId Stores that user id, or null to clear (sign-out).
 */
class InitialHydrateGate(
    private val loadCompletedUserId: () -> String?,
    private val persistCompletedUserId: (String?) -> Unit,
) {
    private val _state = MutableStateFlow(SyncState.IDLE)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /**
     * True when a full initial hydrate already succeeded for [userId] on this device.
     */
    fun hasCompleted(userId: String): Boolean =
        userId.isNotBlank() && loadCompletedUserId() == userId

    /**
     * Marks [SyncState.IN_PROGRESS] for a first-login hydrate.
     *
     * No-ops when [userId] already completed (subsequent opens must not flicker).
     */
    fun onStarted(userId: String) {
        if (userId.isBlank() || hasCompleted(userId)) return
        _state.value = SyncState.IN_PROGRESS
    }

    /**
     * Persists success and moves to [SyncState.COMPLETE].
     */
    fun onSuccess(userId: String) {
        if (userId.isBlank()) return
        persistCompletedUserId(userId)
        _state.value = SyncState.COMPLETE
    }

    /**
     * Moves [IN_PROGRESS] → [FAILED]. Ignores other states so a background refresh
     * failure cannot clobber a completed first hydrate.
     */
    fun onFailure() {
        if (_state.value == SyncState.IN_PROGRESS) {
            _state.value = SyncState.FAILED
        }
    }

    /**
     * Clears persistence and returns to [IDLE] (sign-out / account switch).
     */
    fun reset() {
        persistCompletedUserId(null)
        _state.value = SyncState.IDLE
    }
}
