package com.splitease.app.domain.settings

import java.util.Currency
import java.util.Locale

/**
 * Supported app currencies (ISO 4217).
 *
 * Keep this as the single source of truth for codes and picker labels —
 * do not hardcode `"INR"` / `"USD"` elsewhere in production code.
 */
object AppCurrencies {
    const val INR = "INR"
    const val USD = "USD"

    /** Default when the user has not chosen yet. */
    const val DEFAULT = INR

    /** code → English display name (order shown in pickers). */
    val OPTIONS: List<Pair<String, String>> =
        listOf(
            INR to "Indian Rupee",
            USD to "US Dollar",
            "AED" to "United Arab Emirates Dirham",
            "AUD" to "Australian Dollar",
            "BRL" to "Brazilian Real",
            "CAD" to "Canadian Dollar",
            "CHF" to "Swiss Franc",
            "CNY" to "Chinese Yuan",
            "DKK" to "Danish Krone",
            "EGP" to "Egyptian Pound",
            "EUR" to "Euro",
            "GBP" to "British Pound",
            "HKD" to "Hong Kong Dollar",
            "IDR" to "Indonesian Rupiah",
            "ILS" to "Israeli New Shekel",
            "JPY" to "Japanese Yen",
            "KRW" to "South Korean Won",
            "MXN" to "Mexican Peso",
            "MYR" to "Malaysian Ringgit",
            "NOK" to "Norwegian Krone",
            "NZD" to "New Zealand Dollar",
            "PHP" to "Philippine Peso",
            "PLN" to "Polish Zloty",
            "RUB" to "Russian Ruble",
            "SAR" to "Saudi Riyal",
            "SEK" to "Swedish Krona",
            "SGD" to "Singapore Dollar",
            "THB" to "Thai Baht",
            "TRY" to "Turkish Lira",
            "TWD" to "New Taiwan Dollar",
            "VND" to "Vietnamese Dong",
            "ZAR" to "South African Rand",
        )

    private val supportedCodes: Set<String> = OPTIONS.map { it.first }.toSet()

    /**
     * Returns true when [code] is one of the supported currencies.
     */
    fun isSupported(code: String): Boolean = code.trim().uppercase() in supportedCodes

    /**
     * Normalizes [code] to a supported currency, or [DEFAULT] when blank/unknown.
     */
    fun normalizeOrDefault(code: String?): String {
        val normalized = code?.trim()?.uppercase().orEmpty()
        return if (normalized in supportedCodes) normalized else DEFAULT
    }

    /**
     * English label for [code], or the code itself when unknown.
     */
    fun labelOf(code: String): String =
        OPTIONS.firstOrNull { it.first == code.trim().uppercase() }?.second
            ?: code.trim().uppercase()

    /**
     * Filters [OPTIONS] by code or name substring (case-insensitive).
     *
     * @param query Free-text filter; blank returns all.
     */
    fun filter(query: String): List<Pair<String, String>> {
        val q = query.trim()
        if (q.isEmpty()) return OPTIONS
        return OPTIONS.filter { (code, name) ->
            code.contains(q, ignoreCase = true) || name.contains(q, ignoreCase = true)
        }
    }

    private val SYMBOL_OVERRIDES =
        mapOf(
            "AED" to "د.إ",
            "SAR" to "ر.س",
            "EGP" to "ج.م",
        )

    /**
     * Currency symbol for [code], or the code itself when the JDK has no symbol.
     */
    fun symbol(
        code: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        val normalized = code.trim().uppercase()
        if (normalized.isEmpty()) return DEFAULT
        SYMBOL_OVERRIDES[normalized]?.let { return it }
        return runCatching { Currency.getInstance(normalized).getSymbol(locale) }
            .getOrElse { normalized }
    }
}
