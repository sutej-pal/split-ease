# Supabase architecture follow-ups

Ordered backlog from the offline-first + Supabase design review (2026-08-12).

**Verdict:** Current design is correct (Room offline-first, PostgREST + RLS, Auth UUID = profile id, flush-then-pull, local balances, Realtime on open group only). Do **not** redesign toward Firebase-for-data, a CRUD BFF, or always-on Realtime.

Work items below are improvements on top of that design. Check boxes when done; keep order when picking the next item.

## Ordered TODOs

- [x] **1 — Remote deletes / tombstones** — After a successful full pull for a group (or 1:1 involving-user set), prune local `SYNCED` expenses/payments whose ids are missing remotely. Never prune `PENDING` / `LOCAL_ONLY`. Matches hard deletes (no `deleted_at`). Realtime DELETE → same refresh path. Cross-ref: [extras A5](extras-group-live-updates-notifications.md), [TODO.md](../TODO.md).

- [x] **2 — Explicit conflict policy** — Pull uses last-write-wins on `updatedAtEpochMs` (`SyncConflictPolicy`). Local `PENDING` / `LOCAL_ONLY` is never replaced by an equal-or-older remote row; `SYNCED` skips strictly older remote. Applied in `persistRemoteExpense` / `persistRemotePayment`.

- [x] **3 — Category cloud sync** — Stable built-in ids (`cat_*`) on the wire; no Supabase `categories` table. Legacy random default ids remapped on upgrade (Room v12 + `ensureDefaults`). Push sends stable ids only; pull auto-seeds missing defaults. Custom categories stay device-local until product needs a cloud table. Cross-ref: [PROGRESS.md](../PROGRESS.md), [TODO.md](../TODO.md).

- [x] **4 — Pin board product rule** — Offline-first Room cache + PENDING flush (not online-only). Load fetches Supabase so another member’s save is applied unless local is `PENDING`. No Realtime / live cursor. Documented in [phase-11](phase-11-group-pin-board.md) and [PinBoardPolicy.kt](../app/src/main/java/com/splitease/app/data/pinboard/PinBoardPolicy.kt).

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
