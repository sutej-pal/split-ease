# Maintenance — Email signup OTP verification

## Phase Goal

Replace link-based (“click to confirm”) email signup verification with a **6-digit OTP** flow: after email/password signup, the user enters a numeric code from email in the app. No deep linking, custom URI schemes, or AndroidManifest intent-filter changes are required for this flow.

## Scope (In / Out)

**In**
- In-app OTP entry on the existing verify-email screen
- `AuthRepository.verifySignupOtp` via Supabase `verifyEmailOtp` (`OtpType.Email.SIGNUP`)
- Resend confirmation still uses `resendEmail(SIGNUP)` (delivers a new code when the template includes `{{ .Token }}`)
- Copy, ViewModel, unit tests, living docs / checklist updates

**Out**
- Removing `splitease://auth-callback` (still useful for password-reset / other Auth redirects)
- Password-reset OTP (reset remains link-based for now)
- Changing Supabase project templates in-repo (dashboard steps documented only)

## Architecture Decisions

| Decision | Rationale |
|---|---|
| OTP type `SIGNUP` (verify + resend) | Matches email/password signup confirmation; resend must use `SIGNUP` |
| Session after successful verify | Same hydrate path as sign-in (`persistCurrentUser` + defaults + sync) |
| Keep existing auth deep-link host | Not required for OTP signup; leave for recovery / future OAuth |
| Dashboard email template change | Supabase sends `{{ .Token }}` only when Confirm signup template includes it |

## Data Model Changes

None (Room / PostgREST unchanged).

## Files Added/Modified

| File path | Purpose |
|---|---|
| `domain/repository/AuthRepository.kt` | `verifySignupOtp` |
| `data/repository/SupabaseAuthRepository.kt` | Supabase OTP verify + profile hydrate |
| `domain/model/SignUpResult.kt` | KDoc: code vs link |
| `presentation/auth/AuthViewModel.kt` | `verifySignupOtp` |
| `presentation/auth/AuthScreens.kt` | OTP field + Verify CTA |
| `presentation/navigation/SplitEaseNavHost.kt` | Wire verify callback |
| `res/values*/strings.xml` | OTP copy |
| `AuthViewModelTest.kt` | Verify / validation coverage |
| Living docs | CHANGELOG, PROGRESS, release-checklist, ARCHITECTURE |

## Screens/UI Added

| Screen | Change |
|---|---|
| Verify email | 6-digit code field, Verify button, Resend code, Back to login |

## How to Test

### Supabase Dashboard (required once)
1. Authentication → Providers → Email → **Confirm email** ON.
2. Authentication → Providers → Email → **OTP length** = **6** (must match the app).
3. Authentication → Email Templates → **Confirm signup**: paste [supabase-confirm-signup-otp.html](supabase-confirm-signup-otp.html)  
   (uses `{{ .Token }}`; no `{{ .ConfirmationURL }}` so users enter the code in-app).  
   Or run `.\scripts\configure-signup-otp-email.ps1` (sets template + `mailer_otp_length=6`).

### Manual
1. Sign up with a new email → verify screen shows code field.
2. Enter the 6-digit code from email → lands on Home signed in.
3. Wrong code → error; Resend → new code works.
4. Confirm no App Link / custom-scheme step is needed for signup verify.

### Automated
```bash
.\gradlew.bat :app:testDebugUnitTest --tests com.splitease.app.presentation.auth.AuthViewModelTest
```

## Known Issues / TODOs

- **Confirm signup email template must use `{{ .Token }}`** — the Android app cannot change Supabase mail content.
- **Free-tier blocker (June 2026):** new Free projects using Supabase’s **default** email provider **cannot edit auth templates** (API returns 400). Unlock editing by either:
  1. **Custom SMTP** (recommended, free): Authentication → Emails → SMTP Settings (e.g. [Resend SMTP](https://resend.com/docs/send-with-supabase-smtp)), then paste [supabase-confirm-signup-otp.html](supabase-confirm-signup-otp.html) into Confirm signup, **or**
  2. Upgrade to Pro, **or**
  3. Configure a Send Email Auth Hook.
  Script (after SMTP/Pro unlocks editing): `.\scripts\configure-signup-otp-email.ps1` with a personal access token.
- Password reset remains link-oriented (`splitease://auth-callback` still registered).
- Free-tier Auth email rate limits apply to resend.

## Screenshots

_Placeholder — add device captures of verify-OTP screen when available._

## Outcome

Updated 2026-07-27: real OTP path is now active in app logic.

- Verify screen enforces a 6-digit numeric code.
- `AuthViewModel.verifySignupOtp` now calls `AuthRepository.verifySignupOtp(...)` directly.
- `AuthViewModel.resendConfirmation` now calls `AuthRepository.resendSignupConfirmation(...)`.
- Dev-only hardcoded OTP acceptance (`1234`) was removed from the app flow.

Operator requirements remain:
- Confirm email must stay ON for OTP-gated signup behavior.
- Mailer OTP length must be **6** (`mailer_otp_length`).
- Confirm signup template must include `{{ .Token }}` (see [supabase-confirm-signup-otp.html](supabase-confirm-signup-otp.html)).
- SMTP/provider configuration must be healthy (or equivalent provider setup) for reliable email delivery.
