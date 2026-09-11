package com.splitease.app.domain.settings

/**
 * App appearance preference.
 *
 * [LIGHT] is the default so the airy light palette is used unless the user
 * explicitly chooses dark or follow-system.
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    companion object {
        /** Default appearance for new installs. */
        val DEFAULT: ThemeMode = LIGHT

        /**
         * Parses a stored preference value.
         *
         * @param raw Persisted name, or null.
         * @return Matching mode, or [DEFAULT] when missing/unknown.
         */
        fun fromStorage(raw: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
