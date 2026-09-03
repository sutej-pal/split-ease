# Phase 7 — Search, Categories, Multi-Currency, Offline Sync

Hardened offline-first sync (durable PENDING queue), expense search, categories, a currency picker, and simple spending totals. Payment deep links and CSV import are stretch work in Phase 8.

**Current catalog:** INR and USD in `AppCurrencies.OPTIONS` (a 100+ ISO list was in the original plan and is still open).

## Phase Goal

Make expenses findable and categorizable, expand the currency catalog to 100+, show simple spending totals, and harden offline writes with a durable PENDING flush queue (expenses + payments).

## Scope

### In
- Expense search (description / notes) with dedicated screen
- Category picker on Add Expense; show category on expense rows; add custom category
- Spending totals by category (and currency) for a selectable period
- Currency catalog (planned 100+ ISO; shipped INR/USD with Settings filter/search)
- Durable sync flush for `PENDING` expenses (+ splits) and payments; WorkManager retry
- Payment PostgREST upsert + mark `SYNCED`
- Offline-aware status hint (pending count) on Account

### Out
- Live FX conversion / rates (later: snapshot on add expense, Room v15; not mark-to-market)
- Vico charts (Phase 8)
- Full conflict UI beyond last-write-wins by `updatedAtEpochMs`
- Real Room migrations (still destructive; Phase 9)
- Category cloud table / i18n (Phase 9)

## Architecture Decisions

| Decision                                                         | Rationale                                           |
| ---------------------------------------------------------------- | --------------------------------------------------- |
| Local category presets + custom upsert                           | Already seeded; no remote categories needed for MVP |
| Search via Room `LIKE` on description/notes                      | Simple, offline-first                               |
| Spending totals = sum of viewer's split `owedAmount` by category | Matches "my spending," not group totals             |
| `SyncInteractor.flushPending()` + periodic WorkManager           | Durable retry without reinventing WorkManager       |
| Payments join expense flush path                                 | Closes Phase 6 LOCAL_ONLY gap                       |
| LWW: remote wins only on pull when remote `updated_at` ≥ local   | Minimal conflict policy                             |

## Data Model Changes

- No Room version bump expected (categories/payments/expenses already exist).
- Optional remote payment push uses existing Phase 6 SQL.

## Plan

1. Write this plan doc.
2. Categories UI + wire `categoryId` on create.
3. Expand `AppCurrencies` + searchable Settings.
4. Search DAO + screen; wire home search icon.
5. Spending totals domain + screen.
6. Sync queue flush (expenses + payments) + worker + Account pending hint.
7. Docs, version `0.8.0`, tests/build.

---

## Outcome

**Status:** Done (2026-07-22)

Phase 7 delivered expense search, category pickers + custom categories, spending totals by category/period, a Settings currency picker, and a durable PENDING flush for expenses and payments (`SyncInteractor` + WorkManager). Version **0.8.0**. The in-app catalog is **INR + USD** (`AppCurrencies.OPTIONS`); a 100+ ISO list was planned but not shipped. Snapshot FX on add expense (Room v15) landed later.

**Next:** Phase 8 — Stretch / Pro-like Features.

## Screens/UI Added

- Search expenses
- Spending totals
- Category chips on Add Expense; optional "Add category"
- Settings currency search over 100+ codes
- Account: pending sync count / Sync now

## How to Test

1. Add expense with category → list shows category; spending totals include it.
2. Search by description fragment → matching expenses only.
3. Change Settings currency filter; pick from 100+ list.
4. Airplane mode → create expense/payment → go online → Sync now / wait for worker → rows `SYNCED`.
5. Unit: spending aggregator + currency catalog size ≥ 100.

## Known Issues / TODOs

- FX snapshot on add expense shipped later (INR↔USD, Room v15). Group totals still skip mixing currencies into one rollup. Expanding the ISO catalog is still open.
- Capture a local screenshot for this phase when needed.
- Social PENDING flush already best-effort inline; not moved into SyncInteractor this phase.

## Screenshots placeholder

_(No screenshot checked in for this phase.)_
