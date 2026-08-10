# SplitEase Architecture

Living design document. Update (do not recreate) when a new architectural layer or major pattern is introduced.

## Overview

SplitEase is a native Android expense-sharing app (Kotlin, Jetpack Compose) using **MVVM + Clean Architecture**, an offline-first Room cache, and **Supabase** (Auth + PostgREST). Package: `com.splitease.app`.

```
presentation/   # Compose UI, ViewModels, Navigation
domain/         # Models, repository interfaces, pure business logic
data/           # Room, Supabase, repository implementations, DTOs
```

Single Gradle module `:app`. Money uses `java.math.BigDecimal` only (never `Float`/`Double`).

## Tech stack

| Concern    | Choice                      |
| ---------- | --------------------------- |
| UI         | Jetpack Compose, Material 3 |
| DI         | Hilt                        |
| Local DB   | Room (offline-first)        |
| Async      | Coroutines + Flow           |
| Navigation | Navigation Compose          |
| Backend    | Supabase Auth + PostgREST   |
| Charts     | Vico                        |
| Work       | WorkManager (+ HiltWorker)  |
| Money math | `BigDecimal`                |

Credentials: `SUPABASE_URL` + `SUPABASE_ANON_KEY` + mail config (`MAIL_SERVICE_BASE_URL`, `MAIL_SERVICE_API_KEY`) from gitignored `local.properties` → `BuildConfig`. Never ship database/service-role secrets in the app. Supabase HTTP uses Ktor **OkHttp** (`httpEngine = OkHttp.create()` in `SupabaseModule`).

## Data & sync

- **IDs:** string UUIDs locally; `remoteId` stores the cloud id when synced.
- **Sync bookmarks:** `syncStatus` (`LOCAL_ONLY` \| `PENDING` \| `SYNCED`) + `updatedAtEpochMs`.
- **Flush then pull:** `SyncInteractor.syncForUser` flushes PENDING groups/members/expenses/payments, then pulls friends/groups/expenses/payments. Also runs on login / cold start / Account Sync / group resume.
- **Balances:** derived from Room expenses/splits/payments (no balance tables). Per-currency buckets; no live FX.
- **Activity events:** local `activity_events` (Room); not cloud-synced.
- **Schema SoT:** [docs/data-dictionary.md](docs/data-dictionary.md) + `app/schemas/`.

Apply Supabase SQL via [docs/sql/migration_db.sql](docs/sql/migration_db.sql) (single canonical file). Optional FCM notify triggers are included and no-op until `app.settings` are set — see [docs/fcm-setup.md](docs/fcm-setup.md).

Group detail keeps Room fresh via Supabase Realtime (`GroupLiveSync`) while the screen is resumed; background members are notified via FCM when configured.

## Feature map (packages)

| Area                     | Key packages / types                                                                                                                                                                     |           |          |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- | -------- |
| Auth                     | `AuthRepository`, `SupabaseAuthRepository`, `presentation/auth` (signup + password-reset OTP), `presentation/onboarding` (welcome-email side effect; no setup UI)                        |           |          |
| Invites                  | `InviteLinks`, `InstallReferrerInviteBootstrap` (Play deferred deep link), `presentation/invite` (deep-link landing + join signup), `get_invite_preview` / `accept_invite_by_token` RPCs |           |          |
| Friends & groups         | `SocialInteractor`, `SocialRemoteDataSource`, `presentation/friends\                                                                                                                     | groups\   | home`    |
| Expenses                 | `SplitCalculator`, `ExpenseInteractor`, `presentation/expenses`                                                                                                                          |           |          |
| Balances                 | `BalanceCalculator`, `DebtSimplifier`, `BalanceInteractor`                                                                                                                               |           |          |
| Settlements / recurring  | `PaymentInteractor`, `RecurrenceScheduler`, `RecurringExpenseWorker`                                                                                                                     |           |          |
| Search / spending / sync | `SyncInteractor`, `SpendingTotalsCalculator`, `presentation/search\                                                                                                                      | spending\ | account` |
| Stretch                  | `PaymentDeepLinks`, `CsvTransactionParser`, `SpendingCategoryChart`                                                                                                                      |           |          |
| Pin Board                | `PinBoardInteractor`, `PinBoardRemoteDataSource`, `presentation/pinboard`                                                                                                                |           |          |
| Settings                 | `AppSettingsRepository` (currency, theme, locale, biometric lock, pending invite token, onboarding flags)                                                                                |           |          |

Phase write-ups (historical Plan + Outcome): [docs/README.md](docs/README.md).

## Theming & UI kit

Hand-authored Material 3 schemes from the app icon (indigo + amber). No Material You dynamic color by default.

Canonical tokens: [docs/design-tokens.md](docs/design-tokens.md) · code: `presentation/theme/` · phase: [docs/phase-0-project-setup-and-brand-theme.md](docs/phase-0-project-setup-and-brand-theme.md).

Reusable `Se*` components in `presentation/ui/` wrap Material 3 with brand tokens. Prefer `Se*` / `MaterialTheme.colorScheme` over raw hex.

Secondary screens with back + title use **one** chrome: `SeScreen` → `SeTopBar` → `SeScreenTitleText` (`SeScreenTitleStyle` / `titleLarge`). Spacing rhythm: `SeLayout` (see [design-tokens.md](docs/design-tokens.md#screen-chrome-back--title)).

## Release size

- Release: R8 minify + `shrinkResources` + optimized resource shrinking.
- Keep rules: `app/proguard-rules.pro` (Hilt, Room, Kotlin serialization / Supabase DTOs).

## Conventions

- Domain and data public APIs carry KDoc.
- Financial calculations are pure Kotlin in `domain`, unit-tested with rounding edge cases.
- Documentation for each phase lives under `docs/phase-<N>-*.md`; keep Outcome sections; do not delete prior phase docs.
