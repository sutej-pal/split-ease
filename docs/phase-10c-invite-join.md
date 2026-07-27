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

Supabase (see [sql/phase-3c-invite-join.sql](sql/phase-3c-invite-join.sql)):
- `get_invite_preview(p_token text) → jsonb`
- `accept_invite_by_token(p_token text) → integer`

## Files Added/Modified

| File path | Purpose |
|---|---|
| `docs/sql/phase-3c-invite-join.sql` | Preview + accept-by-token RPCs |
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

1. Apply [sql/phase-3c-invite-join.sql](sql/phase-3c-invite-join.sql) in Supabase SQL Editor (after phase-3b).
2. Confirm email ON + Confirm signup template includes `{{ .Token }}`.
3. From a signed-in account, invite a non-user to a group and share the link.
4. Open `https://splitease.app/invite/{token}` or `splitease://invite/{token}` on a device with the app installed → Invite landing.
5. Tap **Join as someone new** → fill signup → OTP screen appears; Home/onboarding must not show until Verify succeeds.
6. After OTP, user should be a group member and friend row remapped.

## Known Issues / TODOs

- **App Links verification** — without `assetlinks.json` on `splitease.app`, Android may show an open-with chooser for https links; custom scheme works immediately.
- **Select your name / claim placeholder** — landing lists members for context only; joining always creates a new account.
- **Invite email delivery** — still share-sheet MVP.

## Screenshots

_Placeholder — add device captures of invite landing + join signup when available._

## Outcome

Phase 10c delivered the invite deep-link join path:

- Opening `https://splitease.app/invite/{token}` or `splitease://invite/{token}` stores the token and shows the invite landing screen (inviter, group, members, **Join as someone new**).
- Join signup creates the account, then the root OTP gate blocks Home/onboarding until the 6-digit code is verified (also enforced for normal signup).
- After verify, sync calls `accept_invite_by_token` (plus email-based accept) so the new user joins the group.
- Screens use SplitEase `Se*` theme (not a third-party skin). Claiming an existing placeholder from the member list remains out of scope.

### Delivered checklist
- Intent-filters + `MainActivity` invite token intake
- `InviteLandingScreen` + `InviteJoinSignUpScreen`
- Always-on OTP gate after signup
- SQL RPCs in `docs/sql/phase-3c-invite-join.sql`
- Docs: PROGRESS / CHANGELOG / ARCHITECTURE / data-dictionary / README / phase-bundles

## Screenshots

_Placeholder — add device captures of invite landing + join signup when available._
