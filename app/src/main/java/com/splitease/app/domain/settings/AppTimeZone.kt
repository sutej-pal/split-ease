package com.splitease.app.domain.settings

import java.time.ZoneId
import java.util.TimeZone

/**
 * Process-wide zone chosen in Account settings.
 *
 * Display and calendar-month math should read this instead of the device zone.
 * UTC ledgers (recurrence, stored epoch millis) stay on an explicit UTC zone.
 * The value starts as the device zone and is replaced when prefs load or the user picks a zone.
 */
object AppTimeZone {
    @Volatile
    private var zoneId: ZoneId = ZoneId.systemDefault()

    /** IANA zone currently used for user-facing dates. */
    fun zoneId(): ZoneId = zoneId

    /** [TimeZone] equivalent of [zoneId] for [java.util.Calendar]. */
    fun timeZone(): TimeZone = TimeZone.getTimeZone(zoneId)

    /**
     * Applies an IANA id. Unknown ids fall back to the device zone.
     */
    fun apply(id: String?) {
        val trimmed = id?.trim().orEmpty()
        zoneId =
            if (trimmed.isEmpty()) {
                ZoneId.systemDefault()
            } else {
                runCatching { ZoneId.of(trimmed) }.getOrElse { ZoneId.systemDefault() }
            }
    }
}
