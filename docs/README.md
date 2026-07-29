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
- FCM / Realtime ops: [fcm-setup.md](fcm-setup.md)
- Design tokens: [design-tokens.md](design-tokens.md)

## 3) Historical phase docs (preserved)

Historical docs remain for auditability and phase traceability. New work should prefer:
- `ARCHITECTURE.md` for current behavior
- `phase-bundles.md` for condensed phase history
- specific phase docs only when deep context is required

## SQL (apply in Supabase)

1. Fresh DB: [sql/migration_db.sql](sql/migration_db.sql) (single-run baseline — schema, RLS, invites, expenses, payments, realtime, device tokens, pin boards, auth email RPC)
2. Optional notify triggers / FCM: [sql/phase-extras-notify-triggers.sql](sql/phase-extras-notify-triggers.sql) + [fcm-setup.md](fcm-setup.md)

### Clipboard helper

Copy the canonical migration (optionally with notify triggers) to the clipboard:

```powershell
.\scripts\build-supabase-bootstrap-sql.ps1 -CopyToClipboard
```

```powershell
.\scripts\build-supabase-bootstrap-sql.ps1 -IncludeOptionalNotifyTriggers -CopyToClipboard
```
