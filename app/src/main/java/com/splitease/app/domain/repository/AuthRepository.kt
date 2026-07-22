package com.splitease.app.domain.repository

import com.splitease.app.domain.model.AuthSession
import kotlinx.coroutines.flow.Flow

/**
 * Authentication operations backed by Supabase Auth.
 */
interface AuthRepository {
    /**
     * Observes the current auth session (loading / signed out / signed in).
     *
     * @return Cold [Flow] of [AuthSession] updates.
     */
    fun observeSession(): Flow<AuthSession>

    /**
     * Creates an account with email and password.
     *
     * @param email User email.
     * @param password Password (min length enforced by Supabase).
     * @param displayName Optional display name stored in user metadata.
     * @return [Result] success or failure with message.
     */
    suspend fun signUp(email: String, password: String, displayName: String): Result<Unit>

    /**
     * Signs in with email and password.
     *
     * @param email User email.
     * @param password Password.
     * @return [Result] success or failure with message.
     */
    suspend fun signIn(email: String, password: String): Result<Unit>

    /**
     * Sends a password-reset email.
     *
     * @param email Account email.
     * @return [Result] success or failure with message.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /**
     * Signs out the current user and clears the local session.
     *
     * @return [Result] success or failure with message.
     */
    suspend fun signOut(): Result<Unit>

    /**
     * Ensures the signed-in auth user exists in local Room (and remote profiles best-effort).
     * Safe to call on every cold start / session restore.
     *
     * @return [Result] success or failure with message.
     */
    suspend fun ensureLocalProfile(): Result<Unit>
}
