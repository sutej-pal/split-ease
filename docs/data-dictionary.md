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

### `groups`

| Column | Type | Nullable | Description |
|---|---|---|---|
| id | TEXT (PK) | no | Local UUID |
| name | TEXT | no | Group display name |
| defaultCurrencyCode | TEXT | no | ISO 4217 default for new expenses |
| createdByUserId | TEXT | no | Creator user id |
| remoteId | TEXT | yes | Cloud id when synced |
| createdAtEpochMs | INTEGER | no | Created-at UTC millis |
| updatedAtEpochMs | INTEGER | no | Last mutation UTC millis |
| syncStatus | TEXT | no | Sync bookmark |

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
| — app tables — | — | — | — | PostgREST app schema starts Phase 3 |
