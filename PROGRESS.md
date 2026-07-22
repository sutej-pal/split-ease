# SplitEase Progress

Track development phases. Always check this file at the start of a session to determine the next incomplete phase.

**Feature map:** [docs/ROADMAP.md](docs/ROADMAP.md) — product features distributed across phases.

| Phase | Name | Features covered | Status | Doc |
|---|---|---|---|---|
| 0 | Project Setup & Foundations | — | Done | [phase-0-setup.md](docs/phase-0-setup.md) |
| 1 | Data Layer Foundations | Offline DB foundation | Done | [phase-1-data-layer.md](docs/phase-1-data-layer.md) |
| 2 | Authentication (Supabase) | Cloud identity | Done | [phase-2-authentication.md](docs/phase-2-authentication.md) |
| 3 | Friends & Groups | Add groups and friends | Done | [phase-3-friends-groups.md](docs/phase-3-friends-groups.md) |
| 4 | Expense Creation & Splitting Logic | Split expenses / record debts; equal & unequal; % & shares; unlimited expenses | Done | [phase-4-expenses.md](docs/phase-4-expenses.md) |
| 5 | Balances & Debt Simplification | Calculate total balances; simplify debts | Done | [phase-5-balances.md](docs/phase-5-balances.md) |
| 6 | Settlements & Recurring Expenses | Recurring expenses; mark settlements | Done | [phase-6-settlements-recurring.md](docs/phase-6-settlements-recurring.md) |
| 7 | Search, Categories, Multi-Currency, Offline Sync | Offline mode; cloud sync; spending totals; categorize; 100+ currencies | Done | [phase-7-search-categories-sync.md](docs/phase-7-search-categories-sync.md) |
| 8 | Stretch / Pro-like Features | Payment integrations; transaction import; charts | Done | [phase-8-stretch.md](docs/phase-8-stretch.md) |
| 9 | Polish, Testing, and Release Prep | 7+ languages; release hardening | Not Started | — |

**Current phase:** 9 — Polish, Testing, and Release Prep (next)

**Last completed:** Phase 8 on 2026-07-22

### Carried-forward TODOs
- **Email confirmation skipped (MVP)** — keep Confirm email OFF in Supabase; re-enable + in-app verify flow before production (see [phase-2-authentication.md](docs/phase-2-authentication.md)).
- **Apply Phase 3–6 SQL** — run in order: [phase-3-schema.sql](docs/sql/phase-3-schema.sql), [phase-3b-invites.sql](docs/sql/phase-3b-invites.sql), [phase-4-expenses.sql](docs/sql/phase-4-expenses.sql), [phase-6-payments.sql](docs/sql/phase-6-payments.sql).
- **Invite email delivery** — MVP uses the system share sheet; automated send via Edge Function is still TODO.
- **Invite deep links** — `https://splitease.app/invite/{token}` is a placeholder URL until App Links / web landing exist.
- **FX rates** — multi-currency remains per-bucket; live FX is still deferred.
- **Payment handles** — UPI VPA / PayPal / Venmo usernames are not stored yet; deep links open apps with amount only.
- **Social PENDING flush** — still inline best-effort; not fully unified into SyncInteractor.
- **Real Room migrations** — still destructive fallback (Phase 9).
