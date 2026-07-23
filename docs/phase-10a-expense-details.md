# Phase 10a — Expense Details (edit / delete + Activity)

## Phase Goal

Add an expense details screen with edit and delete, and surface those mutations on the Activity tab.

## Scope

### In

- Expense detail screen (amount, payer, date, splits, notes)
- Edit via existing add-expense form (prefilled)
- Delete with confirm modal; remote best-effort delete
- Local `activity_events` table so create / update / delete appear on Activity
- Tap expense rows from group/friend ledgers and Activity → detail

### Out

- Syncing activity events across devices
- Soft-delete tombstones on Supabase
- Payment detail screen

## Architecture Decisions

1. **Activity events are local** — `activity_events` Room table records EXPENSE_ADDED / UPDATED / DELETED for the acting device so deletes remain visible after the expense row is gone.
2. **Stable expense id on edit** — `ExpenseInteractor.updateExpense` keeps the expense id and remaps splits by participant when possible.
3. **Legacy Activity rows** — expenses with no events still appear (pre-migration data); once any event exists for an expense id, live expense rows for that id are suppressed to avoid duplicates.

## Data Model Changes

Room **v5**: `activity_events` (`id`, `kind`, `title`, `subtitle`, `amountLabel`, `actorUserId`, `relatedExpenseId`, `involvedUserIds`, `sortEpochMs`).

## Files Added/Modified

- `presentation/expenses/ExpenseDetailScreen.kt` (new)
- `data/expense/ExpenseInteractor.kt` — update/delete + activity logging
- `data/remote/ExpenseRemoteDataSource.kt` — `deleteExpense`
- Activity event entity/dao/repo + DB migration 4→5
- `ActivityViewModel` / `ActivityScreen` — events + navigation
- Nav routes `expense_detail/{id}`, edit via `expenseId` on add expense
- Ledger / friend / group detail tap → detail

## Screens/UI Added

- Expense detail (edit + delete)
- Activity icons for updated / deleted expense events

## How to Test

1. Create an expense → Activity shows it (or “legacy” expense row / added event).
2. Open expense from group ledger → Edit → change amount → Activity shows “Updated: …”.
3. Delete expense → removed from ledger; Activity shows “Deleted: …”.
4. Tap an updated Activity row → opens detail when expense still exists.

## Known Issues / TODOs

- Activity events do not sync to other devices.
- Remote delete is best-effort; offline deletes may leave cloud rows until a later sync strategy (extras A5).
- Edit form may not restore percentage/shares fields from stored splits (unequal amounts are prefilled).

## Screenshots

_Placeholder_
