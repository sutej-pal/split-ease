package com.splitease.app.domain.repository

import com.splitease.app.domain.model.AuthSession
import com.splitease.app.domain.model.AuthUser
import com.splitease.app.domain.model.SignUpResult
import com.splitease.app.domain.model.SocialSignInResult
import com.splitease.app.domain.settings.AppCurrencies
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
     * Returns the currently signed-in user snapshot, if any.
     *
     * @return [AuthUser] when authenticated, otherwise null.
     */
    suspend fun getSignedInUserOrNull(): AuthUser?

    /**
     * Creates an account with email and password.
     *
     * @param email User email.
     * @param password Password (min length enforced by Supabase).
     * @param displayName Optional display name stored in user metadata.
     * @param phoneCountryCode Optional dialing code (e.g. `+91`).
     * @param phoneNumber Optional national phone number.
     * @param currencyCode Preferred ISO 4217 currency stored in metadata + profile.
     * @param photoUri Optional local avatar URI (content:// or file path). Copied into
     * app-private storage as a compressed JPEG at signup; uploaded after email verify.
     * @return [Result] of [SignUpResult] (session created vs pending email confirmation).
     */
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        phoneCountryCode: String = "+91",
        phoneNumber: String = "",
        currencyCode: String = AppCurrencies.DEFAULT,
        photoUri: String? = null,
    ): Result<SignUpResult>

    /**
     * Signs in with email and password. On success a session is established;
     * callers should hydrate local data via [ensureLocalProfile].
     *
     * @param email User email.
     * @param password Password.
     * @return [Result] success or failure with message.
     */
    suspend fun signIn(email: String, password: String): Result<Unit>

    /**
     * Signs in (or signs up) with a Google ID token from Credential Manager.
     * Email is already verified by Google — no OTP gate.
     *
     * @param idToken Google ID token (JWT).
     * @param rawNonce Unhashed nonce that was SHA-256'd for the Google request.
     * @return [Result] of [SocialSignInResult].
     */
    suspend fun signInWithGoogle(
        idToken: String,
        rawNonce: String,
    ): Result<SocialSignInResult>

    /**
     * Returns whether [email] already has an Auth account (for login/signup messaging).
     * Safe to call while signed out.
     *
     * @param email Candidate account email.
     * @return true when registered; false when not; failure when the check could not run.
     */
    suspend fun isEmailRegistered(email: String): Result<Boolean>

    /**
     * Returns whether [phoneNumber] (with [phoneCountryCode]) is already used on a profile
     * or auth user metadata. Safe to call while signed out. Blank phone → false.
     *
     * @param phoneCountryCode Dialing code (e.g. `+91`).
     * @param phoneNumber National phone number digits.
     * @return true when registered; false when not; failure when the check could not run.
     */
    suspend fun isPhoneRegistered(
        phoneCountryCode: String,
        phoneNumber: String,
    ): Result<Boolean>

    /**
     * Sends a 6-digit email OTP for the post-password login gate.
     * Does not create new accounts (`createUser = false`).
     *
     * @param email Account email that just passed password sign-in.
     * @return [Result] success or failure with message.
     */
    suspend fun sendLoginOtp(email: String): Result<Unit>

    /**
     * Verifies the login email OTP and hydrates the local profile.
     *
     * @param email Account email that received the code.
     * @param token Six-digit OTP from the email (`{{ .Token }}` in Magic Link template).
     * @return [Result] success or failure with message.
     */
    suspend fun verifyLoginOtp(email: String, token: String): Result<Unit>

    /**
     * Resends the signup confirmation email (with a fresh OTP) when Confirm email is enabled.
     *
     * @param email Account email.
     * @return [Result] success or failure with message.
     */
    suspend fun resendSignupConfirmation(email: String): Result<Unit>

    /**
     * Verifies the signup OTP emailed after [signUp] when Confirm email is enabled
     * (6-digit code from Supabase mailer).
     *
     * On success, establishes a session and hydrates the local profile (same as sign-in).
     *
     * @param email Account email that received the code.
     * @param token Six-digit OTP from the confirmation email (`{{ .Token }}`).
     * @return [Result] success or failure with message.
     */
    suspend fun verifySignupOtp(email: String, token: String): Result<Unit>

    /**
     * Requests a password-reset email containing a 6-digit recovery OTP
     * (when the Send Email hook / Reset password template includes `{{ .Token }}`).
     *
     * Unknown emails still succeed (Supabase returns 200 and sends nothing). Rate-limit
     * and hook delivery failures are returned so the UI can ask the user to wait —
     * those do not reveal registration status.
     *
     * @param email Account email (may or may not exist).
     * @return [Result] success, or failure for rate-limit / delivery errors.
     */
    suspend fun requestPasswordReset(email: String): Result<Unit>

    /**
     * Verifies the recovery OTP from [requestPasswordReset] and establishes a session
     * so [updatePassword] can set a new password.
     *
     * @param email Account email that received the code.
     * @param token Six-digit OTP from the reset email (`{{ .Token }}`).
     * @return [Result] success or failure with message.
     */
    suspend fun verifyRecoveryOtp(email: String, token: String): Result<Unit>

    /**
     * Updates the signed-in user's password (call after [verifyRecoveryOtp] or while signed in).
     *
     * @param newPassword New password (min length enforced by Supabase / callers).
     * @return [Result] success or failure with message.
     */
    suspend fun updatePassword(newPassword: String): Result<Unit>

    /**
     * Signs out the current user and clears the local session.
     *
     * Best-effort: flushes PENDING local writes to the server before the
     * auth session is dropped and Room is wiped.
     *
     * @return [Result] success or failure with message.
     */
    suspend fun signOut(): Result<Unit>

    /**
     * Updates the signed-in user's display name in Supabase metadata, local Room, and
     * the remote `profiles` table (best-effort).
     *
     * @param displayName New display name.
     * @return [Result] success or failure with message.
     */
    suspend fun updateDisplayName(displayName: String): Result<Unit>

    /**
     * Updates the signed-in user's profile photo. Copies [photoUri] into app-private storage,
     * uploads it to Storage when possible, then persists the public URL (or local path) in
     * Supabase metadata, Room, and remote `profiles`.
     *
     * @param photoUri Content or file URI string from gallery / camera.
     * @return [Result] success or failure with message.
     */
    suspend fun updateProfilePhoto(photoUri: String): Result<Unit>

    /**
     * Updates the signed-in user's preferred currency in Supabase metadata, Room, and
     * remote `profiles` (best-effort). Does not change the local app default currency
     * preference — callers should update [com.splitease.app.domain.settings.AppSettingsRepository]
     * separately when needed.
     *
     * @param currencyCode ISO 4217 code (e.g. `INR`).
     * @return [Result] success or failure with message.
     */
    suspend fun updatePreferredCurrency(currencyCode: String): Result<Unit>

    /**
     * Ensures the signed-in auth user exists in local Room (and remote profiles best-effort),
     * then flushes PENDING writes and pulls groups/expenses/payments from Supabase.
     * Safe to call on every cold start / session restore.
     *
     * @return [Result] success or failure with message.
     */
    suspend fun ensureLocalProfile(): Result<Unit>
}
