# Changelog

All notable changes to SplitEase will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Debug-only `clone` product flavor (`com.splitease.app.clone`) for side-by-side multi-device sync testing
- Settings → Security: biometric / device-credential app lock with configurable timeout
- Multi-device hydrate: login / cold start / Activity / Groups / Sync now flush PENDING groups+members+expenses+payments then pull from Supabase
- Settings screen with app-wide currency (SharedPreferences); applied to new expenses/groups
- Settings hub with Preferences (Appearance: Light / Dark / System default) and General (Currency) list sections
- Group settings screen (edit name/type, add people, invite link, simplify-debts toggle, leave/delete)
- Bottom navigation (Groups, Friends, Activity, Account) and Groups home UI with balances + Add expense FAB
- Create group screen redesign with Friends / Home / Other types (Room v3 `groupType`)
- Shared `presentation/ui` design system (Apzo SaaS–inspired palette) with Compose `@Preview`s
- App-wide migration onto `Se*` components (screens, lists, buttons, fields, money, feedback)

### Changed
- `SyncInteractor` now flushes social PENDING (groups/members) before expenses/payments and pulls cloud data via `syncForUser`
- Group create uploads owner membership even when extra member upserts fail (invite placeholders)
- Settings Preferences section lists Appearance then Security
- Group detail gear opens dedicated Group settings (instead of inline edit)
- Status bar uses dark icons with the forced light theme; nested scaffold no longer double-pads the top inset
- Search icon available on Groups, Friends, and Activity tabs (not on Settings / Account)
- Activity tab lists expenses and settlements involving you (newest first)
- Group detail redesigned: type header, Settle/Balances/Totals/Members chips, solo empty state, extended Add expense FAB
- Removed currency fields from Add Expense, Create Group, and Edit Group screens
- Global theme switched to light Apzo-inspired blue SaaS look; screens migrate onto `Se*` components
- Groups home top-bar action uses GroupAdd → Create group (Friends still use add-friend)

## [0.9.0] - 2026-07-22 — phase-8

### Added
- Region-aware settle-up pay actions (UPI / PayPal / Venmo / share)
- CSV transaction import (Account → Import) with preview and expense creation
- Vico column chart on Spending totals
- Unit tests for payment deep links and CSV parser

## [0.8.0] - 2026-07-22 — phase-7

### Added
- Expense search screen (description / notes)
- Category picker + custom categories on Add Expense; category on expense rows
- Spending totals by category / period (Account → Spending)
- 100+ ISO currency catalog with Settings search/filter
- Durable PENDING flush for expenses + payments (`SyncInteractor`, WorkManager, Account Sync now)

### Changed
- Payments created as `PENDING` (were `LOCAL_ONLY`) for cloud upload retries

## [0.7.0] - 2026-07-22 — phase-6

### Added
- Settle up / record payment (Room `payments`) applied to derived balances
- Recurring expense frequency on create; WorkManager daily generator; Room v4 schedule fields
- Supabase SQL [phase-6-payments.sql](docs/sql/phase-6-payments.sql)
- Unit tests for payment application and recurrence scheduling

### Changed
- Balances now subtract settlements before debt simplification


## [0.6.0] - 2026-07-22 — phase-5

### Added
- Net balances from expenses/splits (`BalanceCalculator`) per currency
- Debt simplification (`DebtSimplifier`) — minimize who-owes-whom transfers
- Balances hub from Home; balance headers on group and friend detail
- Unit tests for balance math and simplification

## [0.5.0] - 2026-07-22 — phase-4

### Added
- Expense create/list for groups and 1:1 friends (equal, unequal, %, shares)
- `SplitCalculator` with BigDecimal remainder rules + unit tests
- Supabase `expenses` / `expense_splits` sync; invite accept remaps placeholder split ids
- Invited (pending) users can be expense participants immediately

## [0.4.1] - 2026-07-22 — phase-3 invites

### Added
- Email invites for non-users (friends + groups): Room `invites`, Supabase `invites` + `accept_pending_invites()`
- Share-sheet invite link when the recipient is not on SplitEase yet
- Auto-claim pending invites on sign-up / sign-in

### Changed
- Add-friend / group-member by email no longer requires an existing SplitEase account

## [0.4.0] - 2026-07-22 — phase-3

### Added
- Friends list + add friend by email (Room + Supabase PostgREST)
- Groups list, create/edit, detail with members and expenses placeholder
- `profiles` / `friends` / `groups` / `group_members` SQL schema + RLS
- Home hub navigation into Friends and Groups
- Profile upsert on sign-in/sign-up for email lookup

## [0.3.0] - 2026-07-22 — phase-2

### Added
- Supabase Auth (email/password sign-up, sign-in, sign-out, password reset)
- Session-gated navigation: Welcome → Login/Signup/Forgot → Home
- `AuthRepository` / `SupabaseAuthRepository` with Room user upsert + category seeding
- Auth credentials via `local.properties` → `BuildConfig` (URL + anon key only)
- `AuthViewModel` unit tests

### Changed
- Backend choice from Firebase default to **Supabase**

## [0.2.0] - 2026-07-22 — phase-1

### Added
- Domain models: User, Friend, Group, GroupMember, Category, Expense, ExpenseSplit, Payment
- Room database v1 (`splitease.db`) with BigDecimal type converters and sync bookmark columns
- Repository interfaces + Room implementations wired via Hilt
- Default category seeding helper (`CategoryRepository.ensureDefaults`)
- Unit tests for converters/mappers; instrumented in-memory DAO tests
- `docs/data-dictionary.md` schema tables; Room schema export under `app/schemas/`

## [0.1.0] - 2026-07-22 — phase-0

### Added
- Android project skeleton (`com.splitease.app`) with Gradle Kotlin DSL and version catalog
- Jetpack Compose Material 3 theme (dynamic color + teal brand palette)
- Hilt, Navigation Compose, and Room on the classpath
- Welcome screen as Navigation start destination
- ktlint + Compose-aware `.editorconfig`
- Documentation scaffolding (`docs/`, `PROGRESS.md`, `ARCHITECTURE.md`, `data-dictionary.md`)
- Cursor always-apply rule for SplitEase working agreement
