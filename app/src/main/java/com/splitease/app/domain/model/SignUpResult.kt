package com.splitease.app.domain.model

/**
 * Outcome of [com.splitease.app.domain.repository.AuthRepository.signUp].
 *
 * When Supabase has **Confirm email** enabled, signup succeeds without a session
 * until the user verifies — [PendingEmailConfirmation]. When confirmation is off,
 * a session is created immediately — [SignedIn].
 */
sealed interface SignUpResult {
    /** Account created and session established (confirm email off, or already confirmed). */
    data object SignedIn : SignUpResult

    /**
     * Account created; user must open the confirmation email before signing in.
     *
     * @property email Address that received the confirmation link.
     */
    data class PendingEmailConfirmation(val email: String) : SignUpResult
}
