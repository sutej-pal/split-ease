# SplitEase Progress

Track development phases. Always check this file at the start of a session to determine the next incomplete phase.

**Feature map:** [docs/ROADMAP.md](docs/ROADMAP.md) — product features distributed across phases.

| Phase | Name                                             | Features covered                                                               | Status | Doc                                                                                                           |
| ----- | ------------------------------------------------ | ------------------------------------------------------------------------------ | ------ | ------------------------------------------------------------------------------------------------------------- |
| 0     | Project Setup, Foundations & Brand Theme         | Scaffold + Material 3 brand ColorScheme                                        | Done   | [phase-0-project-setup-and-brand-theme.md](docs/phase-0-project-setup-and-brand-theme.md)                     |
| 1     | Data Layer Foundations                           | Offline DB foundation                                                          | Done   | [phase-1-room-data-layer-and-domain.md](docs/phase-1-room-data-layer-and-domain.md)                           |
| 2     | Authentication (Supabase)                        | Cloud identity                                                                 | Done   | [phase-2-supabase-auth-and-session.md](docs/phase-2-supabase-auth-and-session.md)                             |
| 3     | Friends & Groups                                 | Add groups and friends                                                         | Done   | [phase-3-friends-groups-and-invites.md](docs/phase-3-friends-groups-and-invites.md)                           |
| 4     | Expense Creation & Splitting Logic               | Split expenses / record debts; equal & unequal; % & shares; unlimited expenses | Done   | [phase-4-expense-creation-and-splits.md](docs/phase-4-expense-creation-and-splits.md)                         |
| 5     | Balances & Debt Simplification                   | Calculate total balances; simplify debts                                       | Done   | [phase-5-balances-and-debt-simplification.md](docs/phase-5-balances-and-debt-simplification.md)               |
| 6     | Settlements & Recurring Expenses                 | Recurring expenses; mark settlements                                           | Done   | [phase-6-settlements-and-recurring-expenses.md](docs/phase-6-settlements-and-recurring-expenses.md)           |
| 7     | Search, Categories, Multi-Currency, Offline Sync | Offline mode; cloud sync; spending totals; categorize; 100+ currencies         | Done   | [phase-7-search-categories-currency-offline-sync.md](docs/phase-7-search-categories-currency-offline-sync.md) |
| 8     | Stretch / Pro-like Features                      | Payment integrations; transaction import; charts                               | Done   | [phase-8-payments-csv-import-and-charts.md](docs/phase-8-payments-csv-import-and-charts.md)                   |
| 9     | Polish, Testing, and Release Prep                | 7+ languages; release hardening                                                | Done   | [phase-9-i18n-migrations-and-release-prep.md](docs/phase-9-i18n-migrations-and-release-prep.md)               |
| 10    | Post-MVP Product Hardening                       | Expense details + Activity; onboarding; invite join; welcome mail              | Done   | [phase-10-expense-details-onboarding-invite-mail.md](docs/phase-10-expense-details-onboarding-invite-mail.md) |
| 11    | Group Pin Board                                  | Shared per-group notepad (plain text, auto-save)                               | Done   | [phase-11-group-pin-board.md](docs/phase-11-group-pin-board.md)                                               |
| 12    | Forgot Password OTP                              | Reset password via email OTP + set new password in-app                         | Done   | [phase-12-forgot-password-email-otp.md](docs/phase-12-forgot-password-email-otp.md)                           |

**Current phase:** Complete through Phase 12 (2026-08-04)

**Last completed:** Phase 12 — Forgot Password OTP (email recovery code + in-app new password)

### Docs map
- Index: [docs/README.md](docs/README.md)
- Living: [ARCHITECTURE.md](ARCHITECTURE.md), [CHANGELOG.md](CHANGELOG.md), [docs/data-dictionary.md](docs/data-dictionary.md)
- Condensed history: [docs/phase-bundles.md](docs/phase-bundles.md)
- Maintenance: [email OTP verification](docs/maintenance-email-otp-verification.md)
- Ordered Supabase follow-ups: [docs/supabase-architecture-todos.md](docs/supabase-architecture-todos.md)

