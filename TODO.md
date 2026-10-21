# SplitEase App — TODO

Consolidated open work from `PROGRESS.md`, phase docs, extras, and in-code `TODO` markers.

## Auth & onboarding

- [ ] **TODO(auth-mobile-onboarding)** — Allow users to onboard with a mobile phone number (SMS OTP / phone auth) in addition to email.
- [x] **OTP ops checklist** — App + mail-service hook + `{{ .Token }}` templates are in place. Live Confirm email / OTP length 6 / SMTP (Brevo) remain a pre-ship check in [docs/release-checklist.md](docs/release-checklist.md). How-to: [docs/maintenance-email-otp-verification.md](docs/maintenance-email-otp-verification.md).
- [x] **Resend domain** — Superseded. Production OTP uses SplitEase Server + Brevo HTTPS (`BREVO_API_KEY` / `MAIL_FROM`), not Resend SMTP.
- [x] **Google Sign-In** — Credential Manager ID token → Supabase (`signInWith(IDToken)`). Ops: [docs/google-sign-in.md](docs/google-sign-in.md).
- [x] **In-app account deletion** — Account → Account settings → Delete account. Typed `DELETE` confirm; blocked while any group (or non-group) net is non-zero (`BigDecimal.compareTo`); blocked rows open that group or Non-group expenses; server RPC `delete_own_account()` re-checks and anonymizes in place (no hard-delete). SQL: [docs/sql/migration_db.sql](docs/sql/migration_db.sql). **Legal copy still says email [support@splitease.app](mailto:support@splitease.app)** in [privacy-policy.md](docs/legal/privacy-policy.md) / [terms-of-service.md](docs/legal/terms-of-service.md) — keep that as a fallback for edge cases until a human updates the public HTML.
- [x] **Password reset UX** — In-app 6-digit recovery OTP + set-new-password screen ([phase-12](docs/phase-12-forgot-password-email-otp.md)). Email links are not the primary path.
- [ ] **TODO(password-security-rules)** — Enforce password security rules (special characters and numbers) for new password updates and sign-up.
- [x] **Onboarding-complete cloud flag** — Not needed. The post-signup setup wizard was removed; users go straight to the app after OTP. The unused local `onboarding_complete` preference was dropped.
- [x] **Profile photo in onboarding** — Optional avatar on Sign up (crop + 512px JPEG). Compressed into app storage at signup, uploaded after OTP. Google photos are compressed into `user-avatars` on first hydrate.



## Review follow-ups (2026-10-01)

Phone invites (staged) and the empty-group card (`1bbb662`). Detail: [docs/review-2026-10-01-phone-invite-empty-group.md](docs/review-2026-10-01-phone-invite-empty-group.md).

- [ ] **TODO(invite-token-disclosure)** — Public `get_invite_preview` returns other pending members' invite tokens. Deferred on purpose; do not change until this is picked up.
- [ ] **TODO(group-resend-sms-dialog)** — Group settings resend still shows the SMS confirm dialog. Friend resend does not.
- [ ] **TODO(invite-landing-resume)** — Invite landing reloads on every resume and can overwrite the pending member token.
- [ ] **TODO(phone-embedded-country-code)** — A number that already includes the country code without `+` or `00` gets a second dial prefix.
- [ ] **TODO(share-chooser-once)** — Email share sheet can open twice on rotation. SMS is already guarded.
- [ ] **TODO(unknown-dial-prefill)** — Calling codes outside the ten-country list never prefill sign-up.
- [ ] **TODO(dial-plus-one-flag)** — `+1` always shows the US flag, including for Canada.
- [x] **TODO(text-secondary-light-scope)** — Confirmed `TextSecondaryLight` (`#585C7E`) in `Color.kt` and updated `ColorTokensFrozenTest.kt`.
- [x] **TODO(empty-state-theme-mode)** — Empty-group card colors (`SplitEaseColors.EmptyState*`), `seSwitchColors()`, and `BannerCircleIconButton` now follow in-app `ThemeMode` (`darkChrome()`) instead of `isSystemInDarkTheme()`.
- [ ] **TODO(primary-button-height-scope)** — Confirm `SePrimaryButton` min height `48.dp` (was fixed `56.dp`) for every primary button.



