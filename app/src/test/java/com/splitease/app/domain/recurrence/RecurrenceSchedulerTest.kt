package com.splitease.app.domain.recurrence

import com.splitease.app.domain.model.RecurrenceFrequency
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecurrenceSchedulerTest {
    @Test
    fun weekly_advances_seven_days() {
        val start = 1_700_000_000_000L
        val next = RecurrenceScheduler.nextOccurrenceAfter(start, RecurrenceFrequency.WEEKLY)
        assertTrue(next - start == 7L * 24 * 60 * 60 * 1000)
    }

    @Test
    fun catch_up_skips_past_occurrences() {
        val start = 1_700_000_000_000L
        val now = start + 20L * 24 * 60 * 60 * 1000
        val next =
            RecurrenceScheduler.catchUpNextOccurrence(
                fromEpochMs = start,
                frequency = RecurrenceFrequency.WEEKLY,
                nowEpochMs = now,
            )
        assertTrue(next > now)
    }
}
