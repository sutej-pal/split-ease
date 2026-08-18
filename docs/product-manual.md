# SplitEase — Product and Technical Manual

**App:** SplitEase (`com.splitease.app`)  
**Platform:** Android (Kotlin, Jetpack Compose)  
**Last updated:** 18 August 2026  
**Audience:** product, support, and engineering  

This is the full-app manual. Phase history, SQL, and schema details stay in the linked docs; this file describes **what the product does today** and **how the system is put together**.

Paste this document into [Split ease docs](https://docs.google.com/document/d/1jAQqxrKg3qaMQRoQ4_DIUPceE6XKChrZqxeZNWrky2o/edit?usp=sharing) if you want a Google Docs copy. Cursor cannot write that Doc in place.

---

## 1. What SplitEase is

SplitEase is a native Android app for sharing expenses with friends, roommates, and trip groups. People add costs, choose how to split them, see who owes whom, and record payments when they settle up.

It is **offline-first**: a write is stored on the device immediately, then flushed to **Supabase** when the network is available. Other members see the change after cloud sync. If they have the group open, **Realtime** updates the ledger live. If the app is in the background, they can get an **FCM** notification and tap through to that group.

Inspired by Splitwise’s feature set; architecture and UI are original.

**Package:** `com.splitease.app`  
**Category:** Finance / productivity  
**Support:** support@splitease.app  
**Website:** https://splitease.app  
**Privacy:** https://splitease-server-eight.vercel.app/privacy  
**Terms:** https://splitease-server-eight.vercel.app/terms  

---

## 2. Who it is for

- Friends splitting dinners, tickets, and everyday costs  
- Roommates tracking rent, utilities, and groceries  
- Trip groups pooling travel spend  

It is **not** a bank, payment processor, or accounting suite. UPI / PayPal / Venmo actions open those apps with an amount; SplitEase does not move money itself. There is no live foreign-exchange conversion: each currency stays in its own bucket.

---

## 3. System landscape

SplitEase is three cooperating pieces plus Google’s push network:

```
Android app (Room cache)
        │  Auth + PostgREST + Realtime
        ▼
   Supabase (Postgres, Auth, Storage, Edge Functions)
        │  Database webhooks after expenses / payments land
        ▼
   notify-group-members  ──FCM──►  other members’ devices

Android app  ──HTTPS──►  SplitEase Server (email OTP, invite https, legal pages)
Supabase Auth Send Email hook  ──►  same server
```

| Piece | Role |
| ----- | ---- |
| **Android app** (`C:\splitease\app`) | UI, Room, sync, FCM receive |
| **Supabase** | Accounts, ledger, RLS, Realtime, webhooks |
| **SplitEase Server** (`C:\splitease\server`) | OTP / welcome / recovery mail, `/invite/{token}` App Links bridge, privacy/terms |
| **Firebase Cloud Messaging** | Background notifications after cloud sync |

The Android app never holds the database **service role** key. It uses `SUPABASE_URL` + `SUPABASE_ANON_KEY` from gitignored `local.properties`. Mail uses `MAIL_SERVICE_BASE_URL` + `MAIL_SERVICE_API_KEY`. Optional `GOOGLE_WEB_CLIENT_ID` (Google Cloud Web OAuth client ID) for native Google Sign-In. Firebase client config is `app/google-services.json` (also gitignored).

---

## 4. Core concepts

**Account.** Email + password. Signup and password reset use a 6-digit email OTP. Session is gated: unverified or mid-recovery users do not enter the main app.

**Friend.** A person you track 1:1 costs with. You can invite someone who does not have an account yet; they inherit history when they sign up with the **same email**.

**Group.** A named shared ledger (for example a flat or a trip) with members, a default currency, optional photo/cover, and its own expenses and payments.

**Expense.** A cost: description, amount, currency, date, category, who paid, who participates, and a split method. Unlimited count. Attachments (photos) can be added on the expense detail screen.

**Split.** How the amount is divided: equal, exact amounts, percentages, or shares. Remainder cents are assigned deterministically (`BigDecimal` only — never float).

**Balance.** Derived, not stored. Net of expenses/splits minus recorded payments, per currency. Optional **simplify debts** reduces a group to fewer suggested transfers.

**Payment / settle up.** A recorded transfer between two people that reduces balances. Optional group context. Pay links can open UPI, PayPal, or Venmo with the amount.

**Pin board.** One shared Markdown notepad per group. Online-only; not in Room. Save explicitly.

**Activity.** A local feed of what you did on this device. It is **not** a cloud-synced social feed of other people’s actions. Remote changes arrive via sync, Realtime, or push.

---

## 5. User guide

### 5.1 Create an account and sign in

1. Open SplitEase → **Get started** → **Sign up**.  
2. Enter email, password, display name, and optionally a profile photo.  
3. Enter the 6-digit code from email. Resend if needed.  
4. After verify, the app hydrates a local profile and you land on **Groups**. A welcome email is sent once after signup OTP (not on every login).

**Log in** uses email + password, or **Continue with Google** (same button on Sign up and invite-join). Google skips the email OTP step. First-time Google users still get the one-time welcome email.

**Forgot password?**

1. Request a reset code (recovery email, not the signup template).  
2. Enter the OTP and a new password in-app.  
3. Home stays gated until the new password is set.

Phone-number onboarding (SMS OTP) is not shipped.

Android 13+ may prompt once after sign-in for **Notifications** permission.

### 5.2 Main tabs

| Tab | What you see |
| --- | ------------ |
| **Groups** | Your groups, overall you-owe / you-are-owed, settled groups can be hidden |
| **Friends** | Friend list and 1:1 ledgers |
| **Activity** | Local activity events |
| **Account** | Profile, **Sync**, settings hub, sign out |

Search, spending totals, CSV import, and settings are reached from Account / toolbars as implemented in the current navigation (Settings hub: appearance, currency, language, security, notifications).

### 5.3 Groups

**Create a group** from Groups (create-group action). Set a name, type (friends / home / other), and default currency (from Settings currency unless you change it). Optional group photo and cover.

**Open a group** to see:

- Header and cover  
- Who you owe / who owes you in this group  
- **Settle up**, **Balances**, **Totals**, **Pin Board**  
- Chronological expenses  
- **Add expense**

**Group settings** (gear): rename, photos, default split hint, **mute notifications** for this group, add people, invite via link, leave/delete as allowed by role.

**Add people:** find existing users, add from contacts (permission required), or share an invite link.

**Invite via link:** copy or system share sheet. HTTPS links use the mail-service host when `MAIL_SERVICE_BASE_URL` is set, otherwise `splitease.app`. Custom scheme `splitease://invite/{token}` opens the app without App Links verification. Play Install Referrer can carry `invite_token` for deferred install.

Invitee flow: open link → preview → sign up or log in with the invited email → `accept_invite_by_token` joins the group/friendship and remaps placeholder splits to the real user id.

Automated invite email send is still TODO; sharing is via the system share sheet.

### 5.4 Friends

Add by email or contacts. Pending invited people can already appear on expense participant pickers (soft membership). After they sign up with that email, expenses they were included on show up after sync.

Friend detail is a 1:1 ledger: expenses, add expense, settle up.

### 5.5 Add, edit, and delete expenses

From a group or friend ledger, **Add expense**:

| Field | Notes |
| ----- | ----- |
| With you and | Group (“All of {name}”) or chosen friends |
| Description | Required for a useful ledger line |
| Amount + currency | Currency catalog (100+ ISO codes); default from Settings / group |
| Date and time | Editable |
| Category | Built-in defaults (`cat_general`, `cat_food`, …) plus device-local custom categories |
| Paid by | One member |
| Split | Equally, exact amounts, percent, or shares |
| Recurring | Optional weekly / monthly / yearly template |

**Save** writes Room with status `PENDING`, then sync flushes to Supabase. Other members are notified **after the cloud write succeeds**, not at local save.

Tap an expense for **detail**: comments/photos where implemented, edit, delete. Deleting a synced expense removes it in the cloud (hard delete). Other devices drop that row on the next pull if it was `SYNCED` locally. `PENDING` / `LOCAL_ONLY` rows are never pruned by a pull.

### 5.6 Balances, simplify, and totals

**Balances** shows who owes whom in the group (or 1:1).

**Simplify debts** (per-group preference): when on, the app minimizes the number of suggested transfers. When off, it keeps expense-level pairwise debts.

**Totals** / spending: aggregates by category and period, with charts (Vico) on the spending screens.

No live FX: INR and USD balances do not convert into each other.

### 5.7 Settle up and pay apps

**Settle up** records that A paid B an amount (optional group). That **payment** is applied after expense nets when computing balances.

From settle / remind flows you can open **UPI, PayPal, or Venmo** with the amount. Usernames / VPAs are not stored yet; the deep link carries amount (and region-aware app choice), not a saved handle.

### 5.8 Recurring expenses

Mark an expense as recurring (`WEEKLY` / `MONTHLY` / `YEARLY`). A WorkManager daily job materializes due instances as normal expenses. Editing every recurrence rule (pause, end, skip) is limited compared with a full subscription product.

### 5.9 Pin Board

On group detail, **Pin Board** is a shared Markdown notepad (bold, italic, checklist, gallery image references). All members can read and edit. Content lives only in Supabase `pin_boards`. There is no offline copy and no live collaborative cursor; reopen or save to refresh. Gallery images referenced in Markdown stay on-device.

### 5.10 Activity, search, spending, import

- **Activity:** local `activity_events` (your actions on this device).  
- **Search:** find expenses.  
- **Spending:** category/period totals and charts.  
- **Import:** CSV with header `date, description, amount`, optional `currency`, optional `category`. Dates `yyyy-MM-dd` or `dd/MM/yyyy`.

### 5.11 Settings

Reached from Account → Settings.

| Setting | Behavior |
| ------- | -------- |
| **Appearance** | Light, dark, or system. Brand indigo/amber Material 3; dynamic color off by default |
| **Currency** | Default for new expenses and groups |
| **Language** | Locale preference. Overlays exist (`de`, `es`, `fr`, `hi`, `it`, `ja`, `pt`) but currently fall back to English until i18n is finished |
| **Security** | Biometric / device credential lock; timeout from immediate to 1 hour |
| **Device and push notification settings** | Mute all group updates; OS permission; open system notification settings |
| **Ad privacy choices** | When ads are enabled in the build |

**Group settings → Mute notifications** mutes one group. Prefs sync to `notification_prefs` (last-write-wins).

### 5.12 Notifications (what users should expect)

You are notified when **someone else** in a shared group adds, updates, or deletes an expense or payment — after that change is in Supabase.

Typical copy:

> **Room 1**  
> Ada added an expense “Dinner” · INR 1200

- You do **not** get a push for your own save.  
- Muted-all or muted-group users are skipped.  
- Tap opens **that group’s detail**, then the ledger refreshes from the cloud.  
- While the group screen is open, Realtime applies changes without a notification.

Android channel: `group_updates`.

### 5.13 Offline

You can create and edit groups, expenses, and payments offline. Rows stay `PENDING` until `SyncInteractor.syncForUser` flushes them (login, cold start, Account Sync, group resume). Conflict policy: last-write-wins on `updatedAtEpochMs`. A local `PENDING` / `LOCAL_ONLY` row is never overwritten by an equal-or-older remote snapshot.

Pin Board does not work offline.

---

## 6. How a change reaches other people

This is the path that must stay true for support and ops:

1. User A saves an expense → Room `PENDING`.  
2. Sync upserts to Supabase `expenses` / `expense_splits` (or `payments`).  
3. Database webhook POSTs to Edge Function `notify-group-members` with the service role.  
4. Function loads other members, skips muted users, sends **data-only** FCM (title/body in data).  
5. User B’s app shows a tray notification; tap stores `pending_notification_group_id` and navigates to `group_detail/{groupId}`.  
6. Group open/resume flushes local writes and pulls the latest ledger. If B already has the group open, Realtime (`GroupLiveSync`) refreshes Room without a push.

Do **not** send FCM from the Android client. Do **not** put the ledger in Firestore; Supabase remains the source of truth.

Ops checklist: [fcm-setup.md](fcm-setup.md). Product extras log: [extras-group-live-updates-notifications.md](extras-group-live-updates-notifications.md).

---

## 7. Architecture (engineering)

**Style:** MVVM + clean architecture, single Gradle module `:app`.

```
presentation/   Compose UI, ViewModels, Navigation Compose
domain/         Models, repository interfaces, SplitCalculator, BalanceCalculator
data/           Room, Supabase, DTOs, SyncInteractor, FCM
```

| Concern | Choice |
| ------- | ------ |
| UI | Jetpack Compose, Material 3, `Se*` brand components |
| DI | Hilt |
| Local DB | Room (offline-first) |
| Async | Coroutines + Flow |
| Backend | Supabase Auth + PostgREST + Realtime |
| Push | FCM + Edge Function `notify-group-members` |
| Charts | Vico |
| Work | WorkManager (`RecurringExpenseWorker`) |
| Money | `java.math.BigDecimal` only |
| HTTP | Ktor OkHttp (`SupabaseModule`) |

**IDs:** string UUIDs locally; `remoteId` when synced.  
**Sync bookmarks:** `LOCAL_ONLY` \| `PENDING` \| `SYNCED` + `updatedAtEpochMs`.  
**Remote deletes:** hard delete in cloud; after a successful group/1:1 pull, local `SYNCED` expenses/payments missing from the remote id set are removed.  
**Categories:** default ids `cat_*` on `expenses.category_id`. No `categories` table. Custom categories stay device-local.  
**Activity events:** Room only.  
**Release:** R8 minify + resource shrinking; keep rules in `app/proguard-rules.pro`.

Living detail: [ARCHITECTURE.md](../ARCHITECTURE.md). Schema: [data-dictionary.md](data-dictionary.md). SQL: [sql/migration_db.sql](sql/migration_db.sql).

### 7.1 Feature map (packages)

| Area | Where to look |
| ---- | ------------- |
| Auth / OTP | `AuthRepository`, `presentation/auth` (email OTP + Google ID token), `presentation/onboarding` |
| Invites | `InviteLinks`, `InstallReferrerInviteBootstrap`, `get_invite_preview` / `accept_invite_by_token` |
| Friends & groups | `SocialInteractor`, `presentation/friends`, `presentation/groups` |
| Expenses | `ExpenseInteractor`, `SplitCalculator`, `presentation/expenses` |
| Balances | `BalanceCalculator`, `DebtSimplifier`, `BalanceInteractor` |
| Payments / recurring | `PaymentInteractor`, `RecurrenceScheduler`, `RecurringExpenseWorker` |
| Sync | `SyncInteractor`, `SyncConflictPolicy` |
| Pin Board | `PinBoardInteractor` (not in `SyncInteractor`) |
| Push | `PushTokenRegistrar`, `SplitEaseMessagingService`, `notification_prefs` |
| Settings | `AppSettingsRepository` |

---

## 8. Data model (summary)

Canonical columns: [data-dictionary.md](data-dictionary.md). Cloud schema: `migration_db.sql`.

**Room (high level):** `users`, `friends`, `invites`, `groups`, `group_members`, `expenses`, `expense_splits`, `payments`, `activity_events`, plus related local tables as in the dictionary.

**Supabase (high level):** `profiles`, `groups`, `group_members`, `expenses`, `expense_splits`, `payments`, `invites`, `device_tokens`, `notification_prefs`, `pin_boards`, Auth users. RLS is membership- and own-row-based. Realtime publication covers `expenses` / `payments`.

**Local preferences (SharedPreferences):** theme, locale, currency, biometric lock, auth timeout, mute-all, muted group ids, pending invite token, pending notification group id, simplify-debts per group, welcome-mail flags.

---

## 9. Backend operations

### 9.1 Supabase

1. Apply [sql/migration_db.sql](sql/migration_db.sql) on a fresh project (schema, RLS, RPCs, Realtime, tokens, pin boards, optional notify triggers).  
2. Auth: confirm email **on**; signup and recovery templates must include `{{ .Token }}` (or the Send Email hook must inject the OTP).  
3. Deploy `notify-group-members`; set secret `FIREBASE_SERVICE_ACCOUNT_JSON`.  
4. Database webhooks on `public.expenses` and `public.payments` (INSERT/UPDATE/DELETE) → `https://<PROJECT_REF>.supabase.co/functions/v1/notify-group-members` with `Authorization: Bearer <SERVICE_ROLE_KEY>`.  
5. Older pg_net notify triggers in SQL no-op until `app.settings` are set; prefer Dashboard webhooks so the service role is not stored in DB settings.

Clipboard helper (Windows): `.\scripts\build-supabase-bootstrap-sql.ps1 -CopyToClipboard`

### 9.2 Firebase

- Android app `com.splitease.app` in the Firebase project  
- `google-services.json` in `app/`  
- Service account JSON as the Edge Function secret  

### 9.3 SplitEase Server

Node service: health, privacy/terms, invite bridge, `POST /send-mail`, `POST /supabase/send-email-hook`. Production on Vercel (Brevo HTTPS when `BREVO_API_KEY` is set; local SMTP otherwise). Mail HTML lives in `server/mail-templates/`. See [splitease-server-repo.md](splitease-server-repo.md) and `C:\splitease\server\README.md`.

Point the Android app and the Supabase Send Email hook at this server.

### 9.4 App Links

Host `/.well-known/assetlinks.json` (see [app-links-setup.md](app-links-setup.md) and [assetlinks.json](assetlinks.json)) so https invites open SplitEase by default. Until verified, Android may show a chooser.

---

## 10. Building the app

**Requires:** JDK 17+, Android SDK (compile SDK 37 / target 36), `local.properties` with at least `SUPABASE_URL` and `SUPABASE_ANON_KEY`. `GOOGLE_WEB_CLIENT_ID` is required for Continue with Google.

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew ktlintCheck
./gradlew :app:testDebugUnitTest
```

Windows: `gradlew.bat :app:assembleDebug`

Optional AdMob IDs in `local.properties` for release ads. Debug uses Google test units.

Number a store/testing build: `./gradlew newRelease` (see [RELEASES.md](../RELEASES.md)). Production Play listing assets are still incomplete ([store-listing.md](store-listing.md)).

---

## 11. Security and privacy

- Passwords are handled by Supabase Auth (hashed; not stored in the app).  
- RLS restricts ledger access to members / own rows.  
- Device tokens and notification prefs are per-user.  
- Contacts are read only after permission, for invite/add flows.  
- Biometric lock is local (device credential).  
- Ads (when enabled) go through Google UMP consent.  
- Legal copy: [legal/privacy-policy.md](legal/privacy-policy.md), [legal/terms-of-service.md](legal/terms-of-service.md).

Never commit `local.properties`, `google-services.json`, service-account JSON, or keystore passwords.

---

## 12. Current limitations and carried work

Treat these as honest product caveats, not bugs unless noted:

| Topic | Status |
| ----- | ------ |
| Phone signup / SMS OTP | Not started |
| Full i18n | Locale files exist; UI still English |
| Live FX | Deferred; per-currency buckets only |
| Stored payment handles (UPI VPA, PayPal, Venmo) | Not stored; amount-only deep links |
| Invite email automation | Share sheet only |
| Pin Board offline / live co-edit | Out of scope by design |
| Activity badges for remote events | TODO |
| Play feature graphic / screenshots | TODO |
| Realtime + Edge Function cost | Watch Supabase free-tier connection and invocation limits |

OTP delivery depends on Confirm email, `{{ .Token }}` in templates / hook, and SMTP/Brevo health. See [maintenance-email-otp-verification.md](maintenance-email-otp-verification.md).

---

## 13. Support playbook (short)

**“I added an expense but my roommate didn’t get a notification.”**  
Confirm both are **members of the same cloud group**, roommate is not muted, notifications permission is on, the expense actually synced (Account → Sync; check it appears after reopen), and FCM webhooks/function are deployed. Push fires after **cloud** insert, not local-only save.

**“Tap on the notification opened Groups, not the room.”**  
Fixed in the tap-navigation path: pending group id must navigate before it is cleared; FCM is data-only so the app owns the tap intent. Rebuild if an old APK is installed.

**“We both have a group called Room 1 but they’re different.”**  
Names are not unique. Membership is by group UUID. Invite or add the person to the same group id.

**“Pin Board is empty / won’t save offline.”**  
Expected: online-only.

**“Balances don’t match after a delete.”**  
Confirm both devices pulled; `PENDING` local copies are not deleted by remote prune. Account → Sync, or leave and reopen the group.

**“Reset password email looks like signup.”**  
Recovery template must be deployed on SplitEase Server (dedicated recovery copy).

---

## 14. Glossary

| Term | Meaning |
| ---- | ------- |
| **Room** | On-device SQLite database (Jetpack Room), not a “chat room” |
| **Group / room** | A shared expense group in the product UI |
| **PENDING** | Local write not yet acknowledged by Supabase |
| **SYNCED** | Local row matches a cloud row we have pulled |
| **LWW** | Last-write-wins using `updatedAtEpochMs` |
| **FCM** | Firebase Cloud Messaging |
| **RLS** | Postgres row-level security |
| **OTP** | One-time passcode emailed for signup or recovery |

---

## 15. Related documents

| Doc | Use for |
| --- | ------- |
| [README.md](README.md) (this folder’s index) | Where to look next |
| [PROGRESS.md](../PROGRESS.md) | Phase status and carried TODOs |
| [ARCHITECTURE.md](../ARCHITECTURE.md) | Current implementation truth |
| [ROADMAP.md](ROADMAP.md) | Feature → phase map |
| [data-dictionary.md](data-dictionary.md) | Columns and entities |
| [fcm-setup.md](fcm-setup.md) | Push / Realtime ops |
| [sql/migration_db.sql](sql/migration_db.sql) | Canonical database setup |
| [release-checklist.md](release-checklist.md) | Ship checklist |
| [store-listing.md](store-listing.md) | Play Store copy |
| [design-tokens.md](design-tokens.md) | Color, type, `SeScreen` chrome |
| [app-links-setup.md](app-links-setup.md) | Verified https invites |
| [splitease-server-repo.md](splitease-server-repo.md) | Mail / invite server |
| [CHANGELOG.md](../CHANGELOG.md) | Released changes |
| Phase `phase-0` … `phase-12` | Historical Plan + Outcome |

---

*End of manual.*
