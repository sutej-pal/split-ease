# FCM + Realtime setup (Slice 1 + Slice 2)

## Slice 1 — Realtime (live ledger while group is open)

1. Run in Supabase SQL Editor:
   - [`docs/sql/migration_db.sql`](sql/migration_db.sql) (includes realtime publication for `expenses` / `payments`)
2. Install a build that includes `realtime-kt` and open the same group on two devices.
3. Add/edit/delete an expense or payment on device A → device B updates without leaving the screen.

**Cost note:** Supabase Realtime concurrent connections count toward free-tier limits.

## Slice 2 — FCM push (background / closed)

### Firebase

1. Create a Firebase project and add an Android app for:
   - `com.splitease.app`
2. Download `google-services.json` into `app/google-services.json` (gitignored).
   The Google Services Gradle plugin applies automatically when this file exists.
3. Download a Firebase **service account** JSON (Project settings → Service accounts).

### Supabase SQL

Run in order:

1. [`docs/sql/migration_db.sql`](sql/migration_db.sql) (includes `device_tokens` + RLS) if not already applied
2. Optionally [`docs/sql/phase-extras-notify-triggers.sql`](sql/phase-extras-notify-triggers.sql) **or** use Dashboard Database Webhooks (preferred — keeps the service role out of DB settings).

### Edge Function

1. Deploy [`supabase/functions/notify-group-members`](../supabase/functions/notify-group-members/index.ts):

```bash
supabase functions deploy notify-group-members
```

2. Set secrets:

```bash
supabase secrets set FIREBASE_SERVICE_ACCOUNT_JSON="$(cat path/to/service-account.json)"
```

(`SUPABASE_URL` / `SUPABASE_SERVICE_ROLE_KEY` are usually injected automatically.)

### Database Webhook (recommended)

In Supabase Dashboard → Database → Webhooks, create webhooks on `public.expenses` and `public.payments` (INSERT/UPDATE/DELETE) that POST to:

`https://<PROJECT_REF>.supabase.co/functions/v1/notify-group-members`

with header `Authorization: Bearer <SERVICE_ROLE_KEY>` and body including `type`, `table`, `record`, `old_record` (default webhook payload works with the function).

### Android behavior

- On sign-in, the app registers the FCM token into `device_tokens`.
- Incoming pushes show a notification; tap stores `pending_notification_group_id` and opens that group (sync-on-open + Realtime take over).
- Grant **Notifications** permission on Android 13+.

### Verify

1. Two devices signed in as different members of the same group.
2. Background device B; add expense on A → B receives a notification.
3. Tap notification → group detail opens with the new expense.
