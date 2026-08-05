# SplitEase Phase Bundles (Essentials)

Compact references for day-to-day work. Full phase docs remain under `docs/phase-*.md`.

## Bundle A — Foundations (`0`, `1`)

- Project scaffold, Compose/Hilt/Room setup, brand theme tokens, `Se*` UI blocks.
- Offline-first Room entities, repositories, sync markers, `BigDecimal` money.

Use when changing platform foundations, theming, DI, entities, or repositories.  
Docs: [phase-0-project-setup-and-brand-theme.md](phase-0-project-setup-and-brand-theme.md), [phase-1-room-data-layer-and-domain.md](phase-1-room-data-layer-and-domain.md), [design-tokens.md](design-tokens.md)

## Bundle B — Identity & Social Graph (`2`, `3`, `10` invite/onboarding)

- Supabase auth/session gating (signup, login, reset, sign-out).
- Email OTP for signup and recovery; post-signup onboarding / welcome mail.
- Friends/groups + invite deep-link join (`accept_invite_by_token`).

Use when changing auth, onboarding, friends, groups, or invite entry.  
Docs: [phase-2-supabase-auth-and-session.md](phase-2-supabase-auth-and-session.md), [phase-3-friends-groups-and-invites.md](phase-3-friends-groups-and-invites.md), [phase-10-expense-details-onboarding-invite-mail.md](phase-10-expense-details-onboarding-invite-mail.md), [maintenance-email-otp-verification.md](maintenance-email-otp-verification.md)

## Bundle C — Expense Lifecycle (`4`, `5`, `6`, `10` expense details)

- Expense create/edit/delete with split types and Activity events.
- Derived balances, debt simplification, settlements, recurring generation.

Use when changing ledger, split math, balances, settlements, or expense detail.  
Docs: [phase-4-expense-creation-and-splits.md](phase-4-expense-creation-and-splits.md), [phase-5-balances-and-debt-simplification.md](phase-5-balances-and-debt-simplification.md), [phase-6-settlements-and-recurring-expenses.md](phase-6-settlements-and-recurring-expenses.md), [phase-10-expense-details-onboarding-invite-mail.md](phase-10-expense-details-onboarding-invite-mail.md)

## Bundle D — Scale, Sync, Release (`7`, `8`, `9`) + later (`11`, `12`)

- Search, categories, currency catalog, durable sync.
- Stretch features (payment deep links, CSV import, charts).
- Release hardening, locales, pin board, forgot-password OTP.

Use when changing sync, import/pay, i18n, release quality, pin board, or recovery OTP.  
Docs: [phase-7-search-categories-currency-offline-sync.md](phase-7-search-categories-currency-offline-sync.md), [phase-8-payments-csv-import-and-charts.md](phase-8-payments-csv-import-and-charts.md), [phase-9-i18n-migrations-and-release-prep.md](phase-9-i18n-migrations-and-release-prep.md), [phase-11-group-pin-board.md](phase-11-group-pin-board.md), [phase-12-forgot-password-email-otp.md](phase-12-forgot-password-email-otp.md)

## Guardrails

- Keep [PROGRESS.md](../PROGRESS.md) as the status source of truth.
- Prefer [ARCHITECTURE.md](../ARCHITECTURE.md) for current behavior; phase docs for history.
- When shipping a new phase, add one short bullet here and a single `phase-N-*.md` (no a/b/c/d splits).
