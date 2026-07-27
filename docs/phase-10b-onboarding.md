# Phase 10b — Post-Signup Onboarding

## Phase Goal

Add a signed-in onboarding setup flow that gates new users after signup/OTP verification and before the main app tabs, letting them confirm their display name, choose a default currency, and get oriented before landing on the home screen.

## Scope (In / Out)

**In**
- Onboarding completion flag in local app settings (SharedPreferences)
- Post-signup setup flow: display name confirmation → default currency selection → finish
- `AuthRepository.updateDisplayName(name)` to update Supabase metadata + Room + profiles
- Root navigation gate in `SplitEaseNavHost` (after OTP, before `SignedInNavHost`)
- Existing users default to onboarding-complete (no forced migration)

**Out**
- Invite-aware onboarding (deferred)
- Cloud-synced onboarding-complete flag (local-only for now; per-device)
- Profile photo upload
- Phone number onboarding (existing TODO)

## Architecture Decisions

| Decision | Rationale |
|---|---|
| SharedPreferences `onboarding_complete` flag | Reuses existing settings layer; avoids Room migration or remote schema change |
| Gate at root nav host level | Same pattern as OTP gate; prevents tab flash on cold start |
| Existing users default to complete | Avoids forcing long-time users through setup |
| `updateDisplayName` updates metadata + Room + profiles | Keeps all three in sync like `persistCurrentUser` does today |
| Reuse `AppSettingsRepository.setCurrencyCode` | No new currency storage needed |

## Data Model Changes

No Room schema changes. New SharedPreferences key:

| Key | Type | Default | Description |
|---|---|---|---|
| `onboarding_complete` | Boolean | `false` | Set `true` when user finishes setup |

## Files Added/Modified

| File path | Purpose |
|---|---|
| `docs/phase-10b-onboarding.md` | This doc |
| `domain/settings/AppSettingsRepository.kt` | Add onboarding-complete observe/get/set |
| `data/settings/SharedPreferencesAppSettingsRepository.kt` | Implement onboarding flag |
| `domain/repository/AuthRepository.kt` | Add `updateDisplayName` |
| `data/repository/SupabaseAuthRepository.kt` | Implement display name update |
| `presentation/onboarding/OnboardingViewModel.kt` | Setup flow state + save logic |
| `presentation/onboarding/OnboardingScreen.kt` | Compose UI for the setup wizard |
| `presentation/navigation/SplitEaseNavHost.kt` | Onboarding gate before `SignedInNavHost` |
| `res/values/strings.xml` | Onboarding string resources |
| `PROGRESS.md` | Track phase |
| `CHANGELOG.md` | Log changes |

## Screens/UI Added

| Screen | Description |
|---|---|
| OnboardingScreen | Multi-step setup: name → currency → finish; uses Se* components |

## How to Test

### Manual
1. Fresh install → sign up → verify OTP → onboarding appears (not tabs).
2. Confirm/edit display name → Next → pick currency → Finish → land on Groups tab.
3. Kill and relaunch → skip onboarding, go straight to tabs.
4. Existing signed-in user → no onboarding shown.
5. Name persists on Account tab; currency persists in Settings.

### Automated
```bash
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat ktlintCheck
```

## Known Issues / TODOs

- Onboarding-complete is per-device (not cloud-synced); a reinstall or new device re-triggers.
- Profile photo not included in onboarding (future enhancement).

## Screenshots placeholder

![phase-10b-screenshot](./screenshots/phase-10b.png)

---

## Plan

1. Add onboarding-complete preference to app settings layer.
2. Add `AuthRepository.updateDisplayName` and Supabase implementation.
3. Build onboarding screen and ViewModel.
4. Insert onboarding gate in root nav host.
5. Add strings, update docs.

---

## Outcome

**Status:** Done (2026-07-27)

Phase 10b delivered a post-signup onboarding gate: after signup + OTP verification, new users see a two-step setup wizard (confirm display name → choose default currency) before entering the main app. Existing installs default to onboarding-complete and skip the flow.

Key additions:
- `AppSettingsRepository.observe/get/setOnboardingComplete` (SharedPreferences, default `true`)
- `AuthRepository.updateDisplayName` → Supabase user metadata + Room + profiles
- `OnboardingViewModel` + `OnboardingScreen` (name + currency steps, `Se*` components)
- Root nav gate in `SplitEaseNavHost` after OTP handling, before `SignedInNavHost`
- Signup path sets `onboarding_complete = false` so new users are routed through setup
- 6 unit tests (OnboardingViewModelTest) + existing AuthViewModelTest updated

Build, tests, and ktlint green.

### Follow-up (2026-07-27)

Removed the name confirmation UI. Signup already collects display name; after OTP users enter `SignedInNavHost` directly. Welcome email still fires once via `OnboardingViewModel.onSignedInWelcome`.
