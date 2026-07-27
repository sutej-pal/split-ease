# SplitEase Phase Bundles (Essentials)

This file merges related phase outcomes into compact references for day-to-day work.
Historical phase docs remain the source of record and are intentionally preserved.

## Bundle A — Foundations (`0`, `0b`, `1`)

- Project scaffold, Compose/Hilt/Room setup, and base architecture.
- Brand/theme token system and `Se*` UI building blocks.
- Offline-first Room entities, repositories, sync markers, and `BigDecimal` money handling.

Use when changing platform foundations, theming, DI, entities, or repositories.

## Bundle B — Identity & Social Graph (`2`, `3`, `10b`, `10c`, OTP maintenance)

- Supabase auth/session gating (signup, login, reset, sign-out).
- Email OTP verification path for signup (`verifySignupOtp` + resend); always gates after signup.
- Post-signup onboarding gate (display name + default currency setup).
- Friends/groups + invite primitives and profile lookup via `profiles`.
- Invite deep-link join: landing + join signup → OTP → `accept_invite_by_token`.

Use when changing auth, onboarding, friends, groups, or invite entry behavior.

## Bundle C — Expense Lifecycle (`4`, `5`, `6`, `10a`)

- Expense create/edit/delete with split types and activity events.
- Derived balances and debt simplification.
- Settlements/payments and recurring expense generation.

Use when changing any ledger, split math, balances, settlements, or expense detail behavior.

## Bundle D — Scale, Sync, Release (`7`, `8`, `9`)

- Search, categories, currency catalog, durable sync behavior.
- Stretch features (payment deep links, CSV import, charts).
- Release hardening, locales, migrations, and test/release checklist work.

Use when changing sync behavior, import/pay integrations, i18n, or release quality.

## Guardrails for future phases

- Keep `PROGRESS.md` phase order and statuses unchanged.
- Add new phase docs as usual; update this bundle file with one short bullet per shipped capability.
- Do not delete historical phase docs; bundle docs are summaries, not replacements.
