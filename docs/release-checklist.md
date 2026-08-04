# SplitEase release checklist (Phase 9)

Use before uploading a Play Console build.

## Build & version
- [ ] `versionName` / `versionCode` bumped in `app/build.gradle.kts`
- [ ] `./gradlew assembleRelease` (or bundle) succeeds
- [ ] Host Digital Asset Links: `https://splitease.app/.well-known/assetlinks.json` (and mail-service host if used) — see [app-links-setup.md](app-links-setup.md)
- [ ] Settings → SplitEase → Open by default shows verified invite hosts (not “0 verified links”)
- [ ] Release signing configured locally (keystore not committed)
- [ ] ProGuard/R8 rules reviewed if minify enabled

## Supabase production
- [ ] Confirm email **ON** in Authentication → Providers → Email
- [ ] Email OTP length = 6 (Auth → Providers → Email, or `mailer_otp_length`)
- [ ] Confirm signup email template includes `{{ .Token }}` (paste [supabase-confirm-signup-otp.html](supabase-confirm-signup-otp.html) or run `scripts/configure-signup-otp-email.ps1`)
- [ ] Redirect URL allow-list includes `splitease://auth-callback` (legacy / other Auth redirects; **not** required for signup or password-reset OTP)
- [ ] mail-service redeployed with recovery OTP template (or Reset password dashboard template includes `{{ .Token }}` — [supabase-reset-password-otp.html](supabase-reset-password-otp.html))
- [ ] Site URL set to a real landing or Play listing URL
- [ ] Fresh DB SQL applied via [migration_db.sql](sql/migration_db.sql) (profiles, groups, invites, expenses, payments, RLS, realtime, device tokens) — verified
- [ ] Optional FCM notify triggers when using push ([phase-extras-notify-triggers.sql](sql/phase-extras-notify-triggers.sql), [fcm-setup.md](fcm-setup.md))
- [ ] `app/google-services.json` present for release builds that need push; Edge Function `notify-group-members` deployed with `FIREBASE_SERVICE_ACCOUNT_JSON`
- [ ] Anon key only in the app; service role never shipped

## App QA
- [ ] Sign up → verify-email OTP screen when confirmation required → enter 6-digit code → signed in on Home
- [ ] Resend code works
- [ ] Password reset: Forgot password → OTP email (recovery copy) → enter code + new password → signed in / can log in with new password
- [ ] Language switch (Settings → Language) updates UI for at least 2 locales
- [ ] Offline create expense → go online and verify auto-sync uploads within worker window
- [ ] Settle-up pay intents + CSV import + Spending chart smoke
- [ ] Biometric lock timeout smoke
- [ ] Room upgrade from prior install does **not** wipe data (migrations 1→4)

## Store
- [ ] Short/full descriptions from `docs/store-listing.md`
- [ ] Feature graphic + screenshots (phone) prepared
- [ ] Privacy policy URL ready
- [ ] Content rating questionnaire completed

## Tests
- [ ] `./gradlew testDebugUnitTest`
- [ ] `./gradlew connectedDebugAndroidTest` (device/emulator)
