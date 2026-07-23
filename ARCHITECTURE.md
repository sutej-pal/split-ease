# SplitEase Architecture

Living design document. Update (do not recreate) when a new architectural layer or major pattern is introduced.

## Overview

SplitEase is a native Android expense-sharing app (Kotlin, Jetpack Compose) that follows **MVVM + Clean Architecture** with an offline-first Room cache and **Supabase** as the cloud backend (Auth now; PostgREST/Storage in later phases). Original plans mentioned Firebase; Phase 2 switched to Supabase per project credentials.

## Layers

```
presentation/   # Compose UI, ViewModels, Navigation
domain/         # Models, repository interfaces, pure business logic
data/           # Room, Supabase, repository implementations, DTOs
```

Package root: `com.splitease.app`

## Module Structure

- Single Gradle module `:app` for MVP speed.
- Layer packages live under `com.splitease.app.{presentation,domain,data}`.
- Multi-module split may be reconsidered after Phase 5 if build times or boundaries warrant it.

## Offline-first data layer (Phase 1)

```
domain/model          # User, Friend, Group, Expense, …
domain/repository     # Interfaces only (no Android deps)
data/local/entity     # Room rows
data/local/dao        # Queries + @Transaction helpers
data/local/db         # SplitEaseDatabase (v1)
data/repository       # Room*Repository implementations
data/di               # DatabaseModule + RepositoryModule (Hilt)
```

**Money:** domain and Room entities use `java.math.BigDecimal`; persisted as TEXT plain strings via `SplitEaseTypeConverters`.

**IDs:** string UUIDs locally; `remoteId` stores the Supabase auth user id when synced.

**Sync bookmarks:** `syncStatus` (`LOCAL_ONLY` | `PENDING` | `SYNCED`) + `updatedAtEpochMs` — write path only for now; conflict/queue logic in Phase 7.

**Schema source of truth:** `docs/data-dictionary.md` and exported JSON under `app/schemas/`.

## Tech Stack

| Concern | Choice |
|---|---|
| UI | Jetpack Compose, Material 3 |
| DI | Hilt |
| Local DB | Room (offline-first source of truth from Phase 1) |
| Async | Coroutines + Flow |
| Navigation | Navigation Compose |
| Backend | **Supabase** (Auth Phase 2; PostgREST/Storage later) |
| Images | Coil + Supabase Storage (later) |
| Charts | Vico (Phase 8) |
| Money math | `BigDecimal` only (never Float/Double) |

## Conventions

- Domain and data public APIs carry KDoc.
- Financial calculations are pure Kotlin in `domain`, unit-tested with rounding edge cases.
- Documentation for each phase lives under `docs/phase-<N>-*.md`; schema in `docs/data-dictionary.md`.

## Phase 0 Notes

Foundations only: Gradle/Compose/Hilt/Room classpath, theme, Welcome screen. No domain entities or cloud wiring yet.

**As shipped (0.1.0):**
- Single `:app` module; packages under `com.splitease.app.{presentation,domain,data}`
- Entry: `SplitEaseApplication` (`@HiltAndroidApp`) → `MainActivity` → `SplitEaseNavHost` → `WelcomeScreen`
- Style gate: ktlint (`./gradlew ktlintCheck`); Compose function naming allowed via `.editorconfig`
- SDKs: min 26 / target & compile 36

## Phase 1 Notes

Local persistence is live: repositories inject Room DAOs. Auth (Phase 2) upserts the signed-in user and calls `CategoryRepository.ensureDefaults()` after sign-in/sign-up.

## Authentication (Phase 2)

```
domain/repository/AuthRepository
data/repository/SupabaseAuthRepository
data/di/SupabaseModule          # createSupabaseClient + Auth + Postgrest
presentation/auth/*             # screens + AuthViewModel
```

- Credentials: `SUPABASE_URL` + `SUPABASE_ANON_KEY` from gitignored `local.properties` → `BuildConfig`.
- **Never** ship the database password in the Android app.
- Session Flow from `supabase.auth.sessionStatus` gates Welcome/auth vs Home.
- Google OAuth is stubbed pending Supabase provider + deep-link setup.
- **Email confirmation (Phase 9):** signup may return `SignUpResult.PendingEmailConfirmation`; deep link `splitease://auth-callback` (allow-list in Supabase Dashboard). Confirm email should be **ON** for production.

## Friends & groups (Phase 3)

```
data/remote/SocialRemoteDataSource   # PostgREST
data/social/SocialInteractor         # Room-first + sync
presentation/friends|groups|home
```

- Apply `docs/sql/phase-3-schema.sql` and `docs/sql/phase-3b-invites.sql` once in Supabase SQL Editor.
- Offline: Room remains source of truth; remote upsert is best-effort (`PENDING` → `SYNCED`).
- Non-users: pending `invites` + shareable link; on auth, `accept_pending_invites()` claims friendships/group memberships.
- Soft local group membership for invited users so they can be expense participants immediately.

