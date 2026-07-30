# Data Dictionary

Canonical schema reference for Room entities and Firestore collections. Updated whenever a schema changes.

## Room Entities

### `users`

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | TEXT (PK) | no | Local UUID |
| email | TEXT (unique) | no | Login / invite identity |
| displayName | TEXT | no | Display name |
| photoUrl | TEXT | yes | Avatar URL or local path |
| remoteId | TEXT | yes | Firestore doc id when synced |
| createdAtEpochMs | INTEGER | no | Created-at UTC millis |
| updatedAtEpochMs | INTEGER | no | Last local mutation UTC millis |
| syncStatus | TEXT | no | `LOCAL_ONLY` / `PENDING` / `SYNCED` |

### `friends`

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | TEXT (PK) | no | Local UUID |
| ownerUserId | TEXT (FK → users) | no | User who owns this friend row |
| friendUserId | TEXT | no | Other user's id |
| emailSnapshot | TEXT | no | Email captured at add-time |
| displayNameSnapshot | TEXT | no | Name captured at add-time |
| remoteId | TEXT | yes | Cloud id when synced |
| createdAtEpochMs | INTEGER | no | Created-at UTC millis |
| updatedAtEpochMs | INTEGER | no | Last mutation UTC millis |
| syncStatus | TEXT | no | Sync bookmark |

Unique index: `(ownerUserId, friendUserId)`.

### `invites`

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | TEXT (PK) | no | Local/remote UUID |
| token | TEXT (unique) | no | Opaque token in invite URL |
| inviterUserId | TEXT | no | Sender user id |
| email | TEXT | no | Recipient email |
| kind | TEXT | no | `FRIEND` / `GROUP` |
| groupId | TEXT | yes | Target group when kind is GROUP |
| friendRowId | TEXT | yes | Related friendship row |
| status | TEXT | no | `PENDING` / `ACCEPTED` / `CANCELLED` |
| createdAtEpochMs | INTEGER | no | Created-at UTC millis |
| syncStatus | TEXT | no | Sync bookmark |

### `groups`

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | TEXT (PK) | no | Local UUID |
| name | TEXT | no | Group display name |
| defaultCurrencyCode | TEXT | no | ISO 4217 default for new expenses |
| groupType | TEXT | no | `FRIENDS` / `HOME` / `OTHER` (local UI category; Room v3) |
| createdByUserId | TEXT | no | Creator user id |
| remoteId | TEXT | yes | Cloud id when synced |
| createdAtEpochMs | INTEGER | no | Created-at UTC millis |
| updatedAtEpochMs | INTEGER | no | Last mutation UTC millis |
| syncStatus | TEXT | no | Sync bookmark |

**Local preference (not a Room column):** per-group `simplify_debts_{groupId}` in SharedPreferences — when off, balances use expense-level pairwise debts instead of minimized transfers.

**Local Security preferences (SharedPreferences `splitease_settings`):**
- `biometric_lock_enabled` — require biometric / device credential to open the app
- `auth_timeout` — idle grace period before re-auth (`IMMEDIATE`, `FIVE_SECONDS`, …)

**Local Onboarding preferences (SharedPreferences `splitease_settings`):**
- `onboarding_complete` — legacy flag from the removed setup wizard (defaults to `true`; no longer gates navigation)
- `onboarding_email_sent_{userId}` — per-user marker set after welcome email send succeeds
- `pending_invite_token` — opaque invite token from a deep link **or** Play Install Referrer (`invite_token=…`), kept until OTP verify + accept clears it
- `pending_invite_open_target` — group id (or friends sentinel) to open after accept; survives token clear until UI consumes it
- `install_referrer_checked` — one-shot flag; Play Install Referrer is read at most once per install

### `group_members`

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | TEXT (PK) | no | Local UUID |
| groupId | TEXT (FK → groups, CASCADE) | no | Parent group |
| userId | TEXT (FK → users, CASCADE) | no | Member user |
| role | TEXT | no | `OWNER` / `MEMBER` |
| joinedAtEpochMs | INTEGER | no | Join timestamp |
| syncStatus | TEXT | no | Sync bookmark |

Unique index: `(groupId, userId)`.

### `categories`

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | TEXT (PK) | no | Local UUID |
| name | TEXT (unique) | no | Category label |
| iconKey | TEXT | yes | UI icon key |
| isDefault | INTEGER (boolean) | no | Built-in preset flag |
| syncStatus | TEXT | no | Sync bookmark |

