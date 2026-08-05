package com.splitease.app.presentation.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthRateLimiterTest {
    @Test
    fun `allows attempts until max then locks`() {
        var now = 1_000L
        val limiter =
            AuthRateLimiter(
                maxAttempts = 3,
                windowMs = 60_000L,
                clock = { now },
            )

        repeat(2) {
            assertNull(limiter.remainingLockMs(AuthRateAction.LOGIN, "a@b.com"))
            limiter.recordFailure(AuthRateAction.LOGIN, "a@b.com")
        }
        assertNull(limiter.remainingLockMs(AuthRateAction.LOGIN, "a@b.com"))
        limiter.recordFailure(AuthRateAction.LOGIN, "a@b.com")

        val remaining = limiter.remainingLockMs(AuthRateAction.LOGIN, "a@b.com")
        assertEquals(60_000L, remaining)

        now += 30_000L
        assertEquals(30_000L, limiter.remainingLockMs(AuthRateAction.LOGIN, "a@b.com"))

        now += 30_000L
        assertNull(limiter.remainingLockMs(AuthRateAction.LOGIN, "a@b.com"))
    }

    @Test
    fun `success clears failures`() {
        val limiter = AuthRateLimiter(maxAttempts = 2, windowMs = 60_000L)
        limiter.recordFailure(AuthRateAction.LOGIN, "a@b.com")
        limiter.recordSuccess(AuthRateAction.LOGIN, "a@b.com")
        limiter.recordFailure(AuthRateAction.LOGIN, "a@b.com")
        assertNull(limiter.remainingLockMs(AuthRateAction.LOGIN, "a@b.com"))
    }

    @Test
    fun `emails are normalized`() {
        var now = 0L
        val limiter =
            AuthRateLimiter(maxAttempts = 2, windowMs = 60_000L, clock = { now })
        limiter.recordFailure(AuthRateAction.SIGNUP, "A@B.com")
        limiter.recordFailure(AuthRateAction.SIGNUP, " a@b.com ")
        assertTrue((limiter.remainingLockMs(AuthRateAction.SIGNUP, "a@b.com") ?: 0L) > 0L)
    }

    @Test
    fun `buckets are per email and action`() {
        val limiter = AuthRateLimiter(maxAttempts = 2, windowMs = 60_000L)
        limiter.recordFailure(AuthRateAction.LOGIN, "a@b.com")
        limiter.recordFailure(AuthRateAction.LOGIN, "a@b.com")
        assertTrue((limiter.remainingLockMs(AuthRateAction.LOGIN, "a@b.com") ?: 0L) > 0L)
        assertNull(limiter.remainingLockMs(AuthRateAction.LOGIN, "other@b.com"))
        assertNull(limiter.remainingLockMs(AuthRateAction.SIGNUP, "a@b.com"))
        assertNull(limiter.remainingLockMs(AuthRateAction.FORGOT_PASSWORD, "a@b.com"))
    }
}
