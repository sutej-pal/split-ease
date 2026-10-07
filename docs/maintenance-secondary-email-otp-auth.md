# Secondary email Send OTP — auth notes

## Symptom

On **Sign in and contact → Email → Send OTP**, the UI showed a generic error and Logcat:

```
IllegalStateException: Unauthorized
at SupabaseAuthRepository.callSecondaryEmailEdgeFunction
```

Password verification had already succeeded (wrong password would surface a different message).

## Root cause

`verifyPasswordWithoutReplacingSession` proves the password with a one-off GoTrue `grant_type=password` token, then revokes that token via `POST /auth/v1/logout`.

GoTrue’s default logout scope is **`global`**: omitting `scope` revokes **all** sessions for the user, including the app’s real session. The client still held the old access token in memory, so the next `POST /functions/v1/secondary-email` call reached the Edge Function with a revoked JWT. The function’s `auth.getUser(jwt)` then returned `Unauthorized`.

A session-resolution tweak alone (`currentSessionOrNull` vs `sessionStatus`) does not fix this — the token itself was invalidated server-side.

## Fix (app)

1. Revoke the verify session with `POST /auth/v1/logout?scope=local`.
2. Refresh the access token (`refreshCurrentSession`) before the Edge Function HTTP call.
3. Map 401 / `Unauthorized` to a clear “session expired” user message; pass through other Edge Function error strings when they are already user-facing.

Google-only accounts skip password verify, so they never hit the global logout path; they still benefit from the refresh before the Edge Function call.

## Deploy / config (Edge)

No Edge Function code change is required for this bug. Confirm:

- Function `secondary-email` is deployed.
- `supabase/config.toml` keeps `verify_jwt = true` for `secondary-email`.
- Secrets: `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `MAIL_SERVICE_BASE_URL`, optional `MAIL_SERVICE_API_KEY` / `EMAIL_OTP_PEPPER`.
- SQL: `docs/sql/patch_account_settings.sql` applied (`user_emails`, send log, RPCs).

## How to verify

1. Sign in with email + password (not Google-only).
2. Account settings → **Sign in and contact** → Email → add secondary email + current password → **Send OTP**.
3. Expect success toast / OTP email (or a specific rate-limit / validation message), **not** `Unauthorized` / generic “Something went wrong”.
4. Wrong password should still fail before the Edge Function with a credentials/password message.
5. Google-only account: add secondary email without a password field; Send OTP should work without requiring a password.
