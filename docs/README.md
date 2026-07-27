# SplitEase docs index (essential-first)

Use this order to stay fast and consistent.

## 1) Required daily docs

| Doc | Why it matters |
|---|---|
| [../PROGRESS.md](../PROGRESS.md) | Single source for current phase/state |
| [../ARCHITECTURE.md](../ARCHITECTURE.md) | Current implementation truth (layers + sync + theme) |
| [data-dictionary.md](data-dictionary.md) | Data/schema truth (Room + remote) |
| [phase-bundles.md](phase-bundles.md) | Merged summaries of similar phases |

## 2) Task-specific docs

- Product mapping: [ROADMAP.md](ROADMAP.md)
- OTP operations + signup verification runbook: [maintenance-email-otp-verification.md](maintenance-email-otp-verification.md)
- Invite deep-link join: [phase-10c-invite-join.md](phase-10c-invite-join.md)
- Release: [release-checklist.md](release-checklist.md), [store-listing.md](store-listing.md)
- Extras backlog: [extras-group-live-updates-notifications.md](extras-group-live-updates-notifications.md)
- Design tokens: [design-tokens.md](design-tokens.md)

## 3) Historical phase docs (preserved)

Historical docs remain for auditability and phase traceability. New work should prefer:
- `ARCHITECTURE.md` for current behavior
- `phase-bundles.md` for condensed phase history
- specific phase docs only when deep context is required

## SQL (apply in Supabase in order)

1. [sql/phase-3-schema.sql](sql/phase-3-schema.sql)
2. [sql/phase-3b-invites.sql](sql/phase-3b-invites.sql)
3. [sql/phase-3c-invite-join.sql](sql/phase-3c-invite-join.sql)
4. [sql/phase-4-expenses.sql](sql/phase-4-expenses.sql)
5. [sql/phase-6-payments.sql](sql/phase-6-payments.sql)