### `expenses`

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | TEXT (PK) | no | Local UUID |
| description | TEXT | no | Expense title |
| amount | TEXT | no | `BigDecimal` plain string |
| currencyCode | TEXT | no | ISO 4217 code |
| categoryId | TEXT (FK → categories, SET NULL) | yes | Optional category |
| paidByUserId | TEXT (FK → users, RESTRICT) | no | Payer |
| groupId | TEXT (FK → groups, CASCADE) | yes | Null for direct friend expenses |
| expenseDateEpochMs | INTEGER | no | Business date |
| splitType | TEXT | no | `EQUAL` / `UNEQUAL` / `PERCENTAGE` / `SHARES` |
| isRecurring | INTEGER (boolean) | no | Recurring flag |
| recurrenceFrequency | TEXT | no | `NONE` / `WEEKLY` / `MONTHLY` / `YEARLY` |
| nextOccurrenceEpochMs | INTEGER | yes | Next generate-at for templates (Room v4) |
| recurringTemplateId | TEXT | yes | Parent template id for generated instances |
| notes | TEXT | yes | Free-form note |
| remoteId | TEXT | yes | Cloud id when synced |
| createdAtEpochMs | INTEGER | no | Created-at UTC millis |
| updatedAtEpochMs | INTEGER | no | Last mutation UTC millis |
| syncStatus | TEXT | no | Sync bookmark |

### `expense_splits`
| Column | Type | Nullable | Description |
|---|---|---|---|
| id | TEXT (PK) | no | Local UUID |
| expenseId | TEXT (FK → expenses, CASCADE) | no | Parent expense |
| userId | TEXT (FK → users, CASCADE) | no | Participant |
| owedAmount | TEXT | no | `BigDecimal` plain string |
| percentage | TEXT | yes | Percent when split type is PERCENTAGE |
| shares | INTEGER | yes | Share weight when split type is SHARES |
| syncStatus | TEXT | no | Sync bookmark |

Unique index: `(expenseId, userId)`.

**Derived balances (Phase 5+6):** Not stored. Nets and simplified debts are computed in
`domain.balance` from `expenses` + `expense_splits`, then [Payment] settlements are applied
(`fromUser` +amount, `toUser` −amount) before simplification. Per-currency only (no FX).

### `payments`

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | TEXT (PK) | no | Local UUID |
| fromUserId | TEXT (FK → users, RESTRICT) | no | Payer |
| toUserId | TEXT (FK → users, RESTRICT) | no | Payee |
| amount | TEXT | no | `BigDecimal` plain string |
| currencyCode | TEXT | no | ISO 4217 code |
| groupId | TEXT (FK → groups, CASCADE) | yes | Optional group context |
| note | TEXT | yes | Memo (cash / UPI / etc.) |
| paidAtEpochMs | INTEGER | no | Payment timestamp |
| remoteId | TEXT | yes | Cloud id when synced |
| createdAtEpochMs | INTEGER | no | Created-at UTC millis |
| updatedAtEpochMs | INTEGER | no | Last mutation UTC millis |
| syncStatus | TEXT | no | Sync bookmark |

## Firestore / Supabase remote tables

