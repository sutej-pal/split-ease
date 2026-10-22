package com.splitease.app.domain.recurrence

import com.splitease.app.domain.model.RecurrenceFrequency
import java.util.Calendar
import java.util.TimeZone

/**
 * Pure helpers for advancing recurring expense schedules.
 *
 * Uses UTC calendar arithmetic so device TZ does not shift month boundaries unexpectedly.
 */
object RecurrenceScheduler {
    /**
     * Returns the first occurrence strictly after [fromEpochMs] for [frequency].
     *
     * @throws IllegalArgumentException if [frequency] is [RecurrenceFrequency.NONE].
     */
    fun nextOccurrenceAfter(
        fromEpochMs: Long,
        frequency: RecurrenceFrequency,
    ): Long {
        require(frequency != RecurrenceFrequency.NONE) { "NONE has no next occurrence." }
        val cal =
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = fromEpochMs
            }
        when (frequency) {
            RecurrenceFrequency.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RecurrenceFrequency.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RecurrenceFrequency.YEARLY -> cal.add(Calendar.YEAR, 1)
            RecurrenceFrequency.NONE -> error("unreachable")
        }
        return cal.timeInMillis
    }

    /**
     * Advances [fromEpochMs] by [frequency] until the result is strictly greater than [nowEpochMs].
     *
     * Caps iterations to avoid infinite loops on bad clocks.
     */
    fun catchUpNextOccurrence(
        fromEpochMs: Long,
        frequency: RecurrenceFrequency,
        nowEpochMs: Long,
        maxSteps: Int = 520,
    ): Long {
        var cursor = fromEpochMs
        repeat(maxSteps) {
            val next = nextOccurrenceAfter(cursor, frequency)
            if (next > nowEpochMs) return next
            cursor = next
        }
        return nextOccurrenceAfter(cursor, frequency)
    }
}
