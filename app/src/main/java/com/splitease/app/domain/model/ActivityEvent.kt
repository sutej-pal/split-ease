package com.splitease.app.domain.model

/**
 * Activity-feed event (create / update / delete of expenses).
 *
 * Synced to Supabase when [syncStatus] is [SyncStatus.PENDING]. Pre-v16 rows stay
 * [SyncStatus.LOCAL_ONLY] and are not uploaded. [isSeen] is device-local.
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
 * @property remoteId Cloud id when synced; null if local-only.
 * @property syncStatus Offline-first sync bookmark.
 * @property isSeen True if the user has viewed this event.
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
    val remoteId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val isSeen: Boolean = false,
)

/**
 * Kinds persisted in [ActivityEvent.kind].
 */
enum class ActivityEventKind {
    EXPENSE_ADDED,
    EXPENSE_UPDATED,
    EXPENSE_DELETED,
}
