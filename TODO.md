# SplitEase App — TODO

Consolidated open work from `PROGRESS.md`, phase docs, extras, and in-code `TODO` markers.

## Auth & onboarding

- [ ] **TODO(auth-mobile-onboarding)** — Allow users to onboard with a mobile phone number (SMS OTP / phone auth) in addition to email.
- [x] **OTP ops checklist** — App + mail-service hook + `{{ .Token }}` templates are in place. Live Confirm email / OTP length 6 / SMTP (Brevo) remain a pre-ship check in [docs/release-checklist.md](docs/release-checklist.md). How-to: [docs/maintenance-email-otp-verification.md](docs/maintenance-email-otp-verification.md).
- [x] **Resend domain** — Superseded. Production OTP uses SplitEase Server + Brevo HTTPS (`BREVO_API_KEY` / `MAIL_FROM`), not Resend SMTP.
- [x] **Google Sign-In** — Credential Manager ID token → Supabase (`signInWith(IDToken)`). Ops: [docs/google-sign-in.md](docs/google-sign-in.md).
- [x] **Password reset UX** — In-app 6-digit recovery OTP + set-new-password screen ([phase-12](docs/phase-12-forgot-password-email-otp.md)). Email links are not the primary path.
- [x] **Onboarding-complete cloud flag** — Not needed. The post-signup setup wizard was removed; users go straight to the app after OTP. The unused local `onboarding_complete` preference was dropped.
- [x] **Profile photo in onboarding** — Optional avatar on Sign up (crop + 512px JPEG). Compressed into app storage at signup, uploaded after OTP. Google photos are compressed into `user-avatars` on first hydrate.

## Invites & App Links

- [x] **Invite email delivery** — Add people / Review sends invite mail via mail-service when the contact is an email; phone contacts still fall back to the share sheet.
- [ ] **App Links / invite https** — Host [docs/assetlinks.json](docs/assetlinks.json) at `/.well-known/assetlinks.json` on invite hosts for verified Open-by-default links. See [docs/app-links-setup.md](docs/app-links-setup.md).
- [ ] **Install Referrer E2E** — Full deferred-invite proof needs a Play install (Internal testing). Sideload does not populate referrer.
- [ ] **Redeploy mail-service `/invite/:token`** — Open-app then Play Store with `referrer=invite_token%3D...`; redeploy Vercel after pulling mail-service changes.

## Sync, live updates & notifications

Ordered Supabase follow-ups (deletes → conflicts → categories → pin-board boundary → Edge Functions → ops): [docs/supabase-architecture-todos.md](docs/supabase-architecture-todos.md).

- [x] **Group live updates & push** — FCM + Edge Function `notify-group-members` deployed with `FIREBASE_SERVICE_ACCOUNT_JSON`; `notification_prefs` SQL applied; expenses/payments Database Webhooks wired. App: mute prefs, Android 13 permission, tap-to-open. See [docs/fcm-setup.md](docs/fcm-setup.md).
- [x] **A5 — Remote delete tombstones** — Pull prunes local `SYNCED` expenses/payments missing from the remote group (or 1:1) set. See architecture TODO **1**.
- [x] **Conflict policy** — Pull LWW on `updatedAtEpochMs`; never overwrite local `PENDING` / `LOCAL_ONLY` with equal-or-older remote (`SyncConflictPolicy`). See architecture TODO **2**.
- [x] **A6 — Pull-to-refresh** — Won't do. Group ledger stays current via open/resume pull + Realtime (`GroupLiveSync`); gesture removed from group detail.
- [ ] **B6 — Activity badges** — Extend Activity feed / badge when remote events arrive.
- [x] **B8 — Notification preferences** — Mute all (Settings → Notifications) and mute group (Group settings); synced via `notification_prefs`.
- [x] **Category sync** — Stable default ids (`cat_*`) on the wire; legacy defaults remapped (Room v12). Custom categories remain local-only. See architecture TODO **3**.
- [ ] **TODO(mixed-currency-ux)** — Track and display mixed currencies (no FX). Domain already buckets per `Expense.currencyCode` via `BalanceCalculator.netBalancesByCurrency` / `pairwiseNetByCurrency` — do not change that, and do not add conversion. Spec:
  1. **Add Expense currency picker** — `ExpensesViewModel.currencyCode` is app-wide only; `ExpenseScreens.kt` shows a static symbol. Add a tappable selector next to amount (reuse `AppCurrencies.OPTIONS`, e.g. SignUp / settings picker or a bottom sheet). Default to the group's `defaultCurrencyCode` (app setting only for non-group / friend expenses); write the chosen code onto `Expense.currencyCode`.
  2. **Group totals per currency** — `GroupTotalsViewModel` / `GroupSpendingCalculator.periodTotals` skip non-default currencies. Compute totals for each distinct `currencyCode` in the group; change `GroupTotalsUi` from a single `currencyCode`/`totalSpent` to a list of per-currency totals; render labeled rows or a currency tab like `GroupBalancesScreen` does for `memberNetsByCurrency`.
  3. **Expand `AppCurrencies.OPTIONS`** to a common ~20–30 ISO 4217 set (USD, EUR, GBP, INR, JPY, AUD, CAD, SGD, AED, …). Keep `isSupported` / `normalizeOrDefault` / `labelOf` / `filter` working on the expanded list.
