package com.splitease.app.domain.changelog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChangelogParserTest {
    @Test
    fun parseKeepsUnreleasedAndStripsMarkdown() {
        val markdown =
            """
            # Changelog

            ## [Unreleased]

            ### Added
            - **In-app** notes with a [link](https://example.com)

            ## [1.0.0] - 2026-07-23 — build 2

            ### Fixed
            - Groups home `sync` feel

            ### Added
            """.trimIndent()

        val all = ChangelogParser.parse(markdown)
        assertEquals(2, all.size)
        assertEquals("Unreleased", all[0].versionName)
        assertEquals(listOf("In-app notes with a link"), all[0].sections.single().items)

        val shipped = ChangelogParser.parseShipped(markdown)
        assertEquals(1, shipped.size)
        val release = shipped.single()
        assertEquals("1.0.0", release.versionName)
        assertEquals("2026-07-23", release.date)
        assertEquals(2, release.versionCode)
        assertEquals(listOf("Groups home sync feel"), release.sections.single().items)
        assertEquals(listOf("Groups home sync feel"), release.summaryItems())
    }

    @Test
    fun parseShippedSkipsEmptyDocument() {
        assertTrue(ChangelogParser.parseShipped("# Changelog\n").isEmpty())
        val noBuild = ChangelogParser.parse("## [0.9.0] - 2026-07-22\n\n### Added\n- Charts\n")
        assertEquals("0.9.0", noBuild.single().versionName)
        assertEquals("2026-07-22", noBuild.single().date)
        assertNull(noBuild.single().versionCode)
    }
}
