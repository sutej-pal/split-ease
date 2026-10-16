package com.splitease.app.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InitialHydrateGateTest {
    @Test
    fun started_sets_in_progress_when_never_completed() {
        val gate = gate()
        gate.onStarted("user-1")
        assertEquals(SyncState.IN_PROGRESS, gate.state.value)
        assertFalse(gate.hasCompleted("user-1"))
    }

    @Test
    fun started_is_noop_after_completed_hydrate() {
        var stored: String? = "user-1"
        val gate = gate(load = { stored }, persist = { stored = it })
        gate.onStarted("user-1")
        assertEquals(SyncState.IDLE, gate.state.value)
        assertTrue(gate.hasCompleted("user-1"))
    }

    @Test
    fun success_persists_and_completes() {
        var stored: String? = null
        val gate = gate(load = { stored }, persist = { stored = it })
        gate.onStarted("user-1")
        gate.onSuccess("user-1")
        assertEquals(SyncState.COMPLETE, gate.state.value)
        assertEquals("user-1", stored)
        assertTrue(gate.hasCompleted("user-1"))
    }

    @Test
    fun failure_from_in_progress_sets_failed() {
        val gate = gate()
        gate.onStarted("user-1")
        gate.onFailure()
        assertEquals(SyncState.FAILED, gate.state.value)
        assertFalse(gate.hasCompleted("user-1"))
    }

    @Test
    fun failure_does_not_clobber_idle_or_complete() {
        val idle = gate()
        idle.onFailure()
        assertEquals(SyncState.IDLE, idle.state.value)

        var stored: String? = null
        val done = gate(load = { stored }, persist = { stored = it })
        done.onStarted("user-1")
        done.onSuccess("user-1")
        done.onFailure()
        assertEquals(SyncState.COMPLETE, done.state.value)
    }

    @Test
    fun retry_after_failure_returns_to_in_progress() {
        val gate = gate()
        gate.onStarted("user-1")
        gate.onFailure()
        gate.onStarted("user-1")
        assertEquals(SyncState.IN_PROGRESS, gate.state.value)
    }

    @Test
    fun reset_clears_persistence_and_returns_idle() {
        var stored: String? = null
        val gate = gate(load = { stored }, persist = { stored = it })
        gate.onStarted("user-1")
        gate.onSuccess("user-1")
        gate.reset()
        assertEquals(SyncState.IDLE, gate.state.value)
        assertEquals(null, stored)
        assertFalse(gate.hasCompleted("user-1"))
    }

    @Test
    fun shouldFreezeBalances_only_while_in_progress_or_failed() {
        assertFalse(SyncState.IDLE.shouldFreezeBalances)
        assertTrue(SyncState.IN_PROGRESS.shouldFreezeBalances)
        assertFalse(SyncState.COMPLETE.shouldFreezeBalances)
        assertTrue(SyncState.FAILED.shouldFreezeBalances)
    }

    private fun gate(
        load: () -> String? = { null },
        persist: (String?) -> Unit = {},
    ) = InitialHydrateGate(
        loadCompletedUserId = load,
        persistCompletedUserId = persist,
    )
}
