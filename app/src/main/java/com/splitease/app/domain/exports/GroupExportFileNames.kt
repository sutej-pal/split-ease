package com.splitease.app.domain.exports

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Builds a safe download filename for a group CSV export.
 */
object GroupExportFileNames {
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
    private val UNSAFE = Regex("[^A-Za-z0-9._-]+")

    /**
     * @param groupName Raw group display name.
     * @param exportedAtEpochMs Timestamp embedded in the filename.
     * @param zoneId Time zone for the stamp.
     */
    fun fileName(
        groupName: String,
        exportedAtEpochMs: Long,
        zoneId: ZoneId,
    ): String = "SplitEase_${slug(groupName)}_${stamp(exportedAtEpochMs, zoneId)}.csv"

    internal fun slug(groupName: String): String {
        val cleaned =
            groupName
                .trim()
                .replace(UNSAFE, "_")
                .replace("..", "_")
                .trim('_', '.')
                .take(40)
        return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") "group" else cleaned
    }

    private fun stamp(
        exportedAtEpochMs: Long,
        zoneId: ZoneId,
    ): String = Instant.ofEpochMilli(exportedAtEpochMs).atZone(zoneId).format(STAMP)
}
