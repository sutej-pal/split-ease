# SplitEase release checklist (Phase 9)

Use before uploading a Play Console build.

Current `versionName` / sideload APKs are **testing only**. First production Play upload is [TODO(release)](../TODO.md).

## Build & version
- [ ] Create the next build with `./gradlew newRelease` (or `.\scripts\new-release.ps1`) — increments `versionCode`, bumps `versionName`, records [RELEASES.md](../RELEASES.md), cuts [CHANGELOG.md](../CHANGELOG.md)
- [ ] Confirm `versionName` / `versionCode` in `version.properties` (`./gradlew printVersion`)
- [ ] `./gradlew assembleRelease` (or bundle) succeeds
- [ ] Host Digital Asset Links: `https://splitease.app/.well-known/assetlinks.json` (and mail-service host if used) — see [app-links-setup.md](app-links-setup.md)
- [ ] Settings → SplitEase → Open by default shows verified invite hosts (not "0 verified links")
- [ ] Release signing configured locally (keystore not committed)
- [ ] ProGuard/R8 rules reviewed if minify enabled

## Supabase production
- [ ] Confirm email **ON** in Authentication → Providers → Email
- [ ] Email OTP length = 6 (Auth → Providers → Email, or `mailer_otp_length`)
- [ ] Confirm signup email template includes `{{ .Token }}` (paste [server/mail-templates/supabase/confirm-signup.html](../../server/mail-templates/supabase/confirm-signup.html) or run `scripts/configure-signup-otp-email.ps1`)
- [ ] Redirect URL allow-list includes `splitease://auth-callback` (legacy / other Auth redirects; **not** required for signup, password-reset OTP, or Google ID-token sign-in)
- [ ] Google provider enabled with Web client ID + secret; Android OAuth client SHA-1s added; `GOOGLE_WEB_CLIENT_ID` in `local.properties` ([google-sign-in.md](google-sign-in.md))
- [ ] mail-service / SplitEase Server redeployed with recovery OTP template (or Reset password dashboard template includes `{{ .Token }}` — [server/mail-templates/supabase/reset-password.html](../../server/mail-templates/supabase/reset-password.html))
- [ ] Site URL set to a real landing or Play listing URL
- [ ] Fresh DB SQL applied via [migration_db.sql](sql/migration_db.sql) (profiles, groups, invites, expenses, payments, RLS, realtime, device tokens, auth RPCs) — verified
- [ ] FCM configured when using push ([fcm-setup.md](fcm-setup.md); notify triggers are in migration_db.sql and no-op until settings are set)
- [ ] `app/google-services.json` present for release builds that need push; Edge Function `notify-group-members` deployed with `FIREBASE_SERVICE_ACCOUNT_JSON`
- [ ] Anon key only in the app; service role never shipped

## App QA
- [ ] Sign up → verify-email OTP screen when confirmation required → enter 6-digit code → signed in on Home
- [ ] Continue with Google (new + returning) lands on Groups without OTP; first-time Google user gets welcome mail
- [ ] Resend code works
- [ ] Password reset: Forgot password → OTP email (recovery copy) → enter code + new password → signed in / can log in with new password
- [ ] Language switch (Settings → Language) updates UI for at least 2 locales
- [ ] Offline create expense → go online and verify auto-sync uploads within worker window
- [ ] Settle-up pay intents + CSV import + Spending chart smoke
- [ ] Biometric lock timeout smoke
- [ ] Room upgrade from a prior installation does **not** wipe data (migrations 1→4)

## Store
- [ ] Short/full descriptions from `docs/store-listing.md`
- [ ] Feature graphic + screenshots (phone) prepared
- [ ] Privacy policy URL live at https://splitease-server-eight.vercel.app/privacy (and Terms at https://splitease-server-eight.vercel.app/terms)
- [ ] Content rating questionnaire completed

## Tests
- [ ] `./gradlew testDebugUnitTest`
- [ ] `./gradlew connectedDebugAndroidTest` (device/emulator)
