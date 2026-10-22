# SplitEase App — TODO

Consolidated open work from `PROGRESS.md`, phase docs, extras, and in-code `TODO` markers.

Items that were marked done and then checked on the emulator are in [COMPLETED.md](COMPLETED.md) and [FAILED.md](FAILED.md).

## Auth & onboarding

- [ ] **TODO(auth-mobile-onboarding)** — Allow users to onboard with a mobile phone number (SMS OTP / phone auth) in addition to email.
- [ ] **TODO(password-security-rules)** — Enforce password security rules (special characters and numbers) for new password updates and sign-up.



## Review follow-ups (2026-10-01)

Phone invites (staged) and the empty-group card (`1bbb662`). Detail: [docs/review-2026-10-01-phone-invite-empty-group.md](docs/review-2026-10-01-phone-invite-empty-group.md).

- [ ] **TODO(invite-token-disclosure)** — Public `get_invite_preview` returns other pending members' invite tokens. Deferred on purpose; do not change until this is picked up.
- [ ] **TODO(group-resend-sms-dialog)** — Group settings resend still shows the SMS confirm dialog. Friend resend does not.
- [ ] **TODO(invite-landing-resume)** — Invite landing reloads on every resume and can overwrite the pending member token.
- [ ] **TODO(phone-embedded-country-code)** — A number that already includes the country code without `+` or `00` gets a second dial prefix.
- [ ] **TODO(share-chooser-once)** — Email share sheet can open twice on rotation. SMS is already guarded.
- [ ] **TODO(unknown-dial-prefill)** — Calling codes outside the ten-country list never prefill sign-up.
- [ ] **TODO(dial-plus-one-flag)** — `+1` always shows the US flag, including for Canada.
- [ ] **TODO(primary-button-height-scope)** — Confirm `SePrimaryButton` min height `48.dp` (was fixed `56.dp`) for every primary button.



## Review follow-ups (2026-10-21)

Sign-in & Contact OTP state, Activity row typography, and category chip modulo cleanup.

- [ ] **TODO(pending-otp-emails-cleanup)** — In `SignInAndContactViewModel.kt`, `pendingOtpEmails` is added to in `addSecondaryEmail()` and removed in the `observeSecondaryEmails()` collector. Clear `pendingOtpEmails` in `toggleRow()` (or keep only the latest added email) so stale entries cannot linger if the row is collapsed before the flow emits.
- [ ] **TODO(activity-title-span-weights)** — `ActivityRowTitle` in `ActivityScreen.kt` now uses `SeTitleSmall` (`16.sp`, `FontWeight.Normal`), while `ActivityViewModel.formatActivityTitle` / `formatGroupCreatedTitle` / `formatPaymentTitle` apply `SpanStyle(fontWeight = FontWeight.Bold)` and `FontWeight.SemiBold` to names and titles. Confirm whether `Bold` (`W700`) spans or `Medium`/`SemiBold` spans are desired against the lighter `Normal` (`W400`) base text.
- [ ] **TODO(category-chip-hash-mod)** — In `ExpenseDetailScreen.kt` (`CategoryChip`), `iconKey.hashCode().mod(pastels.size).let { if (it < 0) it + pastels.size else it }` has a redundant negative-remainder check because Kotlin's `.mod(n)` already returns a non-negative index in `0..<n` (matching `LedgerEntryUi.kt`).



## Invites & App Links

- [ ] **App Links / invite https** — Host [docs/assetlinks.json](docs/assetlinks.json) at `/.well-known/assetlinks.json` on invite hosts for verified Open-by-default links. See [docs/app-links-setup.md](docs/app-links-setup.md).
- [ ] **Install Referrer E2E** — Full deferred-invite proof needs a Play install (Internal testing). Sideload does not populate referrer.
- [ ] **Redeploy mail-service** `/invite/:token` — Open-app then Play Store with `referrer=invite_token%3D...`; redeploy Vercel after pulling mail-service changes.



## Sync, live updates & notifications

Ordered Supabase follow-ups (deletes → conflicts → categories → pin-board boundary → Edge Functions → ops): [docs/supabase-architecture-todos.md](docs/supabase-architecture-todos.md).

- [ ] **FX mark-to-market** — No live re-conversion of historical expenses into one display currency.



## Payments & stretch

- [ ] **Payment handles** — UPI VPA / PayPal / Venmo usernames are not stored yet; deep links open apps with amount only.



## i18n & polish

- [ ] **TODO(i18n-last)** — Restore and expand full translations; locale overlays (`values-de/es/fr/hi/it/ja/pt`) currently fall back to English.
- [ ] **ViewModel string Context extraction** — Groups / Account / Activity partially outstanding.
- [ ] **detekt** — Optional static analysis beyond ktlint (deferred from Phase 0).



## Store / release

- [ ] **TODO(release) — First production / Play release** — Current `versionCode` / sideload APKs are **testing only** (including `1.0.0` build 2). Do not treat them as shipped. When actually cutting a store build: `./gradlew newRelease` (or `scripts/new-release.ps1`), then complete [docs/release-checklist.md](docs/release-checklist.md). History: [RELEASES.md](RELEASES.md).
- [ ] **Feature graphic** — 1024×500 (`docs/store-listing.md`).
- [ ] **Phone screenshots** — >=2 in `docs/screenshots/`.
- [ ] Remaining checklist items in [docs/release-checklist.md](docs/release-checklist.md).
