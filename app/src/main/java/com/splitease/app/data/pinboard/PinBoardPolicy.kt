package com.splitease.app.data.pinboard

/**
 * Product boundary for the group pin board.
 *
 * **Online-only by design** — not part of the offline-first Room + PENDING flush pipeline.
 *
 * | Concern | Pin board | Expenses / payments |
 * |---------|-----------|---------------------|
 * | Local cache | None (fetch on open only) | Room |
 * | Sync queue | None | [SyncInteractor] PENDING flush |
 * | Realtime | Not subscribed | Group detail Realtime for ledger |
 * | Persistence | Direct PostgREST upsert on explicit Save | Flush-then-pull |
 *
 * Do **not** add a Room entity, `SyncStatus`, or [com.splitease.app.data.sync.SyncInteractor]
 * hooks for pin boards without an explicit product change. Use [PinBoardInteractor] /
 * [PinBoardRemoteDataSource] only.
 *
 * @see docs/phase-11-group-pin-board.md
 */
object PinBoardPolicy {
    /** Supabase table name (PostgREST). */
    const val REMOTE_TABLE = "pin_boards"

    /** True — pin board writes require network; there is no offline queue. */
    const val ONLINE_ONLY = true
}
