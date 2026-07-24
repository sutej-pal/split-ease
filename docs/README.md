# SplitEase docs index

Living docs stay at the repo root (`PROGRESS.md`, `ARCHITECTURE.md`, `CHANGELOG.md`). Phase write-ups and SQL live here.

## Start here

| Doc | When to read |
|---|---|
| [../PROGRESS.md](../PROGRESS.md) | Session start — what is done / open |
| [ROADMAP.md](ROADMAP.md) | Product features across phases |
| [../ARCHITECTURE.md](../ARCHITECTURE.md) | Layers, sync, theming (current) |
| [data-dictionary.md](data-dictionary.md) | Room / domain schema |
| [design-tokens.md](design-tokens.md) | Brand color tokens |

## Release

- [release-checklist.md](release-checklist.md)
- [store-listing.md](store-listing.md)

## Extras (post-roadmap)

- [extras-group-live-updates-notifications.md](extras-group-live-updates-notifications.md)

## Maintenance

- [maintenance-email-otp-verification.md](maintenance-email-otp-verification.md) — signup 6-digit OTP (replaces confirm link UX)

## Phase history (do not delete)

Each file has **Plan** (as written before implementation) and **Outcome** (what shipped). Prefer **Outcome** + `ARCHITECTURE.md` for current truth.

| Phase | Doc |
|---|---|
| 0 | [phase-0-setup.md](phase-0-setup.md) |
| 0b | [phase-0b-theme-system.md](phase-0b-theme-system.md) |
| 1 | [phase-1-data-layer.md](phase-1-data-layer.md) |
| 2 | [phase-2-authentication.md](phase-2-authentication.md) |
| 3 | [phase-3-friends-groups.md](phase-3-friends-groups.md) |
| 4 | [phase-4-expenses.md](phase-4-expenses.md) |
| 5 | [phase-5-balances.md](phase-5-balances.md) |
| 6 | [phase-6-settlements-recurring.md](phase-6-settlements-recurring.md) |
| 7 | [phase-7-search-categories-sync.md](phase-7-search-categories-sync.md) |
| 8 | [phase-8-stretch.md](phase-8-stretch.md) |
| 9 | [phase-9-polish-release.md](phase-9-polish-release.md) |
| 10a | [phase-10a-expense-details.md](phase-10a-expense-details.md) |

## SQL (apply in Supabase in order)

1. [sql/phase-3-schema.sql](sql/phase-3-schema.sql)
2. [sql/phase-3b-invites.sql](sql/phase-3b-invites.sql)
3. [sql/phase-4-expenses.sql](sql/phase-4-expenses.sql)
4. [sql/phase-6-payments.sql](sql/phase-6-payments.sql)
