package com.splitease.app.domain.settings

/**
 * In-app language preference.
 *
 * [SYSTEM] follows the device locale list; other values pin a BCP-47 language tag.
 */
enum class AppLocale(
    /** BCP-47 language tag, or empty for system default. */
    val tag: String,
) {
    SYSTEM(""),
    ENGLISH("en"),
    SPANISH("es"),
    FRENCH("fr"),
    GERMAN("de"),
    PORTUGUESE("pt"),
    HINDI("hi"),
    JAPANESE("ja"),
    ;

    companion object {
        /** Default when unset. */
        val DEFAULT: AppLocale = SYSTEM

        /**
         * Parses a stored preference value.
         *
         * @param raw Stored name or tag.
         * @return Matching [AppLocale], or [DEFAULT].
         */
        fun fromStorage(raw: String?): AppLocale {
            if (raw.isNullOrBlank()) return DEFAULT
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }?.let { return it }
            entries.firstOrNull { it.tag.equals(raw, ignoreCase = true) }?.let { return it }
            return DEFAULT
        }
    }
}
