package com.splitease.app.presentation.auth

/**
 * Which auth flow a throttle bucket belongs to (buckets are per action + email).
 */
enum class AuthRateAction {
    LOGIN,
    SIGNUP,
    FORGOT_PASSWORD,
    RESET_PASSWORD,
}

/**
 * Per-email auth attempt throttle (in-memory).
 *
 * After [maxAttempts] recorded hits within [windowMs], further calls for that
 * action + email are blocked until the lockout expires. Success clears the bucket.
 *
 * @property maxAttempts Hits allowed before lockout (default 5).
 * @property windowMs Lockout / rolling window length (default 15 minutes).
 * @property clock Injectable clock for unit tests.
 */
class AuthRateLimiter(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private data class Bucket(
        var failures: Int = 0,
        var windowStartMs: Long = 0L,
        var lockedUntilMs: Long = 0L,
    )

    private val buckets = mutableMapOf<String, Bucket>()

    /**
     * @return Remaining lockout milliseconds if blocked, or `null` if the attempt may proceed.
     */
    fun remainingLockMs(
        action: AuthRateAction,
        email: String,
    ): Long? {
        val key = bucketKey(action, email) ?: return null
        val now = clock()
        val bucket = buckets[key] ?: return null
        if (bucket.lockedUntilMs > now) {
            return bucket.lockedUntilMs - now
        }
        if (bucket.lockedUntilMs > 0L && bucket.lockedUntilMs <= now) {
            buckets.remove(key)
        }
        return null
    }

    /**
     * Records a hit (failed auth, or a send that should be throttled such as forgot-password).
     */
    fun recordFailure(
        action: AuthRateAction,
        email: String,
    ) {
        val key = bucketKey(action, email) ?: return
        val now = clock()
        val bucket = buckets.getOrPut(key) { Bucket(windowStartMs = now) }

        if (bucket.lockedUntilMs > now) return

        if (now - bucket.windowStartMs >= windowMs) {
            bucket.failures = 0
            bucket.windowStartMs = now
            bucket.lockedUntilMs = 0L
        }

        bucket.failures += 1
        if (bucket.failures >= maxAttempts) {
            bucket.lockedUntilMs = now + windowMs
        }
    }

    /** Clears the bucket after a successful completion of [action] for [email]. */
    fun recordSuccess(
        action: AuthRateAction,
        email: String,
    ) {
        bucketKey(action, email)?.let { buckets.remove(it) }
    }

    private fun bucketKey(
        action: AuthRateAction,
        email: String,
    ): String? {
        val normalized = email.trim().lowercase()
        if (normalized.isEmpty()) return null
        return "${action.name}:$normalized"
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5
        const val DEFAULT_WINDOW_MS = 15 * 60 * 1000L
    }
}
