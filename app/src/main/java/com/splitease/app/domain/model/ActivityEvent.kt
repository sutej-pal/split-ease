package com.splitease.app.domain.model

/**
 * Local activity-feed event (create / update / delete of expenses, etc.).
 *
 * @property id Event id.
 * @property kind Event kind string (see [ActivityEventKind]).
 * @property title Primary label.
 * @property subtitle Secondary label.
 * @property amountLabel Optional amount text.
 * @property actorUserId User who performed the action.
 * @property relatedExpenseId Linked expense id when applicable.
 * @property involvedUserIds Comma-wrapped user ids for feed filtering (`,id1,id2,`).
 * @property sortEpochMs Sort / display time.
 */
data class ActivityEvent(
    val id: String,
    val kind: ActivityEventKind,
    val title: String,
    val subtitle: String,
    val amountLabel: String,
    val actorUserId: String,
    val relatedExpenseId: String? = null,
    val involvedUserIds: String,
    val sortEpochMs: Long,
)

/**
 * Kinds persisted in [ActivityEvent.kind].
 */
enum class ActivityEventKind {
    EXPENSE_ADDED,
    EXPENSE_UPDATED,
    EXPENSE_DELETED,
}
