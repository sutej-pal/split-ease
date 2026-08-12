# Changelog

All notable changes to SplitEase will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Forgot-password via **6-digit email OTP** + in-app set-new-password screen (`OtpType.Email.RECOVERY`); recovery mail uses a dedicated template in mail-service ([phase-12](docs/phase-12-forgot-password-email-otp.md), [supabase-reset-password-otp.html](docs/supabase-reset-password-otp.html))
- Signup blocks duplicate email/phone with clear `already registered` messaging (`auth_email_registered` + `auth_phone_registered`)

### Changed
- Forgot-password copy asks for a reset **code** (not a link); mail-service `buildOtpMail` treats `recovery` / `reset` separately from signup OTP
- Supabase HTTP client engine: **OkHttp** replaces `ktor-client-android` (Realtime WebSockets + safer cancel on navigation)
- Group settle-up / totals moved to dedicated screens (`group_balances/{groupId}`, `group_totals/{groupId}`); back returns to group detail
- Secondary-screen chrome unified: `SeScreen` / `SeTopBar` share one title style (`SeScreenTitleStyle` = titleLarge); spacing via `SeLayout` ([design-tokens](docs/design-tokens.md))

### Removed
- Debug-only `clone` product flavor (and `standard` flavor dimension) used for side-by-side twin installs

