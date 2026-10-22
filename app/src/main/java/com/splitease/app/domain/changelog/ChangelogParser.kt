package com.splitease.app.domain.changelog

/**
 * Parses Keep-a-Changelog markdown shipped as the app's `CHANGELOG.md` asset.
 */
object ChangelogParser {
    private val headingRegex =
        Regex("""^## \[([^\]]+)](?:\s*-\s*(.+))?$""")
    private val buildRegex = Regex("""build\s+(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * Parses a full changelog document.
     *
     * @param markdown File contents.
     * @return Releases in file order (Unreleased first, then newest shipped).
     */
    fun parse(markdown: String): List<ChangelogRelease> {
        val lines = markdown.replace("\r\n", "\n").split('\n')
        val releases = mutableListOf<ChangelogRelease>()
        var versionName: String? = null
        var date: String? = null
        var versionCode: Int? = null
        var sectionTitle: String? = null
        val sectionItems = mutableListOf<String>()
        val sections = mutableListOf<ChangelogSection>()

        fun flushSection() {
            val title = sectionTitle
            if (title != null && sectionItems.isNotEmpty()) {
                sections += ChangelogSection(title = title, items = sectionItems.toList())
            }
            sectionTitle = null
            sectionItems.clear()
        }

        fun flushRelease() {
            flushSection()
            val name = versionName
            if (name != null) {
                releases +=
                    ChangelogRelease(
                        versionName = name,
                        date = date,
                        versionCode = versionCode,
                        sections = sections.toList(),
                    )
            }
            versionName = null
            date = null
            versionCode = null
            sections.clear()
        }

        for (raw in lines) {
            val line = raw.trimEnd()
            val heading = headingRegex.matchEntire(line.trim())
            if (heading != null) {
                flushRelease()
                versionName = heading.groupValues[1].trim()
                val meta = heading.groupValues[2].trim().takeIf { it.isNotEmpty() }
                if (meta != null) {
                    date =
                        meta
                            .substringBefore("—")
                            .substringBefore(" - ")
                            .trim()
                            .takeIf { it.isNotEmpty() }
                    versionCode =
                        buildRegex
                            .find(meta)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull()
                }
                continue
            }
            if (versionName == null) continue
            val trimmed = line.trim()
            if (trimmed.startsWith("### ")) {
                flushSection()
                sectionTitle = trimmed.removePrefix("### ").trim()
                continue
            }
            if (trimmed.startsWith("- ")) {
                sectionItems += stripMarkdown(trimmed.removePrefix("- ").trim())
            }
        }
        flushRelease()
        return releases
    }

    /**
     * Shipped versions only (skips `[Unreleased]`).
     *
     * @param markdown File contents.
     * @return Releases newest-first, matching the file after Unreleased.
     */
    fun parseShipped(markdown: String): List<ChangelogRelease> =
        parse(markdown).filter { it.versionName != "Unreleased" }

    private fun stripMarkdown(text: String): String =
        text
            .replace("**", "")
            .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
            .replace("`", "")
            .trim()
}
