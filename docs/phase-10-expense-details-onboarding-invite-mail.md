# Phase 10 — Post-MVP Product Hardening

Post-MVP hardening in one doc: expense detail (edit/delete) + local Activity events, post-signup onboarding, invite deep-link join through OTP, and a best-effort welcome email via the mail service.

## Phase Goal

After core phases 0–9, harden day-to-day product flows: editable expense history, gated new-user setup, invite links that land in-app through OTP, and a best-effort welcome email via the Render mail service.

## Scope (In / Out)

**In**
- Expense detail (view / edit / delete) + local Activity events for create/update/delete
- Post-signup onboarding gate (currency / welcome side effects; name collected at signup)
- Invite App Links / custom scheme → landing → join signup → OTP → `accept_invite_by_token`
- Pending invite token across signup/OTP; signed-in deep-link claim
- Onboarding-start transactional email via mail-service `/send-mail` (once per user, non-blocking)

**Out**
- Cross-device Activity sync; soft-delete tombstones on Supabase
- Cloud-synced onboarding-complete flag
- “Select your name” placeholder claim on invite landing
- Full App Links verification ops (documented separately in [app-links-setup.md](app-links-setup.md))
- Rich multi-template mail system / delivery analytics

## Architecture Decisions

| Area | Decision | Rationale |
|---|---|---|
| Activity | Local `activity_events` Room table | Deletes stay visible after expense row is gone |
| Expense edit | Stable expense id; remap splits by participant | Avoids duplicate cloud rows |
| Onboarding | SharedPreferences `onboarding_complete` | No schema change; existing users default complete |
| Invite OTP | Always gate after signup | Never enter Home until email verified |
| Invite token | App settings `pending_invite_token` | Survives process death during OTP |
| Invite RPCs | `get_invite_preview` (anon) + `accept_invite_by_token` (auth) | Landing before auth; join email may differ from invite row |
| Welcome mail | `MailRepository` + per-user sent flag | Best-effort; failures must not block UX |

## Data Model Changes

**Room**
- `activity_events` (`id`, `kind`, `title`, `subtitle`, `amountLabel`, `actorUserId`, `relatedExpenseId`, `involvedUserIds`, `sortEpochMs`)

**SharedPreferences**
| Key | Description |
|---|---|
| `onboarding_complete` | Setup finished (existing installs default true) |
| `pending_invite_token` | Invite awaiting accept after signup/OTP |
| `onboarding_email_sent_{userId}` | Suppress duplicate welcome mails |

**Supabase** (see [sql/migration_db.sql](sql/migration_db.sql)):
- `get_invite_preview(p_token text) → jsonb`
- `accept_invite_by_token(p_token text) → integer`
- Share-link burn heal + multi-use token accept included in the same file

## Key deliverables

**Expense details & Activity**
- `ExpenseDetailScreen`; edit via prefilled add-expense form; delete confirm + remote best-effort delete
- Routes: `expense_detail/{id}`; ledger / Activity taps open detail
- Activity shows EXPENSE_ADDED / UPDATED / DELETED (legacy expense rows until first event exists)

**Onboarding**
- Root nav gate after OTP, before `SignedInNavHost`
- `AuthRepository.updateDisplayName`; currency via `AppSettingsRepository`
- Name-confirmation step later removed — signup name is enough; welcome email still fires once

**Invite join**
- Intent-filters + `MainActivity` token intake; `InviteLandingScreen` + `InviteJoinSignUpScreen`
- Play Install Referrer deferred-invite path when app was not installed
- After accept → invited group detail (or Friends for friend-only invites)

**Onboarding mail**
- `MAIL_SERVICE_BASE_URL` / `MAIL_SERVICE_API_KEY` BuildConfig
- Trigger on first signed-in session; Render Free blocks SMTP — prefer Resend HTTPS or paid/local SMTP ([splitease-server-repo.md](splitease-server-repo.md))

## Screens/UI

| Screen | Role |
|---|---|
| Expense detail | View / edit / delete |
| Onboarding (currency / welcome) | Post-OTP gate when incomplete |
| Invite landing | Inviter, group, members, join CTA |
| Invite join signup | Creates account → OTP gate |

## How to Test

1. Create → edit → delete expense; Activity shows added / updated / deleted; tap opens detail when row exists.
2. Fresh signup → OTP → onboarding/currency path as configured; relaunch skips completed onboarding.
3. Apply invite SQL; open `https://…/invite/{token}` or `splitease://invite/{token}` → landing → join → OTP → group membership.
4. Signed-in deep link claims invite; share-link does not burn on inviter sync (SQL fix applied).
5. Configure mail-service; confirm one welcome email per user, no block on mail failure.

## Known Issues / TODOs

- Activity events do not sync across devices; remote delete offline may leave cloud rows until extras sync work
- Onboarding-complete is per-device
- App Links need hosted [assetlinks.json](assetlinks.json) ([app-links-setup.md](app-links-setup.md))
- Invite email still share-sheet MVP; Install Referrer E2E needs a Play install
- Render Free SMTP blocked — use Resend or non-Free hosting for production mail

## Outcome

**Status:** Done (expense details → onboarding → invite join → welcome mail through 2026-07)

Phase 10 delivered editable expenses with local Activity history, post-auth onboarding/welcome mail, and a full invite deep-link join path through OTP and token accept.

**Next historically:** Phase 11 — Group Pin Board.
