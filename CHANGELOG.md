# Changelog

All notable changes to SplitEase will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Settings screen with app-wide currency (SharedPreferences); applied to new expenses/groups

### Changed
- Removed currency fields from Add Expense, Create Group, and Edit Group screens

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
