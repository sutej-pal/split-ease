# Phase 9 — Polish, Testing, and Release Prep

Release hardening: locale string packs, real Room migrations, email confirmation UX, regression smoke tests, and Play Store / checklist prep. Core product features stop here; later phases are post-MVP extras.

## Phase Goal

Ship release-hardening: 7+ languages, real Room migrations (no destructive wipe on upgrade), production-ready email confirmation UX, regression smoke tests, and Play Store / release checklist prep.

## Scope

### In
- `values-xx` string packs for **7+ locales** (plus default English) and an in-app language setting
- Extract remaining high-traffic hardcoded ViewModel/UI messages into string resources
- Real Room migrations `1→2→3→4`; remove production destructive fallback
- Email confirmation flow: pending-verify screen, resend, deep-link callback handling; docs for enabling Confirm email in Supabase
- Instrumentation / Compose smoke regression + release checklist + store listing copy draft
- ProGuard stubs expanded; version **1.0.0**

### Out
- New core product features (payments handles storage, live FX, invite Edge Functions)
- Full Open Banking / Google OAuth completion
- Signed Play upload keystore (document in checklist; secrets stay local)

## Architecture Decisions

| Decision                                                              | Rationale                                                              |
| --------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `AppCompatDelegate.setApplicationLocales`                             | Per-app language without process restart hacks; persists via AppCompat |
| Sign-up returns `SignUpResult` (SignedIn vs PendingEmailConfirmation) | Works with Confirm email ON or OFF                                     |
| Manual `Migration` objects from exported schemas                      | Schemas 1–4 already exported; no AutoMigration annotation churn        |
| Keep destructive fallback **off** in release DB builder               | Data loss on upgrade is unacceptable for 1.0                           |
| Compose UI smoke on Welcome                                           | Proves instrumentation + Compose test deps without brittle nav graphs  |

## Plan

1. Document this plan; bump version to 1.0.0.
2. Add `SplitEaseMigrations` + wire `DatabaseModule`.
3. Auth: `SignUpResult`, pending-verify UI, resend, auth deep link.
4. i18n strings + language preference in Settings.
5. Release docs, ProGuard, Compose smoke test.
6. Build/tests; write Outcome.

## Data Model Changes

- No Room version bump (still v4); migrations added for historical upgrades only.
- `AuthUser` gains `emailConfirmed: Boolean`.
- Settings prefs: `app_locale` (BCP-47 tag or system).

---

## Outcome

**Status:** Done (2026-07-23)

Phase 8 was already complete (payments / CSV / Vico). Phase 9 delivered i18n (8 locales + language setting), Room migrations 1→4, email confirmation UX + `splitease://auth-callback` deep links, release/store docs, Compose + migration instrumented tests, and version **1.0.0**.

### Files Added/Modified

| Path                                                 | Purpose                                       |
| ---------------------------------------------------- | --------------------------------------------- |
| `data/local/db/SplitEaseMigrations.kt`               | Migrations 1→2→3→4                            |
| `data/di/DatabaseModule.kt`                          | `addMigrations`; removed destructive fallback |
| `domain/model/SignUpResult.kt`                       | Signup outcome sealed type                    |
| `domain/settings/AppLocale.kt`                       | Language preference enum                      |
| `presentation/auth/*`                                | Verify-email UI + ViewModel/repo changes      |
| `presentation/settings/*`                            | Language settings screen                      |
| `MainActivity` / `AndroidManifest`                   | Auth deep-link handling                       |
| `res/values-*/strings.xml`                           | es, fr, de, pt, hi, ja, it                    |
| `docs/release-checklist.md`, `docs/store-listing.md` | Release prep                                  |
| `androidTest/.../WelcomeScreenComposeTest.kt`        | Compose smoke                                 |
| `androidTest/.../SplitEaseMigrationsTest.kt`         | Migration validation                          |

### Screens/UI Added

- Verify email (post-signup when confirmation required)
- Settings → Language

### How to Test

1. Settings → Language → switch to Español/हिन्दी → Welcome/nav labels update.
2. Enable Confirm email in Supabase; sign up → verify screen → open mail link (`splitease://auth-callback`) → session signs in.
3. Resend confirmation from verify screen.
4. Install over an older DB (or run `SplitEaseMigrationsTest`) — data preserved.
5. `./gradlew testDebugUnitTest assembleDebug`
6. Optional: `connectedDebugAndroidTest` for Compose + migration tests.

### Known Issues / TODOs

- Not every string is deeply localized yet (many locale files still share English body copy with translated chrome).
- Some ViewModel messages still need Context extraction (Groups/Account/Import/Activity partially outstanding).
- Play feature graphic / screenshots / privacy policy URL still TODO (`docs/store-listing.md`).
- Invite email Edge Function + payment handles remain deferred (carried from earlier phases).

### Screenshots placeholder

![phase-9-screenshot](./screenshots/phase-9.png)
