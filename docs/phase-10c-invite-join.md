# Phase 10c — Invite Deep Link Join Flow

## Phase Goal

When someone opens a SplitEase invite link, open the app to an invite landing screen, let them join as a new user via a dedicated signup screen, gate on email OTP until verified, then claim the invite and add them to the group (or friend connection).

## Scope (In / Out)

**In**
- App Links / custom-scheme handling for `https://splitease.app/invite/{token}` and `splitease://invite/{token}`
- Invite landing screen (inviter + group context + member list + “Join as someone new”)
- Invite-aware join signup screen (name / email / password)
- Persist pending invite token across signup → OTP → signed-in
- Always show OTP gate after signup; do not enter the app until verified
- Supabase RPCs: public invite preview (anon) + accept-by-token (authenticated)
- After OTP, accept invite by token and sync social graph

**Out**
- Claiming an existing placeholder member row from the landing list (“Select your name”)
- Hosted web landing page / Digital Asset Links verification for auto-open without chooser
- Automated invite email Edge Function (still share-sheet MVP)
- Google OAuth on the join signup screen

## Architecture Decisions

| Decision | Rationale |
|---|---|
| Gate OTP at root for every successful signup | Matches product rule: never proceed until email OTP is verified |
| Store pending invite token in app settings | Survives process death during OTP entry |
| `get_invite_preview(token)` SECURITY DEFINER for `anon` | Landing UI must load before auth; token is the capability |
| `accept_invite_by_token(token)` in addition to email-based accept | “Join as someone new” may use a different email than the original invite row |
| Theme-aligned `Se*` screens (not Splitwise orange/green skins) | Workspace UI theme agreement |
| Custom scheme + https intent-filters | Opens app from share links even before App Links verification |

## Data Model Changes

No Room schema changes. New SharedPreferences key:

| Key | Type | Default | Description |
|---|---|---|---|
| `pending_invite_token` | String? | null | Invite token awaiting accept after signup/OTP |

Supabase (see [sql/migration_db.sql](sql/migration_db.sql)):
- `get_invite_preview(p_token text) → jsonb`
- `accept_invite_by_token(p_token text) → integer`

## Files Added/Modified

| File path | Purpose |
|---|---|
| `docs/sql/migration_db.sql` | Preview + accept-by-token RPCs (among other schema) |
| `domain/model/InvitePreview.kt` | Preview DTO for landing UI |
| `domain/settings/AppSettingsRepository.kt` | Pending invite token API |
| `data/settings/SharedPreferencesAppSettingsRepository.kt` | Persist token |
| `data/remote/dto/InvitePreviewDto.kt` | PostgREST decode |
| `data/remote/SocialRemoteDataSource.kt` | Preview + accept-by-token calls |
| `data/social/SocialInteractor.kt` | Orchestrate preview / accept |
| `data/social/InviteLinks.kt` | Custom-scheme helper |
| `presentation/invite/*` | Landing + join signup + ViewModel |
| `presentation/auth/AuthViewModel.kt` | Always OTP-gate after signup |
| `presentation/navigation/SplitEaseNavHost.kt` | Signed-out invite routes |
| `MainActivity.kt` / `AndroidManifest.xml` | Deep link intake |
| `res/values/strings.xml` | Invite join copy |

## Screens/UI Added

| Screen | Description |
|---|---|
| InviteLanding | Brand header, invite copy, member list, “Join as someone new” |
| InviteJoinSignUp | “JOIN {group}” signup form → triggers signup → OTP gate |

## How to Test

1. Apply [sql/migration_db.sql](sql/migration_db.sql) in Supabase SQL Editor (fresh DB), or ensure invite RPCs are present.
2. Confirm email ON + Confirm signup template includes `{{ .Token }}`.
3. From a signed-in account, invite a non-user to a group and share the link.
4. Open the **https** invite link in a browser (Chrome) on a device with the app installed:
   `https://<mail-service>/invite/{token}` — the page redirects into SplitEase → Invite landing.
   (Custom `splitease://` cannot be pasted into the browser address bar.)