## Expenses (Phase 4)

```
domain/split/SplitCalculator
data/expense/ExpenseInteractor
data/remote/ExpenseRemoteDataSource
presentation/expenses
```

- Apply `docs/sql/phase-4-expenses.sql` (expenses, splits, RLS, accept remap).
- Invited placeholders allowed on split `user_id` until accept remaps to auth uid.

## Balances (Phase 5)

```
domain/balance/BalanceCalculator + DebtSimplifier
data/balance/BalanceInteractor
presentation/balances
```

- Balances are **derived** from Room expenses/splits (no balance tables).
- Convention: net > 0 ⇒ is owed; net < 0 ⇒ owes. Per-currency buckets; no FX.
- Debt simplification: greedy debtor/creditor matching to minimize transfers.
- Settlements (`payments`) not applied until Phase 6.

## Settlements & recurring (Phase 6)

```
domain/balance.applyPayments + domain/recurrence/RecurrenceScheduler
data/payment/PaymentInteractor
data/recurring/RecurringExpenseWorker   # WorkManager + HiltWorker
presentation/settlements
```

- Room **v4**: `nextOccurrenceEpochMs`, `recurringTemplateId` on expenses.
- Payments: fromUser +amount / toUser −amount on nets before simplify.
- Recurring templates generate one-off instances via daily WorkManager job.
- Apply `docs/sql/phase-6-payments.sql` in Supabase (payments + optional recurrence columns).
- Cloud payment sync remains local-first until Phase 7 queue.

## Search, categories, currency, sync (Phase 7)

```
domain/spending/SpendingTotalsCalculator
domain/settings/AppCurrencies          # 100+ ISO codes
data/sync/SyncInteractor + SyncWorker
data/spending/SpendingInteractor
data/remote/PaymentRemoteDataSource
presentation/search|spending + Account sync
```

- Search: Room `LIKE` on description/notes; Groups home search icon.
- Categories: picker + custom upsert on Add Expense.
- Spending: viewer owed amounts by category × currency × period.
- Sync: `syncForUser` flushes PENDING groups/members/expenses/payments then **always** pulls friends/groups/expenses/payments; periodic WorkManager + Account Sync now + login/cold start.
- Group detail on resume: full `syncForUser` + targeted group expense pull (see extras doc for push notifications).
- FX still not applied; balances remain per-currency buckets.

## Extras (post Phase 9)

Out-of-roadmap work tracked in [docs/extras-group-live-updates-notifications.md](docs/extras-group-live-updates-notifications.md):

- Member push notifications on expense/payment changes (FCM + Edge Function — not started; needs library approval).
- Stronger multi-device visibility when opening a group (MVP sync-on-open done).

## Stretch features (Phase 8)

```
domain/payment/PaymentDeepLinks
domain/imports/CsvTransactionParser
data/imports/TransactionImportInteractor
presentation/imports + settle-up pay buttons
presentation/spending/SpendingCategoryChart  # Vico 2.1
```

- Settle up (when you are the payer): open UPI / PayPal / Venmo or share a payment request, then record settlement.
- CSV import creates viewer-only equal-split expenses (optional category match/create).
- Spending screen shows a Vico column chart above the category list.

## Polish & release (Phase 9)

```
data/local/db/SplitEaseMigrations   # 1→2→3→4
domain/settings/AppLocale
presentation/settings/LanguageSettingsScreen
presentation/auth/VerifyEmailScreen
docs/release-checklist.md + docs/store-listing.md
```

- Room upgrades use explicit migrations (no destructive fallback).
- Per-app language via `AppCompatDelegate.setApplicationLocales` (`values-es|fr|de|pt|hi|ja|it`).
- Version **1.0.0**.

## App settings (currency)

```
domain/settings/AppSettingsRepository
data/settings/SharedPreferencesAppSettingsRepository
presentation/settings
```

- Single app-wide currency chosen in Settings; new expenses/groups use it automatically.
- No per-expense or per-group currency pickers on create/edit screens.
- Catalog: 100+ ISO codes with filter in Settings (`AppCurrencies`).

## Design system (UI kit)

Theme inspired by [Apzo SaaS](https://demo.goodlayers.com/apzo/saas/): electric blue primary (`#2F57EF`), soft gray background, navy copy.

```
presentation/theme/          # SplitEaseColors, Theme, Typography
presentation/ui/             # Shared Se* components + @Preview
```

Reusable components (`SePrimaryButton`, `SeTextField`, `SeTopBar`, `SeListRow`, `SeTypeChip`, `SeMoneyText`, …) wrap Material 3 with brand tokens. Screens should prefer these over raw Material widgets for consistency.
