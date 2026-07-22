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
}
