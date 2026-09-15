package com.splitease.app.domain.exports

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class GroupExportFileNamesTest {
    @Test
    fun slug_replaces_unsafe_characters() {
        assertEquals("Weekend_trip", GroupExportFileNames.slug("Weekend trip!!"))
        assertEquals("group", GroupExportFileNames.slug("   "))
        assertEquals("group", GroupExportFileNames.slug(".."))
        assertEquals("etc_passwd", GroupExportFileNames.slug("../etc/passwd"))
    }

    @Test
    fun fileName_includes_slug_and_stamp() {
        val epoch =
            LocalDateTime.of(2026, 8, 19, 1, 33, 5).toInstant(ZoneOffset.UTC).toEpochMilli()
        val name = GroupExportFileNames.fileName("Goa 2026", epoch, ZoneOffset.UTC)
        assertEquals("SplitEase_Goa_2026_2026-08-19_013305.csv", name)
        assertTrue(name.endsWith(".csv"))
    }
}