5. Tap **Join as someone new** → fill signup → OTP screen appears; Home/onboarding must not show until Verify succeeds.
6. After OTP, user should land on the invited **group detail** screen (membership already claimed).
7. Friend-only invites should open the Friends tab after OTP.
8. Already signed in: opening the https link claims the invite and opens the group.

## Known Issues / TODOs

- **Redeploy mail-service `/invite/:token`** — page now tries open-app then Play Store with `referrer=invite_token%3D…`; redeploy Render after pulling mail-service changes.
- **Install Referrer E2E** — full deferred-invite proof needs a Play install (Internal testing is enough). Sideload / Studio Run does not populate referrer; parser unit tests cover the string format.
- **App Links verification** — Settings shows **0 verified links** until each https host serves [assetlinks.json](../assetlinks.json) at `/.well-known/assetlinks.json` ([app-links-setup.md](../app-links-setup.md)). Custom scheme `splitease://invite/{token}` works without verification. Without assetlinks, Chrome may load the https bridge then `intent://` (or Play fallback).
- **Select your name / claim placeholder** — landing lists members for context only; joining always creates a new account.
- **Invite email delivery** — still share-sheet MVP.

## Screenshots

_Placeholder — add device captures of invite landing + join signup when available._

## Outcome

Phase 10c delivered the invite deep-link join path:

- Opening `https://splitease.app/invite/{token}` or `splitease://invite/{token}` stores the token and shows the invite landing screen (inviter, group, members, **Join as someone new**).
- Join signup creates the account, then the root OTP gate blocks Home/onboarding until the 6-digit code is verified (also enforced for normal signup).
- After verify, sync calls `accept_invite_by_token` (plus email-based accept) so the new user joins the group.
- Share text includes the custom-scheme link so an installed app opens without relying on App Links verification.
- After accept, the app navigates to the invited **group detail** (or Friends tab for friend-only invites).
- Screens use SplitEase `Se*` theme (not a third-party skin). Claiming an existing placeholder from the member list remains out of scope.

### Follow-up fix (signed-in deep link)
Opening `splitease://invite/{token}` while already signed in previously stored the token but did not re-run accept (claim was keyed only on `userId`). Signed-in navigation now reacts to a new pending invite token, and the token is cleared only after accept succeeds.

### Follow-up: deferred invite via Play Install Referrer

When the app is **not** installed, the mail-service invite page falls back to Google Play with `referrer=invite_token%3D{token}`. On first launch, `InstallReferrerInviteBootstrap` reads that referrer once into `pending_invite_token` — the same path as a live deep link (signup / OTP / accept).

### Follow-up fix (group share-link burn)

Generic **Invite via link** rows used the inviter's email as a placeholder. On the inviter's next sync, `accept_pending_invites()` matched that email and marked the share link `ACCEPTED` before any invitee opened it — deep link opened the app with nothing to claim. Fixed in [sql/phase-3f-fix-group-share-invite-burn.sql](sql/phase-3f-fix-group-share-invite-burn.sql): placeholder email, email-accept skips `friend_row_id is null`, token-accept keeps share links multi-use and ignores inviter self-claim.


### Delivered checklist
- Intent-filters + `MainActivity` invite token intake
- `InviteLandingScreen` + `InviteJoinSignUpScreen`
- Always-on OTP gate after signup
- SQL RPCs in `docs/sql/migration_db.sql`
- Docs: PROGRESS / CHANGELOG / ARCHITECTURE / data-dictionary / README / phase-bundles
- Signed-in deep-link claim + retry-safe token clear
- Mail-service Play Store referrer fallback + Android Install Referrer bootstrap

## Screenshots

_Placeholder — add device captures of invite landing + join signup when available._
