package com.splitease.app.domain.settings

/**
 * How long the app may stay in the background before biometric / device
 * credential authentication is required again.
 *
 * [IMMEDIATE] always prompts when returning from the background.
 */
enum class AuthTimeout(
    /** Idle grace period in milliseconds. */
    val millis: Long,
) {
    IMMEDIATE(0L),
    FIVE_SECONDS(5_000L),
    FIFTEEN_SECONDS(15_000L),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(5 * 60_000L),
    FIFTEEN_MINUTES(15 * 60_000L),
    ONE_HOUR(60 * 60_000L),
    ;

    companion object {
        /** Default when the preference has never been set. */
        val DEFAULT: AuthTimeout = FIVE_SECONDS

        /**
         * Parses a stored preference value.
         *
         * @param raw Persisted name, or null.
         * @return Matching timeout, or [DEFAULT] when missing/unknown.
         */
        fun fromStorage(raw: String?): AuthTimeout =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
