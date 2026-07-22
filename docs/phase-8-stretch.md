# Phase 8 — Stretch / Pro-like Features

## Phase Goal

Add region-aware “pay” deep links from settlements, CSV transaction import into expenses, and Vico charts on spending totals.

## Scope

### In
- Settle-up actions: UPI (INR), PayPal, Venmo, generic share of payment request
- CSV import (date, description, amount, optional currency/category) with review + create expenses
- Vico column/bar chart on Spending totals (by category for active currency bucket)
- Unit tests for deep-link URI builders and CSV parser

### Out
- Real Open Banking / account aggregation
- Live payment confirmation webhooks
- Store listing assets / i18n (Phase 9)
- FX conversion

## Architecture Decisions

| Decision | Rationale |
|---|---|
| Intent / URI builders in `domain.payment` | Pure, testable; UI only launches |
| Region from settlement currency (not locale alone) | Matches owed amount currency |
| CSV → `CreateExpenseInput` as equal split with current user only | Safe default; user can edit later |
| Vico compose-m3 (tech stack) | Charts without custom Canvas |
| No new Room tables | Import writes normal expenses |

## Plan

1. Document this plan.
2. Payment deep-link helpers + Settle-up UI buttons.
3. CSV parser + Import screen (SAF file pick) + Account entry.
4. Add Vico dependency; chart on Spending totals.
5. Docs, version `0.9.0`, tests/build.

---

## Outcome

**Status:** Done (2026-07-22)

Phase 8 delivered region-aware settle-up payment deep links (UPI / PayPal / Venmo / share), CSV transaction import with preview, and a Vico column chart on Spending totals. Version **0.9.0**.

**Next:** Phase 9 — Polish, Testing, and Release Prep.

## Screens/UI Added

- Settle up: Pay via UPI / PayPal / Venmo / Share
- Import transactions (CSV)
- Spending chart above category list

## How to Test

1. Open Settle up for an INR debt → Pay via UPI opens a chooser (or fails gracefully if no UPI app).
2. PayPal / Venmo / Share open expected intents.
3. Import a CSV with 2–3 rows → review → expenses appear.
4. Spending screen shows a chart when totals exist.
5. Unit: `PaymentDeepLinks*` + `CsvTransactionParser*`

## Known Issues / TODOs

- UPI requires a payee VPA; without a stored handle we prefill amount/currency only.
- CSV import creates viewer-only equal-split expenses (not auto-assigned to a group).
- Capture `docs/screenshots/phase-8.png`.

## Screenshots placeholder

![phase-8-screenshot](./screenshots/phase-8.png)
