# Secondary email Send OTP — auth notes

## Symptom

On **Sign in and contact → Email → Send OTP**, the UI showed a generic error and Logcat:

```
IllegalStateException: Unauthorized
at SupabaseAuthRepository.callSecondaryEmailEdgeFunction
```

Password verification had already succeeded (wrong password would surface a different message).

## Root cause

Call order for non-Google-only **Send OTP**:

1. `addSecondaryEmail` → `verifyCurrentPassword` → `verifyPasswordWithoutReplacingSession`
2. Password grant succeeds (`/auth/v1/token?grant_type=password`) — so the password is correct
3. Client then `POST /auth/v1/logout` with the **one-off** access token to discard that verify session
4. `callSecondaryEmailEdgeFunction("add", …)` with the **app** session’s access token
5. Edge Function (or JWT gate) returns JSON `{ "error": "Unauthorized" }`

Step 3 is the bug. GoTrue’s default logout scope is **`global`**: omitting `scope` revokes **all** sessions for the user, including the app’s real session. The client still holds the old access token in memory, so step 4 sends a **revoked** JWT. The in-repo function’s `adminClient.auth.getUser(jwt)` then returns `Unauthorized` (exact string thrown to the UI).

**Why the local session-resolution tweak did not help:** reading `currentSessionOrNull()` / `sessionStatus` still yields the same revoked access token string. The failure is server-side revocation, not “which Kotlin property held the session.”

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
