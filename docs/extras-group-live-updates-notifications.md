# Extras — Group live updates & member notifications

**Status:** Realtime (B7) + FCM push (B1–B5, B8) + remote delete prune (A5) implemented. A6 pull-to-refresh cancelled (resume + Realtime). Activity badges still TODO.  
**Added:** 2026-07-23 (post Phase 9 — out of original roadmap)  
**Updated:** 2026-08-19 — A6 pull-to-refresh won't-do; gesture removed from group detail  
**Why this doc exists:** Capture product extras that are **not** part of Phases 0–9, so we can implement and regress them later without losing intent.

---

## Product goals (reference)

1. **Notify every group member** when anyone in the group changes shared data — at least:
   - Add expense
   - Update expense
   - Delete expense
   - Record / update / delete settlement (payment)
   - (Later) group metadata changes (name, members, leave/delete) if useful
2. **On opening a group**, that device must **pull latest cloud data** so the ledger, balances, and members reflect other users' changes (not only local Room cache).
3. Notifications should deep-link (or land) on the relevant **group detail** when tapped.

---

## Extra changes — checklist

Use this list as the implementation backlog. Mark items when done.

### A. Visible updates when opening a group

| #   | Change                                                                                                  | Status         | Notes                                                                    |
| --- | ------------------------------------------------------------------------------------------------------- | -------------- | ------------------------------------------------------------------------ |
| A1  | On group detail `RESUMED`, refresh cloud expenses for that group into Room                              | **Done (MVP)** | `ExpensesViewModel.refreshGroupFromCloud` / prior `refreshGroupExpenses` |
| A2  | On group open / resume, also flush PENDING local writes then pull friends/groups/expenses/payments      | **Done (MVP)** | `SyncInteractor.syncForUser` + group expense pull                        |
| A3  | Fix `syncForUser` so remote **pull always runs** (not only when invite-accept fails)                    | **Done**       | Was a bug blocking multi-device visibility                               |
| A4  | Pull payments scoped to group (or involving current user) on open                                       | **Done (MVP)** | Via `syncForUser` > `refreshPaymentsForUser`                             |
| A5  | Propagate **deletes** from cloud (tombstones or missing-id cleanup) so remote deletes disappear locally | **Done**       | Missing-id prune of `SYNCED` rows after group / 1:1 pull; PENDING kept   |
| A6  | Optional: pull-to-refresh gesture on group ledger                                                       | **Won't do**   | Resume + Realtime cover it; gesture removed from group detail            |

### B. Notifications to other members

| #   | Change                                                                                      | Status   | Notes                                                                          |
| --- | ------------------------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------ |
| B1  | Choose push stack (recommend **FCM** + Supabase Edge Function; ask before adding libs)      | **Done** | FCM + Edge Function ([fcm-setup.md](fcm-setup.md))                             |
| B2  | Store device push tokens per user (`device_tokens` table + RLS)                             | **Done** | [sql/migration_db.sql](sql/migration_db.sql)                                   |
| B3  | On expense/payment insert/update/delete, notify other **group members** (exclude actor)     | **Done** | Edge Function + webhook/trigger ([sql/migration_db.sql](sql/migration_db.sql)) |
| B4  | Notification copy: actor, group name, action ("Ada added "Dinner" · ₹1,200")                | **Done** | Built in `notify-group-members`                                                |
| B5  | Tap notification > open `group_detail/{groupId}` (App Links / intent extras)                | **Done** | Intent extra + `pending_notification_group_id`                                 |
| B6  | In-app Activity feed already lists own activity — extend or badge when remote events arrive | TODO     | Optional if push is delayed                                                    |
| B7  | Supabase Realtime channel while group detail is open (live list without leaving screen)     | **Done** | `GroupLiveSync` + `realtime-kt`; [sql/migration_db.sql](sql/migration_db.sql)  |
| B8  | Notification preferences (mute group / mute all)                                            | **Done** | Settings → Notifications; Group settings mute; `notification_prefs` |

### C. Docs / ops

| #   | Change                                                             | Status   | Notes                                                 |
| --- | ------------------------------------------------------------------ | -------- | ----------------------------------------------------- |
| C1  | This extras doc                                                    | **Done** |                                                       |
| C2  | SQL for `device_tokens` + notify trigger                           | **Done** | [migration_db.sql](sql/migration_db.sql)              |
| C3  | Flag free-tier FCM / Edge Function / Realtime cost in Known Issues | **Done** | See Known Issues below + [fcm-setup.md](fcm-setup.md) |

---

## Current behavior (baseline before / after this extra)