- [ ] **FX rates** — Multi-currency remains per-bucket; live FX / “convert to one currency” is still deferred (Splitwise Pro-style; out of scope for mixed-currency UX above).
- [ ] **Social PENDING flush** — Groups/members flush in `SyncInteractor` before expenses (still verify Supabase SQL is applied).
- [ ] **Activity events cross-device** — Activity events do not sync to other devices.
- [ ] **Pin board offline / realtime** — No offline cache by design; second device sees updates on (re-)open only. Boundary documented (architecture TODO **4** — done).

## Payments & stretch

- [ ] **Payment handles** — UPI VPA / PayPal / Venmo usernames are not stored yet; deep links open apps with amount only.
- [ ] **CSV import** — Creates viewer-only equal-split expenses (not auto-assigned to a group).

## Design / theme

- [ ] **TODO(design) — Semantic balance colors** — Confirm "you owe" / "you're owed" / pending colors before shipping (`Color.kt`, `Theme.kt`, `SplitEaseColors.kt`).
- [ ] **Light text-secondary token** — Interim `#5C5878` for `onSurfaceVariant` / `NavyMuted`; confirm or add a brand muted token.
- [ ] **Hardcoded colors outside theme** — e.g. invite chip in `GroupSettingsScreen.kt`, pastel avatars in `LedgerEntryUi.kt`.
- [ ] **Migrate `Se*` / screens to `MaterialTheme.colorScheme`** — Many components still use light `SplitEaseColors` aliases under dark theme.
- [ ] **Dynamic color settings opt-in** — `dynamicColor = true` available for a future Settings toggle; default stays brand-fixed.

## i18n & polish

- [ ] **TODO(i18n-last)** — Restore and expand full translations; locale overlays (`values-de/es/fr/hi/it/ja/pt`) currently fall back to English.
- [ ] **ViewModel string Context extraction** — Groups / Account / Import / Activity partially outstanding.
- [ ] **detekt** — Optional static analysis beyond ktlint (deferred from Phase 0).

## Store / release

- [ ] **TODO(release) — First production / Play release** — Current `versionCode` / sideload APKs are **testing only** (including `1.0.0` build 2). Do not treat them as shipped. When actually cutting a store build: `./gradlew newRelease` (or `scripts/new-release.ps1`), then complete [docs/release-checklist.md](docs/release-checklist.md). History: [RELEASES.md](RELEASES.md).
- [ ] **Feature graphic** — 1024×500 (`docs/store-listing.md`).
- [ ] **Phone screenshots** — >=2 in `docs/screenshots/`.
- [x] **Privacy policy URL** — https://splitease-server-eight.vercel.app/privacy (HTML in SplitEase Server `legal/`).
- [x] **Support contact** — `support@splitease.app` / https://splitease.app
- [ ] Remaining checklist items in [docs/release-checklist.md](docs/release-checklist.md).

## Ops / SQL (existing projects)

- [ ] **Apply SQL on fresh DB** — Use [docs/sql/migration_db.sql](docs/sql/migration_db.sql) for full setup in one run.
- [ ] **Mail provider** — Production uses Brevo HTTPS via SplitEase Server on Vercel; local dev can use Nodemailer SMTP. See [docs/phase-10-expense-details-onboarding-invite-mail.md](docs/phase-10-expense-details-onboarding-invite-mail.md).
- [ ] **SplitEase Server** — Lives at `C:\splitease\server`; prefer Nodemailer SMTP locally. See [docs/splitease-server-repo.md](docs/splitease-server-repo.md).

## In-code markers

| File                                    | Marker                                             |
| --------------------------------------- | -------------------------------------------------- |
| `presentation/theme/Color.kt`           | `TODO(design)` — semantic balance colors           |
| `presentation/theme/Theme.kt`           | `TODO(design)` — error* placeholders for "you owe" |
| `presentation/theme/SplitEaseColors.kt` | `TODO(design)` — semantic balance colors           |
