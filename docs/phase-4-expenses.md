# Phase 4 — Expense Creation & Splitting Logic

## Phase Goal

Let users create unlimited expenses with equal / unequal / percentage / shares splits, including **invited (not-yet-signed-up) people**, and sync so the invitee sees those expenses after they sign up or log in.

## Scope

### In
- Create expenses in a group or 1:1 with a friend
- Payer + multi-participant splits (`EQUAL`, `UNEQUAL`, `PERCENTAGE`, `SHARES`)
- `BigDecimal` split math with deterministic remainder rules + unit tests
- Include pending invited friends as participants immediately after add/invite
- Supabase `expenses` / `expense_splits` + best-effort sync
- Remap placeholder user ids → real auth id on invite accept (SQL + local)
- List expenses on group detail and friend detail; Add Expense screens

### Out
- Balance dashboard / debt simplification (Phase 5)
- Recurring expenses (Phase 6)
- Category picker UI beyond default seed (Phase 7)
- Hardened offline sync queue / conflict policy (Phase 7)

## Architecture Decisions

| Decision | Rationale |
|---|---|
| Domain `SplitCalculator` pure functions | Testable money math; no Android deps |
| `ExpenseInteractor` in data layer (like Social) | Room-first then PostgREST |
| No FK from remote `expense_splits.user_id` → `auth.users` | Allows placeholder UUIDs until invite accept |
| Soft local `group_members` for invited users | Group expense pickers can include them; remote membership waits for accept |
| Extend `accept_pending_invites()` to remap splits/payer | Invitee inherits history without client-only hacks |
| Best-effort expense sync (not Phase 7 queue) | Enough for invitee visibility after login |

## Data Model Changes

- Room: remap helpers on `expenses` / `expense_splits` / `group_members` (no schema version bump).
- Supabase: `docs/sql/migration_db.sql` — tables + RLS + accept RPC remap.
- Local remap when friend placeholder → real id on friends refresh.

## Files Added/Modified

| Path | Notes |
|---|---|
| `domain/split/SplitCalculator.kt` | Equal / unequal / % / shares |
| `data/expense/ExpenseInteractor.kt` | Create + sync + refresh |
| `data/remote/ExpenseRemoteDataSource.kt` | PostgREST expenses |
| `docs/sql/migration_db.sql` | Cloud schema + accept remap |
| `presentation/expenses/*` | Add expense, friend detail, list section |
| `presentation/groups/GroupsScreens.kt` | Expense list + FAB |
| `presentation/friends/FriendsScreens.kt` | Open friend detail |
| `presentation/navigation/SplitEaseNavHost.kt` | New routes |
| Auth / SocialInteractor | Claim invites + refresh expenses; soft members |

## Screens/UI Added

- Group detail: expense list + FAB → Add Expense
- Friend detail: expense list + Add Expense (1:1)
- Add Expense form: description, amount, currency, payer, participants, split mode

## How to Test

1. Run `docs/sql/migration_db.sql` in Supabase (fresh DB).
2. User A: add friend by email (non-user) → share invite → open friend → Add expense including invited person.
3. User A: invite email into a group → Add group expense with that participant.
4. User B: sign up with the **same invited email** → open Friends/Groups → expenses appear after sync.
5. Unit: `.\gradlew.bat :app:testDebugUnitTest --tests com.splitease.app.domain.split.SplitCalculatorTest`

## Known Issues / TODOs

- Expense sync is best-effort (full queue in Phase 7).
- Invitee must use the invited email for accept remap.
- Deep link open of invite URL still placeholder.
- Capture screenshot into `docs/screenshots/phase-4.png`.

## Screenshots placeholder

![phase-4-screenshot](./screenshots/phase-4.png)

---

## Plan

1. Ship `SplitCalculator` + JUnit5 tests (₹100 / 3 remainder).
2. Add Supabase expense schema; extend accept RPC for id remap.
3. Remote DTOs + `ExpenseInteractor` (create, list refresh, remap on friend refresh).
4. Soft-add invited users as local group members; allow as expense participants.
5. Wire nav + Compose screens; update docs; build.

---

## Outcome

**Status:** Done (2026-07-22)

Phase 4 delivered expense creation with all four split modes, Room + Supabase sync, and support for **invited users as participants immediately**. On sign-up/sign-in, `accept_pending_invites()` remaps placeholder ids on friends, splits, and payer; the client refreshes expenses so the new user sees history. Version **0.5.0**.

**Required SQL:** `docs/sql/migration_db.sql` (includes `accept_pending_invites()` remap).

**Next:** Phase 5 — Balances & Debt Simplification.
