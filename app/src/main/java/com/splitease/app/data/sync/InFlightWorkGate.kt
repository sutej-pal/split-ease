package com.splitease.app.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicInteger

/**
 * Counts overlapping background writes so sign-out can wait until they finish.
 *
 * [begin] must run on the calling thread **before** the work is launched, so a
 * following [awaitIdle] cannot miss a job that has not started yet.
 */
class InFlightWorkGate {
    private val inFlight = AtomicInteger(0)
    private val idle = MutableStateFlow(true)

    /** True when no tracked work is running. */
    val isIdle: Boolean
        get() = inFlight.get() == 0

    /** Marks one unit of work as started. */
    fun begin() {
        if (inFlight.incrementAndGet() == 1) {
            idle.value = false
        }
    }

    /** Marks one unit of work as finished. */
    fun end() {
        val left = inFlight.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        if (left == 0) {
            idle.value = true
        }
    }

    /** Suspends until [begin]/[end] pairs have drained (already idle returns immediately). */
    suspend fun awaitIdle() {
        idle.first { it }
    }
}
