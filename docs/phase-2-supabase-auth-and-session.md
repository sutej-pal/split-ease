# Phase 2 — Authentication (Supabase)

Wired Supabase email/password auth (sign up, sign in, forgot password, session gating) so navigation is driven by signed-in state and the local Room user row is seeded on login. Cloud identity starts here; friends/groups come next.

## Phase Goal

Replace the planned Firebase Auth path with **Supabase Auth** using the provided SplitEase project credentials, so users can sign up, log in, reset a password, and land on an empty home dashboard — with session state driving navigation.

## Scope (In / Out)

**In**
- Supabase Kotlin client (`auth-kt`) wired via Hilt
- Email/password: sign up, sign in, sign out, forgot-password (reset email)
- `AuthRepository` + `AuthViewModel` + session observation
- Compose screens: Welcome (CTA), Login, Signup, Forgot Password, empty Home/Dashboard
- Persist authenticated user into local Room `users` table; seed default categories
- Secrets via `local.properties` → `BuildConfig` (not committed)

**Out**
- Google Sign-In / OAuth (stub button + toast; needs Google Cloud + Supabase provider config)
- **Email confirmation / verification mails** — skipped for now (MVP). See TODO below.
- PostgREST friends-groups sync (Phase 3)
- Expense UI (Phase 4)
- Embedding the **database password** in the Android app (never — server-only)

## Architecture Decisions

| Decision | Rationale |
|---|---|
| Backend = **Supabase** (not Firebase) | User-provided project credentials; explicit override of default Firebase |
| Store only URL + anon key in app | Anon key is client-safe with RLS; DB password must never ship in APK |
| `supabase-kt` 3.1.4 + Ktor Android 3.1.3 | Official Kotlin client for Auth |
| Session-driven NavHost | Logged-out → auth graph; logged-in → home |
| Upsert Room `User` on successful auth | Keeps Phase 1 offline cache aligned with auth identity |
| Skip email confirmation (MVP) | Faster local testing; signup can establish a session immediately when Confirm email is off in Supabase |

## Data Model Changes

No Room schema version bump. On auth success, upsert into existing `users` with:

| Field | Value |
|---|---|
| id / remoteId | Supabase auth user UUID |
| email | Account email |
| displayName | `user_metadata.display_name` or email local-part |
| syncStatus | `SYNCED` |

## Files Added/Modified

| File path | Purpose |
|---|---|
| `local.properties` (gitignored) | `SUPABASE_URL`, `SUPABASE_ANON_KEY` |
| `app/build.gradle.kts` | BuildConfig fields, supabase deps, serialization plugin, v0.3.0 |
| `gradle/libs.versions.toml` | supabase BOM, auth-kt, ktor-android |
| `data/di/SupabaseModule.kt` | Hilt `SupabaseClient` |
| `data/repository/SupabaseAuthRepository.kt` | Auth + Room user upsert |
| `domain/model/AuthSession.kt` | Session sealed type |
| `domain/repository/AuthRepository.kt` | Auth interface |
| `presentation/auth/*` | Screens + AuthViewModel |
| `presentation/home/HomeScreen.kt` | Empty dashboard |
| `presentation/navigation/SplitEaseNavHost.kt` | Session-gated nav |
| `presentation/welcome/WelcomeScreen.kt` | CTAs to signup/login |
| `AndroidManifest.xml` | `INTERNET` permission |
| `src/test/.../AuthViewModelTest.kt` | ViewModel unit tests |

## Screens/UI Added

| Screen | Description |
|---|---|
| Welcome | Brand + Get started / Log in |
| Login | Email/password + Google stub |
| Signup | Name / email / password |
| ForgotPassword | Send reset email |
| Home | Greeting + empty state + Sign out |

## How to Test This Phase

### Manual
1. Ensure `local.properties` has `SUPABASE_URL` and `SUPABASE_ANON_KEY`.
2. In Supabase Dashboard → **Authentication → Providers → Email**: enabled.
3. **MVP (required for skip-confirm flow):** Authentication → **Providers → Email** → turn **Confirm email** **OFF**  
   (or Authentication → Settings → disable “Enable email confirmations”, depending on dashboard layout).
4. Run the app → Welcome → Sign up → should land on Home without waiting for a confirmation email.
5. Sign out → Log in again → Home.
6. Forgot password → check email for reset link (reset mail still uses Supabase email; separate from signup confirm).
7. Kill app and relaunch → session should restore to Home if still valid.

### Automated
```bash
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat ktlintCheck
```
- `AuthViewModelTest` covers success/failure/reset messaging — **passed**

## Known Issues / TODOs carried forward

- **TODO — Re-enable email confirmation before production:** Signup confirmation mails are **intentionally skipped** for MVP. Keep Supabase **Confirm email** disabled until we add a proper verify-email UX (deep link / in-app “check your inbox” state). Track this before Phase 9 release prep.
- **Google Sign-In** not wired — configure Google provider in Supabase + Android OAuth redirect / deep link in a later pass.
- Password reset deep-link / in-app new-password screen not built (email link opens browser/Supabase page).
- Supabase free-tier Auth limits (MAUs / email rate limits) can throttle abuse testing — flag for prod.
- **Security:** Database password was shared in chat — rotate it in Supabase if this chat is retained; it is **not** stored in the app.
- Capture screenshot into `docs/screenshots/phase-2.png`.

## Screenshots placeholder

![phase-2-screenshot](./screenshots/phase-2.png)

---

## Plan

1. Write credentials to gitignored `local.properties`; expose URL + anon key via `BuildConfig`.
2. Add supabase-kt BOM, `auth-kt`, Ktor Android engine, kotlinx-serialization plugin.
3. Implement `SupabaseModule`, `AuthRepository`, session Flow, Room user upsert.
4. Build auth + home Compose screens; gate navigation on session.
5. Unit-test ViewModel; assembleDebug + ktlint.
6. Document Outcome; stop before Phase 3.

---

## Outcome

**Status:** Done (2026-07-22)

Phase 2 delivered Supabase email/password auth with session-gated navigation to an empty Home dashboard. Room user cache is upserted on sign-in/sign-up; default categories are seeded. Build, unit tests, and ktlint are green (`0.3.0`).

**Email confirmation:** skipped for MVP — Confirm email must stay **off** in the Supabase project until the production TODO is completed.

**Next:** Phase 3 — Friends & Groups (local + Supabase PostgREST sync). Do not start until instructed to continue.

### Revisited on 2026-07-22

Documented intentional skip of authentication confirmation email; marked re-enable as a pre-production TODO.
