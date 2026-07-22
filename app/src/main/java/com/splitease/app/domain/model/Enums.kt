package com.splitease.app.domain.model

/**
 * How an expense amount is divided among participants.
 */
enum class SplitType {
    /** Divide total evenly across participants (remainder handled in Phase 4). */
    EQUAL,

    /** Each participant owes an exact amount. */
    UNEQUAL,

    /** Each participant owes a percentage of the total. */
    PERCENTAGE,

    /** Each participant owes proportional to integer shares. */
    SHARES,
}

/**
 * Role of a user inside a group.
 */
enum class MemberRole {
    OWNER,
    MEMBER,
}

/**
 * Local sync bookmark for offline-first rows (cloud sync arrives Phase 7).
 */
enum class SyncStatus {
    /** Never uploaded; local-only draft or pre-auth data. */
    LOCAL_ONLY,

    /** Queued for upload/update when online. */
    PENDING,

    /** Last known cloud state matches local. */
    SYNCED,
}

/**
 * Recurrence cadence for expenses that repeat (scheduler in Phase 6).
 */
enum class RecurrenceFrequency {
    NONE,
    WEEKLY,
    MONTHLY,
    YEARLY,
}
