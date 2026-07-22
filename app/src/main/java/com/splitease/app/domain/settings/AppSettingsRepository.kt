package com.splitease.app.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * App-wide user preferences (local only).
 *
 * Currency is chosen in Settings and applied to new expenses and groups.
 */
interface AppSettingsRepository {
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
}