| Collection / Table | Field | Type | Nullable | Description |
|---|---|---|---|---|
| auth.users (Supabase managed) | id | UUID | no | Auth user id (mirrored to Room `users.id`) |
| auth.users | email | TEXT | yes | Account email |
| profiles | id | UUID (PK) | no | Same as auth user id |
| profiles | email | TEXT | no | Lookup email |
| profiles | display_name | TEXT | no | Display name |
| profiles | photo_url | TEXT | yes | Avatar |
| profiles | phone_country_code | TEXT | yes | Dialing code (e.g. `+91`) |
| profiles | phone_number | TEXT | yes | National phone number |
| profiles | preferred_currency | TEXT | yes | ISO 4217 from signup |
| profiles | updated_at_epoch_ms | BIGINT | no | Last update |
| friends | id | UUID (PK) | no | Friendship id |
| friends | owner_user_id | UUID | no | Owner |
| friends | friend_user_id | UUID | no | Friend user |
| friends | email_snapshot | TEXT | no | Cached email |
| friends | display_name_snapshot | TEXT | no | Cached name |
| friends | updated_at_epoch_ms | BIGINT | no | Last update |
| groups | id | UUID (PK) | no | Group id |
| groups | name | TEXT | no | Group name |
| groups | default_currency_code | TEXT | no | ISO currency |
| groups | created_by_user_id | UUID | no | Creator |
| groups | updated_at_epoch_ms | BIGINT | no | Last update |
| group_members | id | UUID (PK) | no | Membership id |
| group_members | group_id | UUID | no | Parent group |
| group_members | user_id | UUID | no | Member |
| group_members | role | TEXT | no | OWNER / MEMBER |
| group_members | joined_at_epoch_ms | BIGINT | no | Join time |
| device_tokens | id | UUID (PK) | no | Token row id |
| device_tokens | user_id | UUID | no | Owner auth user |
| device_tokens | token | TEXT | no | FCM registration token |
| device_tokens | platform | TEXT | no | e.g. `android` |
| device_tokens | updated_at_epoch_ms | BIGINT | no | Last upsert |
| invites | id | UUID (PK) | no | Invite id |
| invites | token | TEXT | no | Unique invite token |
| invites | inviter_user_id | UUID | no | Sender |
| invites | email | TEXT | no | Recipient email (person invite) or `group-share@splitease.invalid` for generic share links |
| invites | kind | TEXT | no | FRIEND / GROUP |
| invites | group_id | UUID | yes | Target group |
| invites | friend_row_id | UUID | yes | Related friends row |
| invites | status | TEXT | no | PENDING / ACCEPTED / CANCELLED |
| invites | created_at_epoch_ms | BIGINT | no | Created time |

**Invite join RPCs** (see [sql/migration_db.sql](sql/migration_db.sql)):
- `get_invite_preview(p_token)` — public (anon) preview for landing UI
- `accept_invite_by_token(p_token)` — authenticated accept for deep-link join-as-new (share links stay `PENDING` / multi-use; inviter self-claim returns 0)
- `accept_pending_invites()` — email-based accept for person invites only (`friend_row_id` required; skips generic share links)

**Auth lookup RPCs** (anon + authenticated; see [sql/migration_db.sql](sql/migration_db.sql)):
- `auth_email_registered(p_email)` — whether `auth.users` already has that email
- `auth_phone_registered(p_country_code, p_phone)` — whether profiles / auth metadata already use that dial+national number ([sql/phase-auth-phone-registered.sql](sql/phase-auth-phone-registered.sql))

Share-link burn fix: [sql/phase-3f-fix-group-share-invite-burn.sql](sql/phase-3f-fix-group-share-invite-burn.sql)

| expenses | id | UUID (PK) | no | Expense id |
| expenses | description | TEXT | no | Title |
| expenses | amount | TEXT | no | Plain decimal |
| expenses | currency_code | TEXT | no | ISO currency |
| expenses | paid_by_user_id | UUID | no | Payer (may be placeholder until accept) |
| expenses | group_id | UUID | yes | Group or null for 1:1 |
| expenses | expense_date_epoch_ms | BIGINT | no | Business date |
| expenses | split_type | TEXT | no | EQUAL / UNEQUAL / PERCENTAGE / SHARES |
| expenses | updated_at_epoch_ms | BIGINT | no | Last update |
| expense_splits | id | UUID (PK) | no | Split id |
| expense_splits | expense_id | UUID | no | Parent expense |
| expense_splits | user_id | UUID | no | Participant (no auth FK — placeholders allowed) |
| expense_splits | owed_amount | TEXT | no | Plain decimal |
| expense_splits | percentage | TEXT | yes | Optional % |
| expense_splits | shares | INTEGER | yes | Optional shares |
| payments | id | UUID (PK) | no | Settlement id (see [migration_db.sql](sql/migration_db.sql)) |
| payments | from_user_id / to_user_id | UUID | no | Payer / payee |
| payments | amount / currency_code | TEXT | no | Settlement amount |
| payments | group_id | UUID | yes | Optional group context |
| expenses | is_recurring / recurrence_frequency / next_occurrence_epoch_ms / recurring_template_id | mixed | yes/no | Recurring metadata (Phase 6) |

### `pin_boards` (Supabase only — no Room cache)

| Column | Type | Nullable | Description |
|---|---|---|---|
| group_id | UUID (PK, FK → groups.id CASCADE) | no | One board per group |
| content | TEXT | no | Markdown content (default '') |
| updated_by | UUID (FK → auth.users SET NULL) | yes | Last editor |
| updated_at | TIMESTAMPTZ | no | Last edit timestamp |