## Review follow-ups (2026-10-13)

Account help/support, appearance card, and shared list/theme edits (staged on `develop`).

- [ ] **TODO(rate-play)** — Rate SplitEase must be recorded on Google Play. `RateAppDialog` only sets `submitted = true`; stars and the optional comment are discarded, and the thanks copy says the rating was received. Replace that submit path with the Play in-app review flow (or open the Play listing). Play does not accept a custom star count or comment uploaded by the app.
- [ ] **TODO(support-email-play)** — Publish the contact-support address `support@splitease.app` on the Google Play listing. In the app, Contact support (`openSupportGmail`) pins `com.google.android.gm` whenever Gmail is installed, so the user’s default mail app is skipped. The row says “Email the SplitEase team.”
- [ ] **TODO(action-chip-height)** — `SeActionChip` is a fixed `28.dp` (was `44.dp`) with normal label weight and `TextOverflow.Clip`. Touch target is under `48.dp`, and large font sizes clip Settle up, Balances, Totals, and Pin board on group and friend screens.
- [x] **TODO(theme-row-a11y)** — Added `selectableGroup()` and `Modifier.selectable(..., role = Role.RadioButton)` to `ThemeModeRow` in `AppearanceSettingsScreen.kt` so TalkBack announces which theme is selected.
- [ ] **TODO(help-faq-a11y)** — Help center chevron rotates when a question opens, but the row does not expose expanded or collapsed.
- [ ] **TODO(list-row-type-scope)** — `SeListRow` navigation style changed app-wide: `TitleMedium` semibold / `BodyMedium` became `TitleSmall` regular / `BodySmall`, and horizontal padding went from `16.dp` to `20.dp`. Confirm that scope or limit it to the new help rows. The `Navigation` KDoc still says “Bold title over muted subtitle.”
- [ ] **TODO(faq-split-copy)** — Help answer `faq_add_expense_a` says “exact amounts”; the split screen label is “Unequally.”
- [ ] **TODO(group-settings-preview)** — `GroupSettingsScreenPreview` is a hand-copied layout and uses the placeholder name “asda”, so it can drift from the real screen.



## Review follow-ups (2026-10-16)

Sign-in & Contact error handling, secondary email Edge Function, OTP flows, and dialog vertical actions.

- [x] **TODO(password-row-duplicate-error)** — In `SignInAndContactViewModel.updatePassword()`, when the current password is blank or fails verification, both `currentPasswordError` and `passwordError` are set. Fixed in `SignInAndContactScreen` so `passwordError` only applies to the New Password field when `currentPasswordError == null`.
- [x] **TODO(secondary-email-rpc-error-check)** — In `supabase/functions/secondary-email/index.ts` (`emailTaken`), included `registeredError` in `if (registeredError || profileError || secondaryError) return "error"` (requires `service_role` execute grant on `auth_email_registered` in Supabase).
- [x] **TODO(secondary-email-db-error-masking)** — Restored HTTP 500 `"Failed to add email address."` for unexpected DB/query errors in `supabase/functions/secondary-email/index.ts` (keeping 400 `"That email can't be added."` for taken/duplicate `23505` emails) and removed the error-masking override in `SignInAndContactViewModel.addSecondaryEmail()`.
- [x] **TODO(signin-contact-unused-inset)** — Removed unused `private val BodyStartInset` in `SignInAndContactScreen.kt`.
- [x] **TODO(dialog-vertical-actions-tone)** — Added `containerColor` to `SePrimaryButton` and wired `confirmColor` in `SeDialogActions` (`SeModal.kt`) when `verticalActions = true`.
- [x] **TODO(password-error-heuristic)** — Removed redundant `lower.contains("right")` from `SignInAndContactViewModel.isPasswordRelatedError()`.
- [x] **TODO(auth-unconfirmed-fallback-otp)** — Checked the fallback `authRepository.sendLoginOtp(trimmedEmail)` result in `AuthViewModel.kt` when login hits `email_not_confirmed` and surfaced `friendlyAuthError` on failure instead of navigating to the OTP screen.



## Review follow-ups (2026-10-21)

