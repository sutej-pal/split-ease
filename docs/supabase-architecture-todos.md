# Supabase architecture follow-ups

Ordered backlog from the offline-first + Supabase design review (2026-08-12).

**Verdict:** Current design is correct (Room offline-first, PostgREST + RLS, Auth UUID = profile id, flush-then-pull, local balances, Realtime on open group only). Do **not** redesign toward Firebase-for-data, a CRUD BFF, or always-on Realtime.

Work items below are improvements on top of that design. Check boxes when done; keep order when picking the next item.

## Ordered TODOs

- [ ] **1 — Remote deletes / tombstones** — Pull is upsert-mostly today, so a delete on another device can linger in Room. Propagate deletes (soft-delete + `deleted_at`, or prune local ids missing from the remote set for a group). Highest-impact sync correctness gap. Cross-ref: [extras A5](extras-group-live-updates-notifications.md), [TODO.md](../TODO.md).

- [ ] **2 — Explicit conflict policy** — Rows already carry `updatedAtEpochMs`; define and enforce last-write-wins (or equivalent) on pull. Do not overwrite a local `PENDING` row with an older remote row when two devices edit the same expense offline.

- [ ] **3 — Category cloud sync** — Unstable / device-local category UUIDs break `category_id` on pull. Sync categories end-to-end, or keep only stable default ids in the cloud. Cross-ref: [PROGRESS.md](../PROGRESS.md), [TODO.md](../TODO.md).

- [ ] **4 — Pin board product rule** — Pin board is online-only by design. Document and keep that boundary clear so it is never queued like offline expenses (no accidental Room/PENDING flush path). Cross-ref: [phase-11](phase-11-group-pin-board.md).

- [ ] **5 — Edge Functions stay non-CRUD** — Keep Edge Functions for push/notify/hooks only. Shared privileged rules belong in SQL RPCs; money math stays on-device. Do not add a second API server for table writes.

- [ ] **6 — Ops hygiene** — Keep [sql/migration_db.sql](sql/migration_db.sql) as the single schema SoT; verify RLS on every new table; treat mail-service as separate from Supabase Auth.

## Out of scope (do not pursue)

- Switching core data back to Firebase
- Adding a Node/BFF in front of every PostgREST call
- Storing derived balances in Supabase
- Subscribing all tables to Realtime all the time

## Related docs

- [ARCHITECTURE.md](../ARCHITECTURE.md) — current sync + Supabase wiring
- [extras-group-live-updates-notifications.md](extras-group-live-updates-notifications.md) — live updates / delete tombstones checklist
- [TODO.md](../TODO.md) — consolidated app backlog
- [fcm-setup.md](fcm-setup.md) — FCM + Edge Function ops
