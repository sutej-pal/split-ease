# SplitEase App — TODO

Consolidated open work from `PROGRESS.md`, phase docs, extras, and in-code `TODO` markers.

## Auth & onboarding

- [ ] **TODO(auth-mobile-onboarding)** — Allow users to onboard with a mobile phone number (SMS OTP / phone auth) in addition to email.
- [ ] **OTP ops checklist** — Keep Supabase signup OTP operational (Confirm email ON, `{{ .Token }}` in Confirm signup template, SMTP/provider health). See [docs/maintenance-email-otp-verification.md](docs/maintenance-email-otp-verification.md).
- [ ] **Resend domain** — Verify a sending domain and set `MAIL_FROM` before production OTP; Resend testing mode only delivers to the account email.
- [ ] **Google Sign-In** — Wire Google provider in Supabase + Android OAuth redirect / deep link.
- [ ] **Password reset UX** — In-app new-password screen (today email link opens browser/Supabase page).
- [ ] **Onboarding-complete cloud flag** — Currently per-device/local only; reinstall or new device re-triggers onboarding.
- [ ] **Profile photo in onboarding** — Not included yet (future enhancement).

## Invites & App Links

- [ ] **Invite email delivery** — MVP uses the system share sheet; automated send via mail-service still TODO.
- [ ] **App Links / invite https** — Host [docs/assetlinks.json](docs/assetlinks.json) at `/.well-known/assetlinks.json` on invite hosts for verified Open-by-default links. See [docs/app-links-setup.md](docs/app-links-setup.md).
- [ ] **Install Referrer E2E** — Full deferred-invite proof needs a Play install (Internal testing). Sideload does not populate referrer.
- [ ] **Redeploy mail-service `/invite/:token`** — Open-app then Play Store with `referrer=invite_token%3D…`; redeploy Render after pulling mail-service changes.

## Sync, live updates & notifications

- [ ] **Group live updates & push** — Finish Firebase/`google-services.json` + Edge Function deploy per [docs/fcm-setup.md](docs/fcm-setup.md).
- [ ] **A5 — Remote delete tombstones** — Propagate deletes from cloud so remote deletes disappear locally (pull is upsert-mostly today).
- [ ] **A6 — Pull-to-refresh** — Optional pull-to-refresh gesture on group ledger.
- [ ] **B6 — Activity badges** — Extend Activity feed / badge when remote events arrive.
- [ ] **B8 — Notification preferences** — Mute group / mute all.
- [ ] **Category sync** — Full category cloud sync; older devices still use local UUIDs (remote pull drops unknown `category_id`).
- [ ] **FX rates** — Multi-currency remains per-bucket; live FX is deferred.
- [ ] **Social PENDING flush** — Groups/members flush in `SyncInteractor` before expenses (still verify Supabase SQL is applied).
- [ ] **Activity events cross-device** — Activity events do not sync to other devices.
- [ ] **Pin board offline / realtime** — No offline cache; second device sees updates only on (re-)open.

## Payments & stretch

- [ ] **Payment handles** — UPI VPA / PayPal / Venmo usernames are not stored yet; deep links open apps with amount only.
- [ ] **CSV import** — Creates viewer-only equal-split expenses (not auto-assigned to a group).

## Design / theme

- [ ] **TODO(design) — Semantic balance colors** — Confirm "you owe" / "you're owed" / pending colors before shipping (`Color.kt`, `Theme.kt`, `SplitEaseColors.kt`).
- [ ] **Light text-secondary token** — Interim `#5C5878` for `onSurfaceVariant` / `NavyMuted`; confirm or add a brand muted token.
- [ ] **Hardcoded colors outside theme** — e.g. invite chip in `GroupSettingsScreen.kt`, pastel avatars in `LedgerEntryUi.kt`.
- [ ] **Migrate `Se*` / screens to `MaterialTheme.colorScheme`** — Many components still use light `SplitEaseColors` aliases under dark theme.
- [ ] **Dynamic color settings opt-in** — `dynamicColor = true` available for a future Settings toggle; default stays brand-fixed.

## i18n & polish

- [ ] **TODO(i18n-last)** — Restore and expand full translations; locale overlays (`values-de/es/fr/hi/it/ja/pt`) currently fall back to English.
- [ ] **ViewModel string Context extraction** — Groups / Account / Import / Activity partially outstanding.
- [ ] **detekt** — Optional static analysis beyond ktlint (deferred from Phase 0).

## Store / release

- [ ] **Feature graphic** — 1024×500 (`docs/store-listing.md`).
- [ ] **Phone screenshots** — ≥2 in `docs/screenshots/`.
- [ ] **Privacy policy URL** — Required for Play submission.
- [ ] **Support contact** — Email / website TBD before Play submission.
- [ ] Release checklist items in [docs/release-checklist.md](docs/release-checklist.md).

## Ops / SQL (existing projects)

- [ ] **Apply SQL on fresh DB** — Use [docs/sql/migration_db.sql](docs/sql/migration_db.sql) for full setup in one run.
- [ ] **Group share invite burn** — Apply [docs/sql/phase-3f-fix-group-share-invite-burn.sql](docs/sql/phase-3f-fix-group-share-invite-burn.sql) if not yet run.
- [ ] **Expense SELECT RLS** — Apply [docs/sql/phase-4c-fix-expense-select-rls-returning.sql](docs/sql/phase-4c-fix-expense-select-rls-returning.sql) if not yet run (already on primary project).
- [ ] **Signup phone duplicate RPC** — Apply [docs/sql/phase-auth-phone-registered.sql](docs/sql/phase-auth-phone-registered.sql) if not yet run.
- [ ] **Render Free SMTP** — Outbound SMTP blocked on Free; use Resend HTTPS + verified domain, paid host, or local SMTP. See [docs/phase-10d-onboarding-mail.md](docs/phase-10d-onboarding-mail.md).
- [ ] **SplitEase Server** — Lives at `C:\splitease\server`; prefer Nodemailer SMTP locally. See [docs/splitease-server-repo.md](docs/splitease-server-repo.md).

## In-code markers

| File | Marker |
|---|---|
| `presentation/theme/Color.kt` | `TODO(design)` — semantic balance colors |
| `presentation/theme/Theme.kt` | `TODO(design)` — error* placeholders for "you owe" |
| `presentation/theme/SplitEaseColors.kt` | `TODO(design)` — semantic balance colors |