Sign-in & Contact OTP state, Activity row typography, and category chip modulo cleanup.

- [ ] **TODO(pending-otp-emails-cleanup)** — In `SignInAndContactViewModel.kt`, `pendingOtpEmails` is added to in `addSecondaryEmail()` and removed in the `observeSecondaryEmails()` collector. Clear `pendingOtpEmails` in `toggleRow()` (or keep only the latest added email) so stale entries cannot linger if the row is collapsed before the flow emits.
- [ ] **TODO(activity-title-span-weights)** — `ActivityRowTitle` in `ActivityScreen.kt` now uses `SeTitleSmall` (`16.sp`, `FontWeight.Normal`), while `ActivityViewModel.formatActivityTitle` / `formatGroupCreatedTitle` / `formatPaymentTitle` apply `SpanStyle(fontWeight = FontWeight.Bold)` and `FontWeight.SemiBold` to names and titles. Confirm whether `Bold` (`W700`) spans or `Medium`/`SemiBold` spans are desired against the lighter `Normal` (`W400`) base text.
- [ ] **TODO(category-chip-hash-mod)** — In `ExpenseDetailScreen.kt` (`CategoryChip`), `iconKey.hashCode().mod(pastels.size).let { if (it < 0) it + pastels.size else it }` has a redundant negative-remainder check because Kotlin's `.mod(n)` already returns a non-negative index in `0..<n` (matching `LedgerEntryUi.kt`).



## Invites & App Links

- [x] **Invite email delivery** — Add people / Review sends invite mail via mail-service when the contact is an email; phone contacts still fall back to the share sheet.
- [ ] **App Links / invite https** — Host [docs/assetlinks.json](docs/assetlinks.json) at `/.well-known/assetlinks.json` on invite hosts for verified Open-by-default links. See [docs/app-links-setup.md](docs/app-links-setup.md).
- [ ] **Install Referrer E2E** — Full deferred-invite proof needs a Play install (Internal testing). Sideload does not populate referrer.
- [ ] **Redeploy mail-service** `/invite/:token` — Open-app then Play Store with `referrer=invite_token%3D...`; redeploy Vercel after pulling mail-service changes.



## Sync, live updates & notifications

Ordered Supabase follow-ups (deletes → conflicts → categories → pin-board boundary → Edge Functions → ops): [docs/supabase-architecture-todos.md](docs/supabase-architecture-todos.md).

- [x] **Group live updates & push** — FCM + Edge Function `notify-group-members` deployed with `FIREBASE_SERVICE_ACCOUNT_JSON`; `notification_prefs` SQL applied; expenses/payments Database Webhooks wired. App: mute prefs, Android 13 permission, tap-to-open. See [docs/fcm-setup.md](docs/fcm-setup.md).
- [x] **A5 — Remote delete tombstones** — Pull prunes local `SYNCED` expenses/payments missing from the remote group (or 1:1) set. See architecture TODO **1**.
- [x] **Conflict policy** — Pull LWW on `updatedAtEpochMs`; never overwrite local `PENDING` / `LOCAL_ONLY` with equal-or-older remote (`SyncConflictPolicy`). See architecture TODO **2**.
- [x] **A6 — Pull-to-refresh** — Won't do. Group ledger stays current via open/resume pull + Realtime (`GroupLiveSync`); gesture removed from group detail.
- [x] **B6 — Activity badges** — Unread badge on the Activity tab from `isSeen`; remote events start unseen; opening then leaving the feed marks them seen.
- [x] **B8 — Notification preferences** — Mute all (Account → Notifications) and mute group (Group settings); synced via `notification_prefs`.
- [x] **Category sync** — Stable default ids (`cat_`*) on the wire; legacy defaults remapped (Room v12). Custom categories remain local-only. See architecture TODO **3**.
- [x] **Add Expense currency picker** — Amount symbol opens `CurrencyPickerDialog` (~30 ISO currencies in `AppCurrencies`). Default is the group `defaultCurrencyCode` (app setting for 1:1).
- [x] **Group totals per currency** — `GroupSpendingCalculator.periodTotalsByCurrency` + Group Totals breakdown rows when a period has more than one code. Headline still uses the group default; mixed banner remains.
- [x] **Expand** `AppCurrencies.OPTIONS` beyond INR and USD (common ~30 ISO 4217 set). Keep `isSupported` / `normalizeOrDefault` / `labelOf` / `filter` working on the expanded list.
- [x] **FX snapshot on add expense** — When the picked currency differs from the group/account default, a live rate (`ExchangeRateCurrencyService` / ExchangeRate-API) or a custom rate is stored with the row. Room and cloud keep the entered `amount` / `currency_code`; `originalAmount`, `originalCurrencyCode`, `rateToDefaultCurrency`, `rateSource` (v15) capture the snapshot. Convert All later rewrites `amount` into the default currency. Balances are not revalued as market rates move.
- [ ] **FX mark-to-market** — No live re-conversion of historical expenses into one display currency.
- [x] **Social PENDING flush** — Groups/members/invites flush in `SyncInteractor` before expenses.
- [x] **Activity events cross-device** — New expense create/update/delete events flush via `SyncInteractor` to `public.activity_events`. Pre-v16 local history stays `LOCAL_ONLY` (not uploaded). SQL: [docs/sql/migration_db.sql](docs/sql/migration_db.sql).
- [x] **Pin board offline cache / server refresh** — Room + flush; load/resume/idle poll fetch Supabase. Unsaved local drafts are not overwritten. No live co-edit (architecture TODO **4** — done).



