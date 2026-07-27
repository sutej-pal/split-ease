# Phase 10d — Onboarding Start Email (Render Mail Service)

## Phase Goal
Send a transactional onboarding-start email when a newly signed-in user enters the onboarding flow, using the external Render-hosted mail service.

## Scope

### In
- Add app-side mail client integration against the Render `/send-mail` endpoint.
- Trigger email once per user at onboarding start.
- Keep onboarding UX resilient (email failure must not block onboarding).
- Add configuration through `BuildConfig` from `local.properties` / environment variables.

### Out
- Rich HTML templating system for multiple email types.
- Server-side queueing/retry orchestration.
- Admin dashboards or analytics for delivery events.

## Architecture Decisions
- Use a dedicated `MailRepository` + `MailRemoteDataSource` in clean architecture layers.
- Use lightweight `HttpURLConnection` POST for compatibility and low surface area.
- Persist a per-user local flag in `AppSettingsRepository` to avoid duplicate onboarding-start sends.
- Treat mail send as best-effort side effect (errors swallowed after local attempt).

## Data Model Changes
- SharedPreferences (`splitease_settings`) adds per-user onboarding-email key:
  - `onboarding_email_sent_{userId}` → `Boolean`

## Files Added/Modified
- To be updated in Outcome.

## Screens/UI Added
- None (behavior-only change).

## How to Test
- Configure Render mail service URL/API key in app config.
- Sign up / verify OTP with a user who has not received the welcome email yet.
- Confirm a single email is sent on first signed-in session (no setup screen).
- Reopen the app and confirm no duplicate email is sent.

## Known Issues/TODOs
- Delivery confirmation is not exposed to end users (best-effort background side effect).
- If a send fails once, current behavior may retry next onboarding entry until success.
- **Render Free blocks outbound SMTP** (ports 25/465/587). Gmail SMTP on the Free mail-service hangs until timeout. Use `RESEND_API_KEY` (HTTPS) on Render, or upgrade the instance for SMTP.

## Screenshots placeholder
- N/A (no UI delta)

## Outcome
- Implemented:
  - Added `MailRepository` + `RenderMailRepository` with `MailRemoteDataSource`.
  - Added `BuildConfig` mail config keys (`MAIL_SERVICE_BASE_URL`, `MAIL_SERVICE_API_KEY`).
  - Added onboarding-start trigger in `OnboardingViewModel` (fires on first signed-in session; no setup UI).
  - Added per-user flag in app settings to prevent duplicate sends.
- Validation:
  - Code compiles and lint checks pass for touched files.
