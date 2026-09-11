package com.splitease.app.domain.repository

/**
 * Caps for UI feeds so screens never load unbounded Room histories into memory.
 *
 * Balance math still uses full [ExpenseRepository.observeInvolvingUser] / group observes.
 */
object FeedQueryLimits {
    /** Newest expenses/payments/events shown in Activity and ledgers. */
    const val UI_FEED = 200
}
