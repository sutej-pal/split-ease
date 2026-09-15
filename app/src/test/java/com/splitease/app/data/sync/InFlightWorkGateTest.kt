package com.splitease.app.data.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InFlightWorkGateTest {
    @Test
    fun starts_idle() {
        val gate = InFlightWorkGate()
        assertTrue(gate.isIdle)
    }

    @Test
    fun begin_end_returns_to_idle() {
        val gate = InFlightWorkGate()
        gate.begin()
        assertFalse(gate.isIdle)
        gate.end()
        assertTrue(gate.isIdle)
    }

    @Test
    fun overlapping_work_stays_busy_until_last_end() {
        val gate = InFlightWorkGate()
        gate.begin()
        gate.begin()
        gate.end()
        assertFalse(gate.isIdle)
        gate.end()
        assertTrue(gate.isIdle)
    }

    @Test
    fun extra_end_does_not_underflow() {
        val gate = InFlightWorkGate()
        gate.end()
        assertTrue(gate.isIdle)
        gate.begin()
        assertFalse(gate.isIdle)
        gate.end()
        gate.end()
        assertTrue(gate.isIdle)
        gate.begin()
        assertFalse(gate.isIdle)
    }

    @Test
    fun awaitIdle_returns_immediately_when_already_idle() =
        runTest {
            val gate = InFlightWorkGate()
            gate.awaitIdle()
            assertTrue(gate.isIdle)
        }

    @Test
    fun awaitIdle_resumes_after_end() =
        runTest {
            val gate = InFlightWorkGate()
            gate.begin()
            val waiter = async { gate.awaitIdle() }
            advanceUntilIdle()
            assertFalse(waiter.isCompleted)
            gate.end()
            waiter.await()
            assertTrue(gate.isIdle)
        }
}
