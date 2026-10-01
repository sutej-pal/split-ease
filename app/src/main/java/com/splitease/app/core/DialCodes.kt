package com.splitease.app.core

/**
 * Dial codes shared by signup, invite signup, and the phone-confirm dialog.
 *
 * @property flag Emoji flag shown next to the code.
 * @property code E.164 prefix including `+`.
 * @property label Country name.
 */
data class DialCodeOption(
    val flag: String,
    val code: String,
    val label: String,
)

/**
 * Country calling codes the app offers. Longest-prefix matching uses [codesLongestFirst]
 * so `+971` wins over `+91`.
 */
object DialCodes {
    val options: List<DialCodeOption> =
        listOf(
            DialCodeOption("🇮🇳", "+91", "India"),
            DialCodeOption("🇺🇸", "+1", "United States"),
            DialCodeOption("🇬🇧", "+44", "United Kingdom"),
            DialCodeOption("🇨🇦", "+1", "Canada"),
            DialCodeOption("🇦🇺", "+61", "Australia"),
            DialCodeOption("🇦🇪", "+971", "United Arab Emirates"),
            DialCodeOption("🇸🇬", "+65", "Singapore"),
            DialCodeOption("🇩🇪", "+49", "Germany"),
            DialCodeOption("🇫🇷", "+33", "France"),
            DialCodeOption("🇯🇵", "+81", "Japan"),
        )

    /** Flag for [code], or India when the code is not in [options]. */
    fun flagFor(code: String): String =
        options.firstOrNull { it.code == code }?.flag ?: options.first().flag

    /** Unique codes, longest first, for E.164 prefix splits. */
    fun codesLongestFirst(): List<String> =
        options
            .map { it.code }
            .distinct()
            .sortedByDescending { it.length }
}
