# Maintenance — Email signup OTP verification

## Phase Goal

Replace link-based (`click to confirm`) email signup verification with a **6-digit OTP** flow: after email/password signup, the user enters a numeric code from email in the app. No deep linking, custom URI schemes, or AndroidManifest intent-filter changes are required for this flow.

## Scope (In / Out)

**In**
- In-app OTP entry on the existing verify-email screen
- `AuthRepository.verifySignupOtp` via Supabase `verifyEmailOtp` (`OtpType.Email.SIGNUP`)
- Resend confirmation still uses `resendEmail(SIGNUP)` (delivers a new code when the template includes `{{ .Token }}`)
- Copy, ViewModel, unit tests, living docs / checklist updates

**Out**
- Removing `splitease://auth-callback` (still useful for other Auth redirects)
- Changing Supabase project templates in-repo (dashboard steps documented only; preferred path is mail-service hook)

## Architecture Decisions

| Decision                            | Rationale                                                                   |
| ----------------------------------- | --------------------------------------------------------------------------- |
| OTP type `SIGNUP` (verify + resend) | Matches email/password signup confirmation; resend must use `SIGNUP`        |
| Session after successful verify     | Same hydrate path as sign-in (`persistCurrentUser` + defaults + sync)       |
| Keep existing auth deep-link host   | Not required for OTP signup; leave for recovery / future OAuth              |
| Dashboard email template change     | Supabase sends `{{ .Token }}` only when Confirm signup template includes it |

## Data Model Changes

None (Room / PostgREST unchanged).

## Files Added/Modified

| File path                                     | Purpose                                              |
| --------------------------------------------- | ---------------------------------------------------- |
| `domain/repository/AuthRepository.kt`         | `verifySignupOtp`                                    |
| `data/repository/SupabaseAuthRepository.kt`   | Supabase OTP verify + profile hydrate                |
| `domain/model/SignUpResult.kt`                | KDoc: code vs link                                   |
| `presentation/auth/AuthViewModel.kt`          | `verifySignupOtp`                                    |
| `presentation/auth/AuthScreens.kt`            | OTP field + Verify CTA                               |
| `presentation/navigation/SplitEaseNavHost.kt` | Wire verify callback                                 |
| `res/values*/strings.xml`                     | OTP copy                                             |
| `AuthViewModelTest.kt`                        | Verify / validation coverage                         |
| Living docs                                   | CHANGELOG, PROGRESS, release-checklist, ARCHITECTURE |

## Screens/UI Added

| Screen       | Change                                                        |
| ------------ | ------------------------------------------------------------- |
| Verify email | 6-digit code field, Verify button, Resend code, Back to login |

## How to Test

### Preferred: mail-service Auth Hook (Free tier)
1. Redeploy Render `mail-service` with `POST /supabase/send-email-hook`.
2. Run `.\scripts\configure-signup-otp-email.ps1` (enables hook + sets OTP length 6).
3. Optional: set matching `SEND_EMAIL_HOOK_SECRET` on Render and in Supabase Auth Hooks.
4. Sign up > receive SplitEase 6-digit OTP email from mail-service > verify in app.

### Fallback: Supabase template (Custom SMTP / Pro only)
1. Authentication > Providers > Email > **Confirm email** ON.
2. Authentication > Providers > Email > **OTP length** = **6**.
3. Authentication > Email Templates > **Confirm signup**: paste [supabase-confirm-signup-otp.html](supabase-confirm-signup-otp.html)  
   Or run `.\scripts\configure-signup-otp-email.ps1 -TemplateOnly`.

### Manual
1. Sign up with a new email > verify screen shows code field.
2. Enter the 6-digit code from email > lands on Home signed in.
3. Wrong code > error; Resend > new code works.
4. Confirm no App Link / custom-scheme step is needed for signup verify.

### Automated
```bash
.\gradlew.bat :app:testDebugUnitTest --tests com.splitease.app.presentation.auth.AuthViewModelTest
```

## Known Issues / TODOs

- **Confirm signup email template must use `{{ .Token }}`** when *not* using the Send Email hook — the Android app cannot change Supabase mail content.
- **Preferred ops path:** use mail-service Send Email Auth Hook (`/supabase/send-email-hook`) so Free-tier projects do not need template editing.
- **Free-tier blocker (June 2026):** new Free projects using Supabase's **default** email provider **cannot edit auth templates** (API returns 400). Unlock editing by either:
  1. **Custom SMTP** (recommended, free): Authentication > Emails > SMTP Settings (e.g. [Resend SMTP](https://resend.com/docs/send-with-supabase-smtp)), then paste [supabase-confirm-signup-otp.html](supabase-confirm-signup-otp.html) into Confirm signup, **or**
  2. Upgrade to Pro, **or**
  3. Configure a Send Email Auth Hook (mail-service).
  Script: `.\scripts\configure-signup-otp-email.ps1`.
- If the Send Email hook URL 404s/500s, Supabase signup fails with a generic error — disable the hook (`-DisableHook`) until mail-service is healthy.
- **Resend testing mode:** without a verified domain, Resend only delivers to the Resend account email. Verify a domain at [resend.com/domains](https://resend.com/domains) and set `MAIL_FROM` to an address on that domain before production signup.
- Free-tier Auth email rate limits apply to resend.
- **Password reset OTP** is covered in [phase-12-forgot-password-email-otp.md](phase-12-forgot-password-email-otp.md) (recovery template + in-app set password). Redeploy mail-service after template changes.

## Screenshots

_Placeholder — add device captures of verify-OTP screen when available._

## Outcome

Updated 2026-07-29: signup OTP delivery moved to the Render **mail-service** via Supabase **Send Email Auth Hook**.

- Verify screen enforces a 6-digit numeric code.
- `AuthViewModel.verifySignupOtp` calls `AuthRepository.verifySignupOtp(...)`.
- `AuthViewModel.resendConfirmation` calls `AuthRepository.resendSignupConfirmation(...)`.
- mail-service exposes `POST /supabase/send-email-hook` and sends the SplitEase OTP HTML.
- Ops script: `.\scripts\configure-signup-otp-email.ps1` (probe + enable hook; `-DisableHook` / `-TemplateOnly` available).

Operator requirements remain:
- Confirm email must stay ON for OTP-gated signup behavior.
- Mailer OTP length must be **6** (`mailer_otp_length`).
- Send Email hook must point at a healthy mail-service (otherwise signup fails).
- SMTP/provider configuration must be healthy (Resend HTTPS on Render Free).
- **Resend testing mode blocker (2026-07-29):** without a verified domain, Resend only delivers to the Resend account email (`sutejpal@hotmail.com`). Until a domain is verified and `MAIL_FROM` uses it, keep `mailer_autoconfirm=true` + Send Email hook OFF so emulator/dev signup can create a session; re-enable Confirm email + hook after Resend domain verification.
