# Phase 3 — Friends & Groups

## Phase Goal

Let signed-in users manage friends (add by email) and groups (create/edit, add members), with Room as the offline cache and Supabase PostgREST as the cloud sync target — so data survives app restarts and is visible after coming back online.

## Scope (In / Out)

**In**
- Friend list + add friend by email
- Group list, create/edit group, group detail (members list, empty expenses state)
- Add group members from existing friends
- Local Room persistence (already modeled in Phase 1)
- Supabase tables + PostgREST sync (upsert/pull) with `syncStatus` bookmarks
- Upsert `profiles` row on auth so friends can look each other up by email
- Home hub navigation into Friends / Groups

**Out**
- Expenses inside a group (Phase 4)
- Balances (Phase 5)
- Hardened offline write queue / conflict resolution (Phase 7)
- Google contact import

## Architecture Decisions

| Decision | Rationale |
|---|---|
| Supabase PostgREST instead of Firestore | Matches Phase 2 backend choice |
| Room write-first, then best-effort cloud upsert | Offline-first; pending rows kept if sync fails |
| `profiles` table mirroring auth users | Enables friend lookup by email without exposing `auth.users` |
| SQL schema shipped as `docs/sql/migration_db.sql` | Anon key cannot create tables; apply once in SQL editor |
| `SocialInteractor` in `data/social` | Orchestrates Room + remote without polluting domain |
| Nested signed-in NavHost from Home | Keeps auth graph separate from app graph |

## Data Model Changes

Room schema unchanged (v1).

Supabase public tables (see [migration_db.sql](./sql/migration_db.sql)):

| Table | Purpose |
|---|---|
| `profiles` | Public user mirror for email lookup |
| `friends` | Owner → friend edges |
| `groups` | Shared expense groups |
| `group_members` | Membership + role |

## Files Added/Modified

| File path | Purpose |
|---|---|
| `docs/sql/migration_db.sql` | Supabase DDL + RLS |
| `data/remote/dto/SocialDtos.kt` | PostgREST DTOs |
| `data/remote/SocialRemoteDataSource.kt` | PostgREST calls |
| `data/social/SocialInteractor.kt` | Add friend / create group / refresh |
| `data/di/SupabaseModule.kt` | Install Auth + Postgrest |
| `presentation/friends/*` | Friends list + add |
| `presentation/groups/*` | Groups list / create / detail |
| `presentation/home/HomeScreen.kt` | Hub shortcuts |
| `presentation/navigation/SplitEaseNavHost.kt` | Signed-in nested nav |
| `gradle/libs.versions.toml` | `postgrest-kt` |
| `app/build.gradle.kts` | v0.4.0 + postgrest dep |

## Screens/UI Added

| Screen | Description |
|---|---|
| HomeHub | Friends / Groups shortcuts + sign out |
| FriendsList | List friends; FAB to add |
| AddFriend | Email form to add friend |
| GroupsList | List groups; FAB to create |
| CreateGroup | Name, currency, optional friend members |
| GroupDetail | Edit name/currency, members, add from friends, expenses placeholder |

## How to Test This Phase

### One-time Supabase setup
1. Open Supabase Dashboard → SQL Editor.
2. Paste and run [`docs/sql/migration_db.sql`](./sql/migration_db.sql).
3. Confirm tables `profiles`, `friends`, `groups`, `group_members` exist.

### Manual
1. Install app; sign up user A and user B (two accounts) with Confirm email OFF.
2. As A: Home → Friends → + → enter B’s email → friend appears.
3. Kill app / airplane mode briefly → friend still listed (Room cache).
4. As A: Groups → + → name + currency → optionally check B → Create → detail shows members.
5. Edit group name → Save → reopen after restart → name persists.
6. Go online → data should sync (check Table Editor in Supabase).

### Automated
```bash
.\gradlew.bat :app:assembleDebug
.\gradlew.bat ktlintCheck
```

## Known Issues / TODOs carried forward

- **Must run SQL** before cloud sync works; local Room still works offline without it.
  - Canonical: `docs/sql/migration_db.sql` (profiles, groups, invites + `accept_pending_invites()`, expenses, payments, …)
- Non-users can be invited by email; MVP shares a link via the system share sheet (no automated SMTP yet).
- Invite landing URL `https://splitease.app/invite/{token}` is a placeholder until deep links / web are wired.
- Sync conflicts / offline queue still Phase 7.
- Carried from Phase 2: email confirmation skipped; Google Sign-In stub.
- Capture screenshot into `docs/screenshots/phase-3.png`.

## Screenshots placeholder

![phase-3-screenshot](./screenshots/phase-3.png)

---

## Plan

1. Add `postgrest-kt`, install Postgrest on Supabase client; ship SQL schema.
2. Profile upsert on auth; remote DTOs + sync helpers for friends/groups/members.
3. Friends ViewModel + screens; Groups ViewModel + screens.
4. Signed-in nested navigation from Home.
5. Build/install; document Outcome; stop before Phase 4.

---

## Outcome

**Status:** Done (2026-07-22), revisited for email invites (2026-07-22)

Phase 3 delivered friends and groups UI with Room-first persistence and Supabase PostgREST sync. Home hub navigates to Friends and Groups flows.

**Invite follow-up (v0.4.1):** Adding a friend/group member by email no longer requires an existing SplitEase account. Non-users get a pending friend row + `invites` record; the app opens a share sheet with the invite link. On sign-up/sign-in, `accept_pending_invites()` links the friendship and (for group invites) adds membership.

**Required before cloud sync:** run `docs/sql/migration_db.sql` in the Supabase SQL Editor.

**Next:** Phase 4 — Expense Creation & Splitting Logic.
