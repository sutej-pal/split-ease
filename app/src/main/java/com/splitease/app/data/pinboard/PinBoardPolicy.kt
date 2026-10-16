package com.splitease.app.data.pinboard

/**
 * Product boundary for the group pin board.
 *
 * **Offline-first** — uses Room cache and [com.splitease.app.data.sync.SyncInteractor]
 * for background synchronization.
 *
 * | Concern | Pin board | Expenses / payments |
 * |---------|-----------|---------------------|
 * | Local cache | Room | Room |
 * | Sync queue | [com.splitease.app.data.sync.SyncInteractor] | [com.splitease.app.data.sync.SyncInteractor] PENDING flush |
 * | Realtime | Not subscribed | Group detail Realtime for ledger |
 * | Persistence | Debounced auto-save to Room, then sync | Flush-then-pull |
 * | Remote read | Fetch on open, resume, and idle poll | Group detail Realtime |
 *
 * @see docs/phase-11-group-pin-board.md
 */
object PinBoardPolicy {
    /** Supabase table name (PostgREST). */
    const val REMOTE_TABLE = "pin_boards"

    /** False — pin board supports offline writes via Room + SyncInteractor. */
    const val ONLINE_ONLY = false
}
