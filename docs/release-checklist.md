# SplitEase release checklist (Phase 9)

Use before uploading a Play Console build.

## Build & version
- [ ] `versionName` / `versionCode` bumped in `app/build.gradle.kts`
- [ ] `./gradlew assembleStandardRelease` (or bundle) succeeds
- [ ] Release signing configured locally (keystore not committed)
- [ ] ProGuard/R8 rules reviewed if minify enabled

## Supabase production
- [ ] Confirm email **ON** in Authentication → Providers → Email
- [ ] Redirect URL allow-list includes `splitease://auth-callback`
- [ ] Site URL set to a real landing or Play listing URL
- [ ] Phase 3–6 SQL applied (profiles, groups, invites, expenses, payments) + RLS verified
- [ ] Anon key only in the app; service role never shipped

## App QA
- [ ] Sign up → verify-email screen when confirmation required → confirm → log in
- [ ] Resend confirmation works
- [ ] Password reset email arrives
- [ ] Language switch (Settings → Language) updates UI for at least 2 locales
- [ ] Offline create expense → Sync now uploads
- [ ] Settle-up pay intents + CSV import + Spending chart smoke
- [ ] Biometric lock timeout smoke
- [ ] Room upgrade from prior install does **not** wipe data (migrations 1→4)

## Store
- [ ] Short/full descriptions from `docs/store-listing.md`
- [ ] Feature graphic + screenshots (phone) prepared
- [ ] Privacy policy URL ready
- [ ] Content rating questionnaire completed

## Tests
- [ ] `./gradlew testStandardDebugUnitTest`
- [ ] `./gradlew connectedStandardDebugAndroidTest` (device/emulator)
