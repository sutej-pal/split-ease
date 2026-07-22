# Changelog

All notable changes to SplitEase will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Documented MVP decision to skip Supabase signup email confirmation; marked re-enable as pre-production TODO

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