## Payments & stretch

- [ ] **Payment handles** — UPI VPA / PayPal / Venmo usernames are not stored yet; deep links open apps with amount only.
- [x] ~~**CSV import**~~ — Removed. The Account tab no longer offers bank CSV import (viewer-only equal-split expenses).



## Design / theme

- [x] **TODO(design) — Semantic balance colors** — Brand-permanent `OweRed` / `OwedTeal` (and containers) in `Color.kt`; `SplitEaseColors.YouOwe` / `OwedToYou` are role aliases.
- [x] **Light text-secondary token** — Authored `TextSecondaryLight` (`#585C7E`) in `Color.kt` for `onSurfaceVariant` / `NavyMuted`.
- [x] **Hardcoded colors outside theme** — Moved `CategoryPastels`, `PendingInviteCard`, switch unchecked track/border, shadow colors, and `ErrorContainerDark` into `Color.kt` / `SplitEaseColors`.
- [x] **Migrate** `Se`* **/ screens to** `MaterialTheme.colorScheme` — Made `SplitEaseColors.IconDefault`, `YouOweContainer`, and `OwedToYouContainer` theme-aware, and replaced hardcoded light `Color.White` fills/tints in `GroupSettingsScreen`, `EditContactScreen`, `FriendDetailScreen`, `GroupsScreens`, and `SeAvatarBadge`.
- [x] **Dynamic color settings opt-in** — `dynamicColor = true` parameter is available on `SplitEaseTheme` for a future Settings toggle; default stays brand-fixed (`false`).



## i18n & polish

- [ ] **TODO(i18n-last)** — Restore and expand full translations; locale overlays (`values-de/es/fr/hi/it/ja/pt`) currently fall back to English.
- [ ] **ViewModel string Context extraction** — Groups / Account / Activity partially outstanding.
- [ ] **detekt** — Optional static analysis beyond ktlint (deferred from Phase 0).



## Store / release

- [ ] **TODO(release) — First production / Play release** — Current `versionCode` / sideload APKs are **testing only** (including `1.0.0` build 2). Do not treat them as shipped. When actually cutting a store build: `./gradlew newRelease` (or `scripts/new-release.ps1`), then complete [docs/release-checklist.md](docs/release-checklist.md). History: [RELEASES.md](RELEASES.md).
- [ ] **Feature graphic** — 1024×500 (`docs/store-listing.md`).
- [ ] **Phone screenshots** — >=2 in `docs/screenshots/`.
- [x] **Privacy policy URL** — [https://splitease-server-eight.vercel.app/privacy](https://splitease-server-eight.vercel.app/privacy) (HTML in SplitEase Server `legal/`).
- [x] **Support contact** — `support@splitease.app` / [https://splitease.app](https://splitease.app)
- [ ] Remaining checklist items in [docs/release-checklist.md](docs/release-checklist.md).