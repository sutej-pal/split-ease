package com.splitease.app.domain.changelog

/**
 * One Keep-a-Changelog version section.
 *
 * @property versionName SemVer label, or `"Unreleased"`.
 * @property date ISO date when cut, if present.
 * @property versionCode Play `versionCode` when the heading includes `build N`.
 * @property sections Non-empty Added/Changed/Fixed/Removed groups.
 */
data class ChangelogRelease(
    val versionName: String,
    val date: String? = null,
    val versionCode: Int? = null,
    val sections: List<ChangelogSection> = emptyList(),
) {
    /**
     * Flattened bullet list for dialogs and Play copy.
     *
     * @param maxItems Maximum bullets to include.
     * @return Plain-text lines, or empty when this section has no items.
     */
    fun summaryItems(maxItems: Int = 6): List<String> =
        sections.flatMap { it.items }.take(maxItems)
}

/**
 * A `###` subsection inside a [ChangelogRelease].
 *
 * @property title Heading such as `Added` or `Fixed`.
 * @property items Bullet bodies with markdown emphasis stripped.
 */
data class ChangelogSection(
    val title: String,
    val items: List<String>,
)
