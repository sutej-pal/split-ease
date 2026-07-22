# Phase 5 — Balances & Debt Simplification

## Phase Goal

Derive per-friend and per-group net balances from existing expenses/splits, show “who owes whom,” and minimize settlement transactions with a pure BigDecimal debt-simplification algorithm.

## Scope

### In
- Net balances from expenses + splits (`BigDecimal` only), grouped by currency (no FX)
- Per-friend and per-group balance summaries
- Debt simplification (minimize transfer count) within a balance scope
- Balances hub from Home; balance headers on group detail and friend detail
- Unit tests for balance math and simplification

### Out
- Record settlement / mark paid UI (Phase 6)
- Recurring expenses (Phase 6)
- Multi-currency FX conversion (Phase 7)
- Persisting balances as tables (balances stay derived)
- Hardened offline sync queue (Phase 7)

## Architecture Decisions

| Decision | Rationale |
|---|---|
| Pure `domain.balance` calculators (like `SplitCalculator`) | Testable money math; no Android deps |
| Convention: net > 0 ⇒ is owed; net < 0 ⇒ owes | Documented once; UI maps to “you are owed / you owe” |
| Per-currency buckets; no cross-currency netting | FX is Phase 7 |
| Greedy creditor/debtor matching for simplify | Deterministic, minimal transaction count for MVP |
| `BalanceInteractor` loads Room expenses/splits then calls domain | Matches ExpenseInteractor pattern; ViewModels stay thin |
| No new Room/Supabase schema | Balances are derived; payments unused until Phase 6 |

## Data Model Changes

- None (derived from `expenses` / `expense_splits`).
- Repository: expose `observeInvolvingUser` + batch split load for balance Flows.

## Files Added/Modified

| Path | Notes |
|---|---|
| `domain/balance/BalanceCalculator.kt` | Net balances by user / currency |
| `domain/balance/DebtSimplifier.kt` | Minimize transfers |
| `domain/balance/*Test.kt` | JUnit5 BigDecimal cases |
| `data/balance/BalanceInteractor.kt` | Observe + label summaries |
| `data/repository/RoomExpenseRepository.kt` | Involving-user + batch splits |
| `presentation/balances/*` | Hub screen + ViewModel |
| Group / friend detail + Home + nav | Surface balances |
| Docs / version `0.6.0` | PROGRESS, CHANGELOG, ARCHITECTURE |

## Screens/UI Added

- Home → Balances hub: overall owed/owing, friend rows, group rows
- Group detail: balance section + simplified who-owes-whom
- Friend detail: net balance line for 1:1 expenses

## How to Test

1. Create group expenses with multiple payers/participants; open group → verify nets and simplified debts.
2. Create 1:1 friend expenses; open friend → verify “owes you / you owe”.
3. Open Home → Balances → friend and group summaries match detail screens.
4. Unit: `.\gradlew.bat :app:testDebugUnitTest --tests com.splitease.app.domain.balance.*`

## Known Issues / TODOs

- Settlements (`payments`) are now applied in Phase 6 (v0.7.0).
- Mixed currencies shown separately; no conversion.
- Capture screenshot into `docs/screenshots/phase-5.png`.

## Screenshots placeholder

![phase-5-screenshot](./screenshots/phase-5.png)

---

## Plan

1. Ship `BalanceCalculator` + `DebtSimplifier` + unit tests.
2. Extend `ExpenseRepository` for involving-user + batch splits; add `BalanceInteractor`.
3. Wire Balances hub + group/friend balance sections; Home entry.
4. Update docs, bump to 0.6.0, run tests/build.

---

## Outcome

**Status:** Done (2026-07-22)

Phase 5 delivered derived net balances (per friend / per group / overall) and greedy debt simplification with BigDecimal unit tests. Balances hub is reachable from Home; group and friend detail screens show balance headers and simplified who-owes-whom. Version **0.6.0**. No schema changes — balances remain computed from expenses/splits. Settlements are not subtracted until Phase 6.

**Next:** Phase 6 — Settlements & Recurring Expenses.
