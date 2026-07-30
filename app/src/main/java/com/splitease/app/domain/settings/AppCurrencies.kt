package com.splitease.app.domain.settings

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
}
