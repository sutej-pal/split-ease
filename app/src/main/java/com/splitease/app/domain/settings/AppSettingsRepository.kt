package com.splitease.app.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * App-wide user preferences (local only).
 *
 * Currency is chosen in Settings and applied to new expenses and groups.
 */
interface AppSettingsRepository {
    companion object {
        /**
         * Sentinel for [getPendingInviteOpenTarget] / [setPendingInviteOpenTarget]:
         * open the Friends tab after invite accept (friend invite, no group).
         */
        const val PENDING_INVITE_OPEN_FRIENDS = "__friends__"
    }

    /**
     * Observes the active ISO 4217 currency code (e.g. `"INR"`).
     *
     * @return Cold [Flow]; always emits at least the default.
     */
    fun observeCurrencyCode(): Flow<String>

    /**
     * Reads the current currency once.
     *
     * @return ISO 4217 code.
     */
    suspend fun getCurrencyCode(): String

    /**
     * Persists the app-wide currency.
     *
     * @param code ISO 4217 code (normalized to uppercase).
     */
    suspend fun setCurrencyCode(code: String)

    /**
     * Observes whether debt simplification is enabled for [groupId].
     * Defaults to `true` when unset.
     *
     * @param groupId Local group id.
     * @return Cold [Flow] of the preference.
     */
    fun observeSimplifyGroupDebts(groupId: String): Flow<Boolean>

    /**
     * Observes all per-group simplify-debt preferences (missing keys ⇒ enabled).
     *
     * @return Cold [Flow] of groupId → enabled.
     */
    fun observeSimplifyGroupDebtsMap(): Flow<Map<String, Boolean>>

    /**
     * Reads whether debt simplification is enabled for [groupId].
     *
     * @param groupId Local group id.
     * @return `true` when simplification is on (default).
     */
    suspend fun getSimplifyGroupDebts(groupId: String): Boolean

    /**
     * Persists the per-group debt-simplification preference.
     *
     * @param groupId Local group id.
     * @param enabled When true, balances use minimized who-owes-whom transfers.
     */
    suspend fun setSimplifyGroupDebts(groupId: String, enabled: Boolean)

    /**
     * Observes the appearance preference.
     *
     * @return Cold [Flow]; defaults to [ThemeMode.SYSTEM].
     */
    fun observeThemeMode(): Flow<ThemeMode>

    /**
     * Reads the appearance preference once.
     *
     * @return Current [ThemeMode].
     */
    suspend fun getThemeMode(): ThemeMode

    /**
     * Persists the appearance preference.
     *
     * @param mode Light, dark, or follow system.
     */
    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * Observes whether opening the app requires biometrics / device credential.
     *
     * @return Cold [Flow]; defaults to `false`.
     */
    fun observeBiometricLockEnabled(): Flow<Boolean>

    /**
     * Reads the biometric app-lock preference once.
     *
     * @return `true` when lock is enabled.
     */
    suspend fun getBiometricLockEnabled(): Boolean

    /**
     * Persists the biometric app-lock preference.
     *
     * @param enabled When true, require auth after the configured timeout.
     */
    suspend fun setBiometricLockEnabled(enabled: Boolean)

    /**
     * Observes the idle timeout before re-auth is required.
     *
     * @return Cold [Flow]; defaults to [AuthTimeout.DEFAULT].
     */
    fun observeAuthTimeout(): Flow<AuthTimeout>

    /**
     * Reads the auth timeout once.
     *
     * @return Current [AuthTimeout].
     */
    suspend fun getAuthTimeout(): AuthTimeout

    /**
     * Persists the auth timeout.
     *
     * @param timeout Grace period before re-auth when returning to the app.
     */
    suspend fun setAuthTimeout(timeout: AuthTimeout)

    /**
     * Observes whether the signed-in user has completed post-signup onboarding.
     *
     * @return Cold [Flow]; defaults to `false`.
     */
    fun observeOnboardingComplete(): Flow<Boolean>

    /**
     * Reads the onboarding-complete flag once.
     *
     * @return `true` when the user finished the setup wizard.
     */
    suspend fun getOnboardingComplete(): Boolean

    /**
     * Persists the onboarding-complete flag.
     *
     * @param complete `true` after the user finishes setup.
     */
    suspend fun setOnboardingComplete(complete: Boolean)

    /**
     * Reads whether onboarding-start email has already been sent for a user.
     *
     * @param userId Signed-in user id.
     * @return `true` when onboarding-start email was already sent.
     */
    suspend fun getOnboardingEmailSent(userId: String): Boolean

    /**
     * Persists onboarding-start email sent state for a user.
     *
     * @param userId Signed-in user id.
     * @param sent `true` after successful send.
     */
    suspend fun setOnboardingEmailSent(userId: String, sent: Boolean)

    /**
     * Observes a pending invite token from a deep link (awaiting signup / OTP / accept).
     *
     * @return Cold [Flow]; emits `null` when none is stored.
     */
    fun observePendingInviteToken(): Flow<String?>

    /**
     * Reads the pending invite token once.
     *
     * @return Token string, or null.
     */
    suspend fun getPendingInviteToken(): String?

    /**
     * Persists or clears the pending invite token.
     *
     * @param token Opaque invite token, or null to clear.
     */
    suspend fun setPendingInviteToken(token: String?)

    /**
     * Observes where to navigate after an invite is accepted.
     *
     * Values:
     * - a group id → open that group
     * - [PENDING_INVITE_OPEN_FRIENDS] → Friends tab
     * - `null` → no pending invite navigation
     *
     * Survives token clear after accept until the UI consumes it.
     */
    fun observePendingInviteOpenTarget(): Flow<String?>

    /**
     * Reads the pending post-invite navigation target once.
     *
     * @return Group id, [PENDING_INVITE_OPEN_FRIENDS], or null.
     */
    suspend fun getPendingInviteOpenTarget(): String?

    /**
     * Persists or clears where to navigate after invite accept.
     *
     * @param target Group id, [PENDING_INVITE_OPEN_FRIENDS], or null to clear.
     */
    suspend fun setPendingInviteOpenTarget(target: String?)

    /**
     * Observes the in-app language preference.
     *
     * @return Cold [Flow]; defaults to [AppLocale.SYSTEM].
     */
    fun observeAppLocale(): Flow<AppLocale>

    /**
     * Reads the language preference once.
     *
     * @return Current [AppLocale].
     */
    suspend fun getAppLocale(): AppLocale

    /**
     * Persists the language preference and applies it process-wide.
     *
     * @param locale System or pinned BCP-47 language.
     */
    suspend fun setAppLocale(locale: AppLocale)
}
