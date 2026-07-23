package com.splitease.app.domain.model

/**
 * Authenticated session snapshot for presentation layer.
 *
 * @property userId Supabase auth user UUID.
 * @property email Account email.
 * @property displayName Preferred display name when available.
 * @property emailConfirmed True when Supabase has confirmed the email address.
 */
data class AuthUser(
    val userId: String,
    val email: String,
    val displayName: String,
    val emailConfirmed: Boolean = true,
)

/**
 * High-level auth session for navigation gating.
 */
sealed interface AuthSession {
    /** Session is still being restored from disk / network. */
    data object Loading : AuthSession

    /** No authenticated user. */
    data object SignedOut : AuthSession

    /**
     * Authenticated user is present.
     *
     * @property user Session user.
     */
    data class SignedIn(val user: AuthUser) : AuthSession
}
