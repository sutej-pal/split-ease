# Phase 12 — Forgot password via email OTP

Replaced link-based password reset with a 6-digit email OTP flow: request code, enter OTP, set a new password in-app. Recovery mail uses a dedicated template (not the signup copy).

## Phase Goal

Replace link-based password reset with a **6-digit email OTP** flow that matches signup verification: request code → enter OTP + new password in-app → password updated. Recovery mail uses a dedicated Reset password template (not the signup copy).

## Scope (In / Out)

**In**
- mail-service `buildOtpMail` branch for `recovery` / `reset` (distinct subject + body)
- Fallback Supabase HTML template doc for Reset password
- `AuthRepository.verifyRecoveryOtp` + `updatePassword`
- Forgot-password → OTP + set-new-password screen (gated like signup OTP)
- Resend recovery code via existing `requestPasswordReset` / `resetPasswordForEmail`
- Strings, ViewModel, unit tests, living docs

**Out**
- Deep-link / browser reset page as the primary path (callback host may remain for other Auth redirects)
- SMS password reset
- Changing signup OTP behavior

## Architecture Decisions

| Decision                                                       | Rationale                                                                          |
| -------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Supabase `resetPasswordForEmail` to send                       | Same Auth recovery email path; hook delivers OTP when `email_action_type=recovery` |
| Verify with `OtpType.Email.RECOVERY`                           | Correct type for recovery tokens                                                   |
| Then `updateUser { password }` while session active            | Required after recovery OTP establishes a session                                  |
| Gate Home with `pendingConfirmationEmail` + `RECOVERY` purpose | Same pattern as signup OTP; blocks Home until password is set                      |
| Dedicated recovery mail copy                                   | User reported reset mail reused signup/generic template                            |

## Data Model Changes

None (Room / PostgREST unchanged).

## Files Added/Modified

| File path                                     | Purpose                                            |
| --------------------------------------------- | -------------------------------------------------- |
| `mail-service/server.js`                      | Recovery OTP email template                        |
| `docs/supabase-reset-password-otp.html`       | Dashboard fallback template                        |
| `domain/repository/AuthRepository.kt`         | `verifyRecoveryOtp`, `updatePassword`              |
| `data/repository/SupabaseAuthRepository.kt`   | Supabase RECOVERY verify + password update         |
| `presentation/auth/AuthViewModel.kt`          | `RECOVERY` purpose + `completePasswordReset`       |
| `presentation/auth/AuthScreens.kt`            | Reset OTP + new password UI                        |
| `presentation/navigation/SplitEaseNavHost.kt` | Branch verify UI for recovery                      |
| `res/values/strings.xml`                      | OTP reset copy                                     |
| `AuthViewModelTest.kt`                        | Coverage                                           |
| Living docs                                   | PROGRESS, CHANGELOG, ARCHITECTURE, maintenance OTP |

## Screens/UI Added

| Screen               | Change                                                                                                 |
| -------------------- | ------------------------------------------------------------------------------------------------------ |
| Forgot password      | Copy: send a **code** if an account exists (never "email not found")                                   |
| Reset password (OTP) | After send: always navigate; 6-digit code + new password + confirm; Resend code (Supabase rate-limits) |

## How to Test

1. Redeploy mail-service so recovery template is live.
2. Forgot password → enter registered email → receive **Reset your SplitEase password** mail with 6-digit code (not signup wording).
3. Enter code + new password (≥8) + confirm → lands signed in (or can sign in with new password).
4. Wrong OTP → error; Resend → new code works.
5. Unit: `AuthViewModelTest` password-reset OTP cases.

```bash
.\gradlew.bat :app:testDebugUnitTest --tests com.splitease.app.presentation.auth.AuthViewModelTest
```

## Known Issues / TODOs

- mail-service must be redeployed for recovery copy to reach users on Vercel.
- Free-tier Auth email rate limits apply to recovery resend.
- If Send Email hook is off and dashboard Reset password template still has a link only, in-app OTP will fail until `{{ .Token }}` is present (use [supabase-reset-password-otp.html](supabase-reset-password-otp.html)).

## Outcome

Updated 2026-08-04: forgot-password uses the same mail-service Send Email hook with a dedicated **recovery** template; the Android app verifies `OtpType.Email.RECOVERY` then calls `updatePassword`. Request is privacy-preserving: `requestPasswordReset` always soft-succeeds and navigates to OTP with generic "If an account exists…" copy; verify failures are always "Invalid or expired code."

- Forgot password → `requestPasswordReset` → always `ResetPasswordOtpScreen` (OTP + new password + confirm).
- `AuthRepository.requestPasswordReset` + `verifyRecoveryOtp` + `updatePassword`.
- mail-service `buildOtpMail` treats `recovery` / `reset` separately from signup ("Reset your SplitEase password").
- Fallback dashboard HTML: [supabase-reset-password-otp.html](supabase-reset-password-otp.html).
- Unit tests cover gate arming, soft-success navigate, verify+update, generic invalid OTP, mismatch, and password-only retry after OTP success.

Operator requirements:
- Redeploy mail-service on Vercel so recovery copy is live.
- If the Send Email hook is off, paste the reset OTP HTML into Authentication → Email Templates → **Reset password**.
- Mailer OTP length must remain **6**.

## Screenshots

_Placeholder — add device captures of forgot → OTP + new password when available._
