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

/**
 * Curated currency options for Settings (full catalog is Phase 7).
 */
object AppCurrencies {
    /** Default when the user has not chosen yet. */
    const val DEFAULT = "INR"

    /** Display options: code → short label. */
    val OPTIONS: List<Pair<String, String>> =
        listOf(
            "INR" to "Indian Rupee",
            "USD" to "US Dollar",
            "EUR" to "Euro",
            "GBP" to "British Pound",
            "AED" to "UAE Dirham",
            "SGD" to "Singapore Dollar",
            "AUD" to "Australian Dollar",
            "CAD" to "Canadian Dollar",
            "JPY" to "Japanese Yen",
            "CHF" to "Swiss Franc",
        )
}