**Before fix**
- Group detail refreshed expenses on resume only.
- `SyncInteractor.syncForUser` pulled friends/groups/expenses **only inside** `acceptPendingInvites` **failure** path — successful invite-accept skipped hydrate. Opening a group on another device to see their expense was unreliable.

**After MVP (A1–A4) + Realtime/FCM (2026-07-29)**
- Opening / resuming a group runs full `syncForUser` (flush + pull) and a targeted group expense refresh so ledger/balances update from Supabase.
- While group detail is **RESUMED**, Realtime postgres changes on `expenses` / `payments` debounce-refresh Room (`GroupLiveSync`).
- Background members get FCM via Edge Function `notify-group-members` when Firebase + webhooks are configured ([fcm-setup.md](fcm-setup.md)).
- Group ledger has **no pull-to-refresh** (A6 cancelled): resume + Realtime cover it.

---

## Architecture (Realtime + FCM)

```
Member A writes expense > Room PENDING > PostgREST upsert
        ↓
Supabase DB trigger / webhook on expenses | payments
        ↓
Edge Function: resolve group_members − actor, load FCM tokens
        ↓
FCM data+notification message { groupId, expenseId, type }
        ↓
Member B device: show notification; on tap > GroupDetail
        ↓
GroupDetail RESUMED / Realtime event > sync pull > UI Flow updates
```
**Foreground:** Supabase Realtime on `expenses` / `payments` filtered by `group_id` while GroupDetail is visible (`GroupLiveSync`).  
**Background:** FCM via Edge Function (see [fcm-setup.md](fcm-setup.md)).

**Remote pull into Room:** `persistRemoteExpense` / `persistRemotePayment` stub missing payer/participant users. Default categories use stable `cat_*` ids (auto-seeded on pull); custom category ids from other devices are still dropped until a cloud `categories` table exists.

---

## Known Issues / costs

- Supabase Realtime concurrent connections and FCM/Edge Function invocations count toward free-tier quotas — monitor before wide rollout.
- Without `app/google-services.json`, FCM registration no-ops at runtime (Realtime still works).
- Notify triggers no-op until `app.settings.notify_function_url` + `service_role_key` are set (or use Dashboard webhooks instead).
- Mute prefs (B8) are implemented (Settings + per-group mute; Edge Function skips muted recipients).

---

## Library / product decisions

Approved for this extras work:

- `realtime-kt` (Supabase Realtime) — installed
- `firebase-messaging` (FCM) — installed; requires `app/google-services.json` (gitignored)

Still deferred:

- Third-party push (OneSignal, etc.)
- Firebase-only notify (Firestore tokens + Cloud Functions instead of Edge Function / webhooks) — considered for Supabase free-tier limits; keep current FCM + `notify-group-members` path for now

---

## How to test (MVP sync visibility)

1. Two devices signed in as two group members; Supabase Phase 3–6 SQL applied.
2. Device A adds an expense in the shared group; wait for automatic background sync.
3. Device B opens (or returns to) that group > expense and balances appear without reinstall.
4. Repeat for settle-up payment.
5. Device A deletes an expense; Device B opens/resumes the group (or stays on detail with Realtime) > the expense disappears from B's ledger.
6. Background device B; add expense on A → B receives a push; tap opens the group.

---

## Related docs

- [PROGRESS.md](../PROGRESS.md) — carried-forward TODO pointer
- [ARCHITECTURE.md](../ARCHITECTURE.md) — sync layer
- [supabase-architecture-todos.md](./supabase-architecture-todos.md) — ordered sync follow-ups (A5 = TODO 1)
- [phase-7-search-categories-currency-offline-sync.md](./phase-7-search-categories-currency-offline-sync.md) — offline queue / hydrate
- Invite deep links still placeholder (`splitease.app`) — notification tap targets should align when App Links land

---

## Outcome log

| Date       | What changed                                                                                                          |
| ---------- | --------------------------------------------------------------------------------------------------------------------- |
| 2026-07-23 | Created this extras doc; fixed `syncForUser` pull; group open runs full sync + group expense refresh                  |
| 2026-07-23 | Find people + Add friend contact UI (device contacts, search) — related social UX extra                               |
| 2026-07-29 | Slice 1 Realtime (`GroupLiveSync` + publication SQL) and Slice 2 FCM (device_tokens, Edge Function, MessagingService) |
| 2026-08-18 | Ops: `notification_prefs` applied; Edge Function `notify-group-members` deployed with `FIREBASE_SERVICE_ACCOUNT_JSON`; expenses/payments Database Webhooks wired |
| 2026-08-19 | A6 cancelled: group ledger has no pull-to-refresh; open/resume + `GroupLiveSync` keep Room current     |
