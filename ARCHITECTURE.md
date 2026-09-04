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

Credentials: `SUPABASE_URL` + `SUPABASE_ANON_KEY` + mail config (`MAIL_SERVICE_BASE_URL`, `MAIL_SERVICE_API_KEY`) from gitignored `local.properties` → `BuildConfig`. Optional `GOOGLE_WEB_CLIENT_ID` (Google Cloud **Web** OAuth client ID; not a secret) for native Google Sign-In. Optional `EXCHANGE_RATE_API_KEY` for add-expense FX snapshots. Never ship database/service-role secrets or the Google client secret in the app. Supabase HTTP uses Ktor **OkHttp** (`httpEngine = OkHttp.create()` in `SupabaseModule`).

## Data & sync

- **IDs:** string UUIDs locally; `remoteId` stores the cloud id when synced.
- **Sync bookmarks:** `syncStatus` (`LOCAL_ONLY` \| `PENDING` \| `SYNCED`) + `updatedAtEpochMs`.
- **Flush then pull:** `SyncInteractor.syncForUser` flushes PENDING groups/members/invites/expenses/payments/pin boards, then pulls friends/groups/expenses/payments. Also runs on login / cold start / Account Sync / group resume.
- **Conflict policy (pull):** Last-write-wins on `updatedAtEpochMs` via `SyncConflictPolicy`. A local `PENDING` / `LOCAL_ONLY` row is never replaced by an equal-or-older remote snapshot; `SYNCED` skips strictly older remote.
- **Categories (cloud):** Built-in defaults use stable ids (`cat_general`, `cat_food`, …) on `expenses.category_id`. No Supabase `categories` table; pull auto-seeds missing defaults; push omits custom/local-only ids. Room v12 remaps legacy random default ids.
- **Pin board:** Shared plain-text notepad per group. Room `pin_boards` cache (write locally, then flush). Debounced autosave (~2s) plus an explicit **Save** action. [PinBoardInteractor.load](app/src/main/java/com/splitease/app/data/pinboard/PinBoardInteractor.kt) fetches Supabase on open, resume, and idle poll so another member’s save is applied unless this device has a PENDING draft. No live collaborative cursor. See [PinBoardPolicy](app/src/main/java/com/splitease/app/data/pinboard/PinBoardPolicy.kt).
- **Remote deletes:** After a successful group (or 1:1 involving-user) pull, local `SYNCED` expenses/payments absent from the remote id set are removed from Room. `PENDING` / `LOCAL_ONLY` are never pruned. Soft-delete / `deleted_at` is not used (cloud rows are hard-deleted).
- **Balances:** derived from Room expenses/splits/payments (no balance tables). Stored `amount`/`currencyCode` after optional add-expense FX snapshot (INR↔USD via ExchangeRate-API or a custom rate; original amount kept on the Room row). Balances are not revalued as market rates move.
- **Activity events:** local `activity_events` (Room); not cloud-synced. The feed is **newest-first**. Creating a group then adding an expense in the same minute lists the expense above “you created the group” because the expense is the later action. Time labels use short local time (hour:minute), so both rows can show the same clock; sort still uses milliseconds. An expense’s sort key is at least 1ms after its group’s created time so it cannot appear as if it predates the group; the visible time is still the real event time.
- **Sign-out:** `flushBeforeSignOut` waits for in-flight expense writes then flushes PENDING rows while the session is valid (10s cap per step). `discardLocalWrites` then invalidates in-flight persist callbacks so a hung cloud push cannot re-insert into Room after wipe. Offline sign-out can still drop unsynced rows after that timeout.
- **Expense recorded time:** cloud `expenses` has `updated_at_epoch_ms` and `expense_date_epoch_ms`, not a separate created-at. First hydrate fills local `createdAtEpochMs` from `updated_at` (else expense date). Add-expense stamps save time unless the user picked a custom date.
- **Schema SoT:** [docs/data-dictionary.md](docs/data-dictionary.md) + `app/schemas/` (Room **v15**).

Apply Supabase SQL via [docs/sql/migration_db.sql](docs/sql/migration_db.sql) (single canonical file). Optional FCM notify triggers are included and no-op until `app.settings` are set — see [docs/fcm-setup.md](docs/fcm-setup.md).

Group detail keeps Room fresh via Supabase Realtime (`GroupLiveSync`) while the screen is resumed; background members are notified via FCM when configured. Mute-all / mute-group live in `notification_prefs` (Settings + Group settings).

## Feature map (packages)

| Area                     | Key packages / types                                                                                                                                                                     |           |          |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- | -------- |
| Auth                     | `AuthRepository`, `SupabaseAuthRepository`, `presentation/auth` (signup + password-reset OTP + Google ID token), `presentation/onboarding` (welcome-email side effect; no setup UI)      |           |          |
| Invites                  | `InviteLinks`, `InstallReferrerInviteBootstrap` (Play deferred deep link), `presentation/invite` (deep-link landing + join signup), `get_invite_preview` / `accept_invite_by_token` RPCs |           |          |
| Friends & groups         | `SocialInteractor`, `SocialRemoteDataSource`, `presentation/friends\                                                                                                                     | groups\   | home`    |
| Expenses                 | `SplitCalculator`, `ExpenseInteractor`, `presentation/expenses`                                                                                                                          |           |          |
| Balances                 | `BalanceCalculator`, `DebtSimplifier`, `BalanceInteractor`                                                                                                                               |           |          |
| Settlements / recurring  | `PaymentInteractor`, `RecurrenceScheduler`, `RecurringExpenseWorker`                                                                                                                     |           |          |
| Search / spending / sync | `SyncInteractor`, `SpendingTotalsCalculator`, `presentation/search\                                                                                                                      | spending\ | account` |
| Stretch                  | `PaymentDeepLinks`, `CsvTransactionParser`, `SpendingCategoryChart`, `ExchangeRateCurrencyService`                                                                                         |           |          |
| Pin Board                | `PinBoardInteractor`, `PinBoardRemoteDataSource`, `presentation/pinboard`                                                                                                                |           |          |
| Settings                 | `AppSettingsRepository` (currency, theme, locale, biometric lock, pending invite token, welcome-mail flags)                                                                                |           |          |

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
