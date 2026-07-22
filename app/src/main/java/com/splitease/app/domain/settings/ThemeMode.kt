package com.splitease.app.domain.settings

/**
 * App appearance preference.
 *
 * [SYSTEM] follows the device dark/light setting (default).
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    companion object {
        /**
         * Parses a stored preference value.
         *
         * @param raw Persisted name, or null.
         * @return Matching mode, or [SYSTEM] when missing/unknown.
         */
        fun fromStorage(raw: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: SYSTEM
    }
}
