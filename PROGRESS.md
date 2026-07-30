# SplitEase Progress

Track development phases. Always check this file at the start of a session to determine the next incomplete phase.

**Feature map:** [docs/ROADMAP.md](docs/ROADMAP.md) — product features distributed across phases.

| Phase | Name | Features covered | Status | Doc |
|---|---|---|---|---|
| 0 | Project Setup & Foundations | — | Done | [phase-0-setup.md](docs/phase-0-setup.md) |
| 0b | Brand Theme System | Light/dark Material 3 ColorScheme | Done | [phase-0b-theme-system.md](docs/phase-0b-theme-system.md) |
| 10a | Expense Details | Edit/delete expense + Activity events | Done | [phase-10a-expense-details.md](docs/phase-10a-expense-details.md) |
| 10b | Post-Signup Onboarding | Name + currency setup flow | Done | [phase-10b-onboarding.md](docs/phase-10b-onboarding.md) |
| 10c | Invite Deep Link Join | Invite landing + join signup + OTP gate | Done | [phase-10c-invite-join.md](docs/phase-10c-invite-join.md) |
| 10d | Onboarding Start Email | Send onboarding-start email via Render mail service | Done | [phase-10d-onboarding-mail.md](docs/phase-10d-onboarding-mail.md) |
| 11 | Group Pin Board | Shared per-group notepad (Markdown, online-only) | Done | [phase-11-pinboard.md](docs/phase-11-pinboard.md) |
| 1 | Data Layer Foundations | Offline DB foundation | Done | [phase-1-data-layer.md](docs/phase-1-data-layer.md) |
| 2 | Authentication (Supabase) | Cloud identity | Done | [phase-2-authentication.md](docs/phase-2-authentication.md) |
| 3 | Friends & Groups | Add groups and friends | Done | [phase-3-friends-groups.md](docs/phase-3-friends-groups.md) |
| 4 | Expense Creation & Splitting Logic | Split expenses / record debts; equal & unequal; % & shares; unlimited expenses | Done | [phase-4-expenses.md](docs/phase-4-expenses.md) |
| 5 | Balances & Debt Simplification | Calculate total balances; simplify debts | Done | [phase-5-balances.md](docs/phase-5-balances.md) |
| 6 | Settlements & Recurring Expenses | Recurring expenses; mark settlements | Done | [phase-6-settlements-recurring.md](docs/phase-6-settlements-recurring.md) |
| 7 | Search, Categories, Multi-Currency, Offline Sync | Offline mode; cloud sync; spending totals; categorize; 100+ currencies | Done | [phase-7-search-categories-sync.md](docs/phase-7-search-categories-sync.md) |
| 8 | Stretch / Pro-like Features | Payment integrations; transaction import; charts | Done | [phase-8-stretch.md](docs/phase-8-stretch.md) |
| 9 | Polish, Testing, and Release Prep | 7+ languages; release hardening | Done | [phase-9-polish-release.md](docs/phase-9-polish-release.md) |

**Current phase:** Complete through Phase 11 (2026-07-29)

**Last completed:** Phase 11 — Group Pin Board (shared per-group notepad)

### Docs map
- Index: [docs/README.md](docs/README.md)
- Living: [ARCHITECTURE.md](ARCHITECTURE.md), [CHANGELOG.md](CHANGELOG.md), [docs/data-dictionary.md](docs/data-dictionary.md)
- Maintenance: [email OTP verification](docs/maintenance-email-otp-verification.md)

### Carried-forward TODOs
- **OTP ops checklist** — Keep Supabase signup OTP operational (Confirm email ON, `{{ .Token }}` in Confirm signup template, SMTP/provider health) ([maintenance-email-otp-verification.md](docs/maintenance-email-otp-verification.md)).
- **TODO(auth-mobile-onboarding)** — Allow users to onboard with a mobile phone number (SMS OTP / phone auth) in addition to email.
- **Semantic balance colors** — confirm "you owe" / "you're owed" / pending before shipping ([phase-0b](docs/phase-0b-theme-system.md)).
- **Apply SQL on fresh DB** — use [migration_db.sql](docs/sql/migration_db.sql) for full setup in one run.
- **Invite email delivery** — MVP uses the system share sheet; automated send via mail-service is still TODO.
- **Group share invite burn (fixed 2026-07-29)** — Apply [sql/phase-3f-fix-group-share-invite-burn.sql](docs/sql/phase-3f-fix-group-share-invite-burn.sql) on existing Supabase projects if not yet run (reactivates burned share links + RPC fixes).
- **Render Free SMTP** — outbound SMTP ports are blocked on Free; onboarding mail needs `RESEND_API_KEY` (HTTPS) on the mail-service, or a paid Render instance for Gmail SMTP ([phase-10d](docs/phase-10d-onboarding-mail.md)).
- **App Links / invite https** — share links use `MAIL_SERVICE_BASE_URL/invite/{token}` when set, else `splitease.app`. Host [docs/assetlinks.json](docs/assetlinks.json) for verified Open-by-default links ([app-links-setup.md](docs/app-links-setup.md)). Custom scheme `splitease://invite/{token}` works without verification.
- **Group live updates & push notifications (extra)** — Realtime + FCM path implemented; finish Firebase/`google-services.json` + Edge Function deploy per [docs/fcm-setup.md](docs/fcm-setup.md). Mute prefs / delete tombstones still TODO ([docs/extras-group-live-updates-notifications.md](docs/extras-group-live-updates-notifications.md)).
- **Category sync** — default category ids are now stable on fresh installs; older devices still use local UUIDs (remote pull drops unknown `category_id`). Full category cloud sync still TODO.
- **FX rates** — multi-currency remains per-bucket; live FX is still deferred.
- **Payment handles** — UPI VPA / PayPal / Venmo usernames are not stored yet; deep links open apps with amount only.
- **Social PENDING flush** — groups/members are now flushed in `SyncInteractor` before expenses (still verify Supabase SQL is applied).
- **Expense SELECT RLS (fixed 2026-07-30)** — Apply [sql/phase-4c-fix-expense-select-rls-returning.sql](docs/sql/phase-4c-fix-expense-select-rls-returning.sql) on existing Supabase projects if not yet run (already applied on the primary project).
- **Store assets** — feature graphic, phone screenshots, privacy policy URL (`docs/store-listing.md`).
- **TODO(i18n-last)** — Localization is deferred until the end of the product. Locale overlays (`values-de/es/fr/hi/it/ja/pt`) currently fall back to English; restore and expand full translations last.