### Fixed
- Co-member expense categories were lost on pull when devices used different default UUIDs: stable `cat_*` ids on the wire, Room v12 remaps legacy defaults, pull auto-seeds missing builtins ([supabase-architecture-todos](docs/supabase-architecture-todos.md) #3)
- Flush no longer inflates local `updatedAtEpochMs` after push (keeps cloud LWW aligned with PostgREST); remote-delete prune skips when fetch may hit PostgREST row cap
- Pull could overwrite a newer local `PENDING` expense/payment with a stale remote row: `SyncConflictPolicy` enforces LWW on `updatedAtEpochMs` and protects equal-or-older remote from clobbering unflushed local edits ([supabase-architecture-todos](docs/supabase-architecture-todos.md) #2)
- Remote deletes lingered in Room (pull was upsert-only): after group / 1:1 pull, prune local `SYNCED` expenses and payments missing from the remote set; Realtime DELETE uses the same refresh path ([supabase-architecture-todos](docs/supabase-architecture-todos.md) #1 / extras A5)
- Crash when leaving a screen during an in-flight Supabase call (seen on Motorola): Ktor Android engine closed HTTP on Main (`NetworkOnMainThreadException`); switch to OkHttp and pin `httpEngine` in [SupabaseModule](app/src/main/java/com/splitease/app/data/di/SupabaseModule.kt)
- Password reset showed generic `Something went wrong` after a valid OTP: `updatePassword` no longer fails the whole flow when local hydrate hiccups, and Supabase `same_password` / expired-session errors map to clear copy
- Forgot-password never reveals whether an email is registered: `requestPasswordReset` always soft-succeeds, always opens the OTP screen with `If an account exists...` copy, and OTP verify failures use a generic `Invalid or expired code` (Supabase rate-limits resend server-side)
- Adding a friend from Find people > Friends on SplitEase now syncs `group_members` to Supabase (group pushed first; cloud errors surfaced). Pending invite friends get a GROUP invite instead of a local-only placeholder membership
- Friend/group expenses invisible on the other account: inviter reconcile remapped splits only in Room then marked invites `ACCEPTED` (so `accept_pending_invites` never remapped remote `expense_splits`), and friendships were one-way so the invitee had no Friends list entry; now remaps remote splits (RPC + re-push heal), creates reciprocal friendships on link/accept, and backfills friends from shared activity ([sql/migration_db.sql](docs/sql/migration_db.sql))
- Sign-in briefly flashed Welcome before verify OTP: OTP gate is armed before password session sign-out
- Group expenses never reached Supabase: `expenses_select` used `can_access_expense(id)`, which re-queries `expenses` and cannot see the in-flight row during PostgREST `INSERT ... RETURNING`, so upserts rolled back while Room kept a local PENDING copy ([sql/migration_db.sql](docs/sql/migration_db.sql)); expense push now also re-upserts the group before FK write
- Dark theme text was near-invisible: `SplitEaseColors` light-only aliases (`Navy`, surfaces, etc.) now resolve from `MaterialTheme.colorScheme`; status/nav bar icon contrast follows background luminance
- Group invite share links opened the app but never joined the group: share-link invites stored the inviter's email, so `accept_pending_invites` auto-accepted (burned) the link on the inviter's next sync; share links now use a placeholder email, email-accept skips token-only rows, and token-accept keeps share links multi-use ([sql/migration_db.sql](docs/sql/migration_db.sql))
- Expense create/update now warns when cloud sync fails (local-only save); co-members cannot see expenses that never reached Supabase
- Pull-to-refresh on Groups home, group detail, groups list, and Friends (was wired in ViewModels but never shown in UI)
- Co-member expenses never appeared on other devices: default category ids were random per install, so Room rejected the remote row on `categoryId` FK; pull now drops unknown categories and seeds stable default ids on fresh installs
- Non-creator group members could only sync themselves (RLS); co-members can now SELECT all `group_members` rows ([sql/migration_db.sql](docs/sql/migration_db.sql))
- Group settings showed UUID prefixes for members not in the local friends list; member sync now loads `profiles` into Room and the UI falls back to that display name
- Invite `splitease://` links fail when pasted into Chrome; share links are now **https** (`MAIL_SERVICE_BASE_URL/invite/{token}`) with a mail-service redirect page into the app
- Groups/invites failed to sync under authenticated RLS (`42P17` infinite recursion between `groups` and `group_members`); fixed with SECURITY DEFINER helpers ([sql/migration_db.sql](docs/sql/migration_db.sql))
- Invite share links are only returned after the invite row is successfully upserted to Supabase (no more unclaimable deep links)
- Opening an invite deep link while already signed in now claims the invite and opens the group (previously the token was stored but accept only ran on sign-in)
- Group invite links failed when the group had not synced to Supabase (FK); invites are now pushed after ensuring the group exists, and pending invites are flushed on sync

### Added
- Account profile header with gallery/camera photo picker; Account settings screen for display name, default currency, and language
- Sign-up screen redesigned (welcome header, full name + photo, phone + dial code, currency preference, terms links, Done CTA); profile fields `phone_country_code`, `phone_number`, `preferred_currency` on Supabase `profiles` + Room `users` ([sql/migration_db.sql](docs/sql/migration_db.sql))
- Group Pin Board — shared per-group notepad (Markdown) accessible from the group detail action chips; auto-saves with 2-second debounce; online-only via Supabase `pin_boards` table
- Live group ledger via Supabase Realtime while group detail is open (`GroupLiveSync` + [sql/migration_db.sql](docs/sql/migration_db.sql))
- FCM push for other group members on expense/payment changes (`device_tokens`, Edge Function `notify-group-members`, [fcm-setup.md](docs/fcm-setup.md))
- Deferred invite deep link: mail-service Play Store fallback with `referrer=invite_token%3D...` + Android Play Install Referrer bootstrap into `pending_invite_token` (same OTP / accept path as live deep links)
- Group settings > **Invite via link** opens an Invite link screen (copy / share / change link) instead of jumping straight to the share sheet
- Onboarding-start transactional email trigger via external mail service (`/send-mail`) when onboarding first opens for a signed-in user ([phase-10](docs/phase-10-expense-details-onboarding-invite-mail.md))
- `MailRepository` + remote mail data source and `BuildConfig` keys (`MAIL_SERVICE_BASE_URL`, `MAIL_SERVICE_API_KEY`)
- Invite deep-link join flow: landing screen + join signup > OTP gate > accept invite / join group ([phase-10](docs/phase-10-expense-details-onboarding-invite-mail.md))
- Deep links for `https://splitease.app/invite/{token}` and `splitease://invite/{token}`
- Supabase RPCs `get_invite_preview` + `accept_invite_by_token` ([sql/migration_db.sql](docs/sql/migration_db.sql))
- Post-signup onboarding setup flow: confirm display name + choose default currency before entering the app ([phase-10](docs/phase-10-expense-details-onboarding-invite-mail.md))
- `AuthRepository.updateDisplayName` updates Supabase metadata, Room, and remote profile
- Onboarding-complete preference in app settings (existing users skip automatically)
- Signup email verification via **6-digit OTP** (in-app code entry; no deep link required) ([docs/maintenance-email-otp-verification.md](docs/maintenance-email-otp-verification.md))
- Expense detail screen with edit and delete; create/update/delete appear on Activity ([phase-10](docs/phase-10-expense-details-onboarding-invite-mail.md))
- Brand color system and Material 3 theme (light/dark) from icon indigo/amber tokens ([phase-0](docs/phase-0-project-setup-and-brand-theme.md), [design-tokens](docs/design-tokens.md))
- Find people screen (search friends + device contacts); Group settings > Add people uses it
- Extras backlog: [docs/extras-group-live-updates-notifications.md](docs/extras-group-live-updates-notifications.md)
- Settings > Security: biometric / device-credential app lock with timeout
- Settings hub (appearance, language, currency) and Group settings screen
- Bottom navigation + Groups home UI; shared `Se*` design system

### Changed
- Account no longer shows a manual **Cloud sync** section/action; sync now runs automatically via background workers
- Invite token is cleared only after accept-by-token succeeds (failed claims can retry)
- Signup OTP is strictly **6 digits** (field max + validation; 8-digit codes rejected)
- Confirm-signup email template + configure script set Supabase `mailer_otp_length=6` and OTP-first HTML ([supabase-confirm-signup-otp.html](docs/supabase-confirm-signup-otp.html))
- Invite share text includes https + custom-scheme options; after signup/OTP the app opens the invited group (or Friends tab)
- Pending invites show **Copy** / **Share again** on Friends list and Group settings member rows
- Restored `splitease://invite` intent-filter; https App Links need hosted `assetlinks.json` ([app-links-setup.md](docs/app-links-setup.md))
- Invite share/copy now prefer `splitease://invite/{token}` (plus https fallback) so the installed app opens without verified App Links
- Expense cloud sync: flush groups before expense upsert; warn when an expense stays local-only; RLS fix so group members can insert expenses ([migration_db.sql](docs/sql/migration_db.sql))
- Removed post-signup name confirmation screen; display name from signup is used and users go straight to the app after OTP ([phase-10](docs/phase-10-expense-details-onboarding-invite-mail.md))
- Welcome email still sends once on first signed-in session (no setup UI gate)
- Onboarding now tracks a per-user local `onboarding_email_sent_{userId}` flag to avoid duplicate onboarding-start sends
- Signup always gates on the OTP screen until the code is verified (including when Supabase returns an immediate session)
- Auth signup now uses real 6-digit OTP verify/resend repository calls; mobile/phone onboarding remains TODO
- Locale string packs temporarily emptied (fall back to English); full i18n deferred to last (TODO(i18n-last))
- Auth no longer blanks the UI on `SessionStatus.Initializing` after background (disabled Supabase lifecycle callbacks); loading gate times out to signed-out after 8s; refresh failures clear stale sessions
- Verify-email screen: enter OTP + Verify / Resend code (replaces `open confirmation link` copy)
- **Release size:** R8 minify + resource shrinking enabled; removed unused Coil/Espresso/JUnit4 deps; pruned dead string resources and orphan `commonMain` tree
- ViewModels use string resources for user-facing messages (expenses, groups, sync, import, activity, spending)
- Docs: refreshed README / `ARCHITECTURE.md`; added [docs/README.md](docs/README.md) index (phase docs retained)
- `SyncInteractor.syncForUser` always pulls after flush; flushes social PENDING before expenses/payments
- Group detail resume refreshes via full sync + targeted group expense pull
- Splash/system `colors.xml` aligned to brand indigo background

## [1.0.0] - 2026-07-23 — phase-9

### Added
- Seven locale packs (`es`, `fr`, `de`, `pt`, `hi`, `ja`, `it`) plus Settings > Language
- Room migrations 1–2–3–4 (invites, groupType, recurring columns)
- Email confirmation UX (pending verify screen, resend) and `splitease://auth-callback` deep links
- Compose Welcome smoke test + Room migration instrumented test
- Release checklist and Play Store listing draft

### Changed
- Version **1.0.0** (`versionCode` 2)
- Removed Room destructive migration fallback
- Auth signup returns `SignUpResult` (session vs pending confirmation)

## [0.9.0] - 2026-07-22 — phase-8

### Added
- Region-aware settle-up pay actions (UPI / PayPal / Venmo / share)
- CSV transaction import (Account > Import) with preview and expense creation
- Vico column chart on Spending totals
- Unit tests for payment deep links and CSV parser

## [0.8.0] - 2026-07-22 — phase-7

### Added
- Expense search screen (description / notes)
- Category picker + custom categories on Add Expense; category on expense rows
- Spending totals by category / period (Account > Spending)
- 100+ ISO currency catalog with Settings search/filter
- Durable PENDING flush for expenses + payments (`SyncInteractor`, WorkManager, Account Sync now)

### Changed
- Payments created as `PENDING` (were `LOCAL_ONLY`) for cloud upload retries

## [0.7.0] - 2026-07-22 — phase-6

### Added
- Settle up / record payment (Room `payments`) applied to derived balances
- Recurring expense frequency on create; WorkManager daily generator; Room v4 schedule fields
- Supabase SQL [migration_db.sql](docs/sql/migration_db.sql)
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
- Session-gated navigation: Welcome > Login/Signup/Forgot > Home
- `AuthRepository` / `SupabaseAuthRepository` with Room user upsert + category seeding
- Auth credentials via `local.properties` > `BuildConfig` (URL + anon key only)
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
