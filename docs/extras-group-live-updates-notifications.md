# Extras — Group live updates & member notifications

**Status:** Partially started (sync-on-open + sync pull fix). Push notifications not implemented yet.  
**Added:** 2026-07-23 (post Phase 9 — out of original roadmap)  
**Why this doc exists:** Capture product extras that are **not** part of Phases 0–9, so we can implement and regress them later without losing intent.

---

## Product goals (reference)

1. **Notify every group member** when anyone in the group changes shared data — at least:
   - Add expense
   - Update expense
   - Delete expense
   - Record / update / delete settlement (payment)
   - (Later) group metadata changes (name, members, leave/delete) if useful
2. **On opening a group**, that device must **pull latest cloud data** so the ledger, balances, and members reflect other users’ changes (not only local Room cache).
3. Notifications should deep-link (or land) on the relevant **group detail** when tapped.

---

## Extra changes — checklist

Use this list as the implementation backlog. Mark items when done.

### A. Visible updates when opening a group

| # | Change | Status | Notes |
|---|---|---|---|
| A1 | On group detail `RESUMED`, refresh cloud expenses for that group into Room | **Done (MVP)** | `ExpensesViewModel.refreshGroupFromCloud` / prior `refreshGroupExpenses` |
| A2 | On group open / resume, also flush PENDING local writes then pull friends/groups/expenses/payments | **Done (MVP)** | `SyncInteractor.syncForUser` + group expense pull |
| A3 | Fix `syncForUser` so remote **pull always runs** (not only when invite-accept fails) | **Done** | Was a bug blocking multi-device visibility |
| A4 | Pull payments scoped to group (or involving current user) on open | **Done (MVP)** | Via `syncForUser` → `refreshPaymentsForUser` |
| A5 | Propagate **deletes** from cloud (tombstones or missing-id cleanup) so remote deletes disappear locally | TODO | Today pull is upsert-mostly; deleted remote rows may linger offline |
| A6 | Optional: pull-to-refresh gesture on group ledger | TODO | UX nicety |

### B. Notifications to other members

| # | Change | Status | Notes |
|---|---|---|---|
| B1 | Choose push stack (recommend **FCM** + Supabase Edge Function; ask before adding libs) | TODO | Not in current Gradle stack (`auth` + `postgrest` only) |
| B2 | Store device push tokens per user (`device_tokens` table + RLS) | TODO | |
| B3 | On expense/payment insert/update/delete, notify other **group members** (exclude actor) | TODO | DB webhook / trigger → Edge Function → FCM |
| B4 | Notification copy: actor, group name, action (“Ada added “Dinner” · ₹1,200”) | TODO | Keep short; no PII beyond display names |
| B5 | Tap notification → open `group_detail/{groupId}` (App Links / intent extras) | TODO | Depends on invite deep-link / App Links work |
| B6 | In-app Activity feed already lists own activity — extend or badge when remote events arrive | TODO | Optional if push is delayed |
| B7 | Supabase Realtime channel while group detail is open (live list without leaving screen) | TODO | Needs `realtime-kt`; ask before adding |
| B8 | Notification preferences (mute group / mute all) | TODO | Settings later |

### C. Docs / ops

| # | Change | Status | Notes |
|---|---|---|---|
| C1 | This extras doc | **Done** | |
| C2 | SQL for `device_tokens` + notify trigger | TODO | Ship under `docs/sql/` when implementing B |
| C3 | Flag free-tier FCM / Edge Function / Realtime cost in Known Issues | TODO | When B/Realtime land |

---

## Current behavior (baseline before / after this extra)

**Before fix**
- Group detail refreshed expenses on resume only.
- `SyncInteractor.syncForUser` pulled friends/groups/expenses **only inside** `acceptPendingInvites` **failure** path — successful invite-accept skipped hydrate. Multi-device “open group → see their expense” was unreliable.

**After MVP (A1–A4)**
- Opening / resuming a group runs full `syncForUser` (flush + pull) and a targeted group expense refresh so ledger/balances update from Supabase.
- **No system notification** yet when another member changes data while the app is backgrounded or closed.

---

## Recommended architecture (when implementing B + B7)

```
Member A writes expense → Room PENDING → PostgREST upsert
        ↓
Supabase DB trigger / webhook on expenses | payments
        ↓
Edge Function: resolve group_members − actor, load FCM tokens
        ↓
FCM data+notification message { groupId, expenseId, type }
        ↓
Member B device: show notification; on tap → GroupDetail
        ↓
GroupDetail RESUMED / Realtime event → sync pull → UI Flow updates
```

**Foreground alternative:** Supabase Realtime on `expenses` / `payments` filtered by `group_id` while GroupDetail is visible; still keep FCM for background.

---

## Library / product decisions (blocked until asked)

Per project rules, do **not** add without explicit approval:

- `firebase-messaging` (FCM)
- `realtime-kt` (Supabase Realtime)
- Third-party push (OneSignal, etc.)

---

## How to test (MVP sync visibility)

1. Two devices / flavors (`standard` + `clone`) signed in as two group members; Supabase Phase 3–6 SQL applied.
2. Device A adds an expense in the shared group; wait for sync (or Account → Sync now).
3. Device B opens (or returns to) that group → expense and balances appear without reinstall.
4. Repeat for settle-up payment.
5. (Later) B receives a push when A saves; tap opens the group.

---

## Related docs

- [PROGRESS.md](../PROGRESS.md) — carried-forward TODO pointer
- [ARCHITECTURE.md](../ARCHITECTURE.md) — sync layer
- [phase-7-search-categories-sync.md](./phase-7-search-categories-sync.md) — offline queue / hydrate
- Invite deep links still placeholder (`splitease.app`) — notification tap targets should align when App Links land

---

## Outcome log

| Date | What changed |
|---|---|
| 2026-07-23 | Created this extras doc; fixed `syncForUser` pull; group open runs full sync + group expense refresh |
| 2026-07-23 | Find people + Add friend contact UI (device contacts, search) — related social UX extra |
| | Push notifications (B*) still TODO — needs FCM / Edge Function decision |