### Carried-forward TODOs
- **Supabase architecture TODOs (ordered)** — ~~Remote delete tombstones~~ → ~~conflict policy~~ → ~~category sync (defaults)~~ → ~~pin-board boundary~~ → Edge Functions non-CRUD → ops hygiene ([supabase-architecture-todos.md](docs/supabase-architecture-todos.md)).
- **OTP ops** — App OTP flows shipped; live Confirm email / templates / SMTP stay on [release-checklist.md](docs/release-checklist.md) ([maintenance-email-otp-verification.md](docs/maintenance-email-otp-verification.md)).
- **Redeploy mail-service** — Recovery password-reset template lives in the mail-service; redeploy Vercel so reset mails are not the generic/signup copy ([phase-12](docs/phase-12-forgot-password-email-otp.md)).
- **TODO(auth-mobile-onboarding)** — Allow users to onboard with a mobile phone number (SMS OTP / phone auth) in addition to email.
- **Semantic balance colors** — confirm "you owe" / "you're owed" / pending before shipping ([phase-0](docs/phase-0-project-setup-and-brand-theme.md)).
- **Apply SQL on fresh DB** — use [migration_db.sql](docs/sql/migration_db.sql) for full setup in one run (includes share-link heal, expense RLS, phone RPC, reciprocal friends, optional FCM notify triggers).
- **Invite email delivery** — MVP uses the system share sheet; automated send via mail-service is still TODO.
- **SplitEase Server (separate repo)** — lives at `C:\splitease\server` beside the Android app at `C:\splitease\app` ([docs/splitease-server-repo.md](docs/splitease-server-repo.md)). Deployed on Vercel; uses Brevo HTTPS when `BREVO_API_KEY` is set, otherwise Nodemailer SMTP locally.
- **Mail provider** — Production uses Brevo HTTPS via SplitEase Server on Vercel; local dev can use Nodemailer SMTP ([phase-10](docs/phase-10-expense-details-onboarding-invite-mail.md)).
- **App Links / invite https** — share links use `MAIL_SERVICE_BASE_URL/invite/{token}` when set, else `splitease.app`. Host [docs/assetlinks.json](docs/assetlinks.json) for verified Open-by-default links ([app-links-setup.md](docs/app-links-setup.md)). Custom scheme `splitease://invite/{token}` works without verification.
- **Group live updates & push notifications (extra)** — Realtime + FCM path live: Edge Function deployed, `notification_prefs` applied, expenses/payments webhooks wired ([docs/fcm-setup.md](docs/fcm-setup.md), [docs/extras-group-live-updates-notifications.md](docs/extras-group-live-updates-notifications.md)).
- **Category sync** — stable default ids (`cat_*`) on the wire; Room v12 remaps legacy random defaults; custom categories remain device-local ([supabase-architecture-todos.md](docs/supabase-architecture-todos.md) #3).
- **TODO(mixed-currency-ux)** — Per-expense currency picker, group totals per currency, expand `AppCurrencies` (~20–30 codes). No FX conversion. See [TODO.md](TODO.md).
- **FX rates** — multi-currency remains per-bucket; live FX is still deferred.
- **Payment handles** — UPI VPA / PayPal / Venmo usernames are not stored yet; deep links open apps with amount only.
- **Social PENDING flush** — groups/members are now flushed in `SyncInteractor` before expenses (still verify Supabase SQL is applied).
- **Store assets** — feature graphic, phone screenshots, privacy policy URL (`docs/store-listing.md`).
- **TODO(i18n-last)** — Localization is deferred until the end of the product. Locale overlays (`values-de/es/fr/hi/it/ja/pt`) currently fall back to English; restore and expand full translations last.
