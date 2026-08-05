# Phase 6 — Settlements & Recurring Expenses

Added settle-up payments that reduce derived balances, plus recurring expense templates that WorkManager materializes on a schedule. Hardened offline sync of the full ledger is Phase 7.

## Phase Goal

Let users record settlements (“mark paid”) that reduce derived balances, and support recurring expense templates that generate new expense instances on a schedule via WorkManager.

## Scope

### In
- Record payment / settle-up between two users (optional group context)
- Apply `payments` when computing balances (after expense nets, before simplify)
- Payment history (group / friend scoped lists)
- Recurring flag + frequency on expense create (`WEEKLY` / `MONTHLY` / `YEARLY`)
- WorkManager daily worker that materializes due recurring instances into Phase 4 expenses
- Unit tests for payment application math
- Supabase SQL for `payments` (+ optional recurrence columns on `expenses`)

### Out
- Payment gateway / UPI deep links (Phase 8)
- Hardened offline sync queue / conflict policy (Phase 7)
- Editing / pausing / ending recurrence rules beyond “template stays active”
- FX / multi-currency conversion (Phase 7)

## Architecture Decisions

| Decision | Rationale |
|---|---|
| Reuse existing Room `payments` table | Schema ready since Phase 1 |
| `BalanceCalculator.applyPayments` pure function | Same testability as expense nets |
| Payment: fromUser +amount, toUser −amount | Matches “A pays B” reducing A’s debt / B’s credit |
| Room v4: `nextOccurrenceEpochMs` + `recurringTemplateId` | Track schedule + link instances without hacking notes |
| Destructive migration (existing MVP policy) | No production users yet |
| WorkManager + Hilt Worker | Roadmap-mandated scheduler; Jetpack standard |
| Local-first payments; best-effort Supabase upsert | Matches expenses pattern; full queue in Phase 7 |

## Data Model Changes

### Room v4 (`expenses`)
| Column | Type | Notes |
|---|---|---|
| `nextOccurrenceEpochMs` | INTEGER nullable | Next generate-at for templates |
| `recurringTemplateId` | TEXT nullable | Parent template id for generated instances |

### Remote
- New `payments` table + RLS
- Optional `is_recurring`, `recurrence_frequency`, `next_occurrence_epoch_ms`, `recurring_template_id` on `expenses`

## Files Added/Modified (planned)

| Path | Notes |
|---|---|
| `domain/balance/BalanceCalculator.kt` | `applyPayments` |
| `domain/balance/*Test.kt` | Payment math cases |
| `data/payment/PaymentInteractor.kt` | Record settlement |
| `data/balance/BalanceInteractor.kt` | Combine payments into Flows |
| `data/local/dao/PaymentDao.kt` | Pairwise / involving queries |
| `data/expense/ExpenseInteractor.kt` | Recurrence on create + generate instance |
| `data/recurring/RecurringExpenseWorker.kt` | WorkManager |
| `presentation/settlements/*` | Settle-up UI |
| `presentation/expenses/*` | Frequency picker |
| `docs/sql/migration_db.sql` | Cloud schema (payments + recurring columns) |
| Docs / version `0.7.0` | PROGRESS, CHANGELOG, ARCHITECTURE, data-dictionary |

## Screens/UI Added

- Settle up (from friend detail, group balance debt line, or balances hub)
- Optional recent payments section on friend/group detail
- Recurrence frequency chips on Add Expense

## How to Test

1. Create expenses so A owes B; record settlement → balances settle to zero.
2. Settle a simplified group debt → that transfer disappears from who-owes-whom.
3. Create weekly recurring expense; advance / wait for worker (or trigger manually) → new instance appears.
4. Unit: `.\gradlew.bat :app:testDebugUnitTest --tests com.splitease.app.domain.balance.*`

## Known Issues / TODOs

- Capture screenshot into `docs/screenshots/phase-6.png`.
- Cloud payment sync best-effort only until Phase 7 queue.
- Recurring remote columns optional until SQL applied.

## Screenshots placeholder

![phase-6-screenshot](./screenshots/phase-6.png)

---

## Plan

1. Document this plan; bump Room to v4 recurrence fields.
2. Payment apply math + `PaymentInteractor` + balance wiring.
3. Settle-up UI + navigation entry points.
4. Recurring create UI + WorkManager generator.
5. SQL + docs + tests + version 0.7.0.

---

## Outcome

**Status:** Done (2026-07-22)

Phase 6 delivered settle-up payments that reduce derived balances, and recurring expense templates with a daily WorkManager generator. Room bumped to **v4** (`nextOccurrenceEpochMs`, `recurringTemplateId`). Version **0.7.0**. Apply [migration_db.sql](sql/migration_db.sql) in Supabase when ready; cloud payment push stays Phase 7.

**Next:** Phase 7 — Search, Categories, Multi-Currency, Offline Sync.
