# SplitEase docs index (essential-first)

Use this order to stay fast and consistent.

## 1) Required daily docs

| Doc                                      | Why it matters                                       |
| ---------------------------------------- | ---------------------------------------------------- |
| [product-manual.md](product-manual.md)   | Full product + technical manual (paste into Google Docs) |
| [../PROGRESS.md](../PROGRESS.md)         | Single source for current phase/state                |
| [../ARCHITECTURE.md](../ARCHITECTURE.md) | Current implementation truth (layers + sync + theme) |
| [data-dictionary.md](data-dictionary.md) | Data/schema truth (Room + remote)                    |
| [phase-bundles.md](phase-bundles.md)     | Condensed phase history by theme                     |

## 2) Task-specific docs

- Product mapping: [ROADMAP.md](ROADMAP.md)
- OTP operations (signup + recovery): [maintenance-email-otp-verification.md](maintenance-email-otp-verification.md)
- Forgot-password OTP: [phase-12-forgot-password-email-otp.md](phase-12-forgot-password-email-otp.md)
- Invite / onboarding / expense details: [phase-10-expense-details-onboarding-invite-mail.md](phase-10-expense-details-onboarding-invite-mail.md)
- App Links: [app-links-setup.md](app-links-setup.md)
- Release: [release-checklist.md](release-checklist.md), [store-listing.md](store-listing.md), [../RELEASES.md](../RELEASES.md)
- Extras backlog: [extras-group-live-updates-notifications.md](extras-group-live-updates-notifications.md)
- Supabase architecture TODOs (ordered): [supabase-architecture-todos.md](supabase-architecture-todos.md)
- FCM / Realtime ops: [fcm-setup.md](fcm-setup.md)
- Design tokens: [design-tokens.md](design-tokens.md)
- Mail server repo notes: [splitease-server-repo.md](splitease-server-repo.md)

## 3) Phase docs

One doc per phase (no a/b/c/d splits). Prefer:
- `ARCHITECTURE.md` for current behavior
- `phase-bundles.md` for condensed history
- `phase-N-*.md` only when you need phase-level detail

| Phase | Doc                                                                                                      |
| ----- | -------------------------------------------------------------------------------------------------------- |
| 0     | [phase-0-project-setup-and-brand-theme.md](phase-0-project-setup-and-brand-theme.md)                     |
| 1     | [phase-1-room-data-layer-and-domain.md](phase-1-room-data-layer-and-domain.md)                           |
| 2     | [phase-2-supabase-auth-and-session.md](phase-2-supabase-auth-and-session.md)                             |
| 3     | [phase-3-friends-groups-and-invites.md](phase-3-friends-groups-and-invites.md)                           |
| 4     | [phase-4-expense-creation-and-splits.md](phase-4-expense-creation-and-splits.md)                         |
| 5     | [phase-5-balances-and-debt-simplification.md](phase-5-balances-and-debt-simplification.md)               |
| 6     | [phase-6-settlements-and-recurring-expenses.md](phase-6-settlements-and-recurring-expenses.md)           |
| 7     | [phase-7-search-categories-currency-offline-sync.md](phase-7-search-categories-currency-offline-sync.md) |
| 8     | [phase-8-payments-csv-import-and-charts.md](phase-8-payments-csv-import-and-charts.md)                   |
| 9     | [phase-9-i18n-migrations-and-release-prep.md](phase-9-i18n-migrations-and-release-prep.md)               |
| 10    | [phase-10-expense-details-onboarding-invite-mail.md](phase-10-expense-details-onboarding-invite-mail.md) |
| 11    | [phase-11-group-pin-board.md](phase-11-group-pin-board.md)                                               |
| 12    | [phase-12-forgot-password-email-otp.md](phase-12-forgot-password-email-otp.md)                           |

## SQL (apply in Supabase)

1. Fresh DB (or re-apply safely): [sql/migration_db.sql](sql/migration_db.sql) — single canonical file (schema, RLS, invites, expenses, payments, realtime, device tokens, pin boards, auth RPCs, share-link heal, optional FCM notify triggers).
2. FCM Edge Function / webhooks ops: [fcm-setup.md](fcm-setup.md)

### Clipboard helper

```powershell
.\scripts\build-supabase-bootstrap-sql.ps1 -CopyToClipboard
```
