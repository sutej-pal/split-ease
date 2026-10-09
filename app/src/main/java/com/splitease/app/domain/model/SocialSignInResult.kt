package com.splitease.app.domain.model

/**
 * Outcome of a native social sign-in (Google ID token → Supabase session).
 *
 * @property isNewUser True when Auth created this user on this sign-in
 *   (`created_at` ≈ `last_sign_in_at`). Used to send the one-time welcome email.
 */
data class SocialSignInResult(
    val isNewUser: Boolean,
)
