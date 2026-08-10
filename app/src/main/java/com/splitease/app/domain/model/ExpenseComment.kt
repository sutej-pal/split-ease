package com.splitease.app.domain.model

/**
 * A user or system comment on an [Expense] (Splitwise-style thread).
 *
 * @property id Stable UUID.
 * @property expenseId Parent expense.
 * @property authorUserId Actor who wrote the comment (or who triggered a system update).
 * @property body Comment text / change summary.
 * @property kind [ExpenseCommentKind.USER] for free-form notes; [ExpenseCommentKind.SYSTEM] for
 *   automated “updated by …” / “added a photo” entries.
 * @property createdAtEpochMs When the comment was created.
 * @property syncStatus Offline-first sync bookmark.
 */
data class ExpenseComment(
    val id: String,
    val expenseId: String,
    val authorUserId: String,
    val body: String,
    val kind: ExpenseCommentKind = ExpenseCommentKind.USER,
    val createdAtEpochMs: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)

/** Distinguishes free-form comments from automated expense-update log lines. */
enum class ExpenseCommentKind {
    USER,
    SYSTEM,
}
