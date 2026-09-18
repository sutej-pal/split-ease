# Reset Supabase for a fresh app test

Use this when the user asks to **clear / wipe / reset the server database**, **start with fresh data**, or **re-apply schema after many SQL changes**.

The Android app’s cloud database is **Supabase**, not the Node mail-service in `C:\splitease\server`. Do not wipe Vercel or mail-service data for this request.

Credentials live in gitignored `app/local.properties` (`SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_ACCESS_TOKEN`). Never print those values.

## Agent procedure

From `C:\splitease\app` (PowerShell), run in order. Do not invent a new wipe path.

1. **Wipe rows, storage, and auth users**

```powershell
.\scripts\clear-supabase.ps1
```

2. **Re-apply canonical schema** (required after schema changes, or if the clear script skipped a missing table such as `activity_events`)

```powershell
.\scripts\apply-supabase-schema.ps1
```

3. **Optional seed** — only if the user wants the Admin / Member test accounts instead of a brand-new signup

```powershell
.\scripts\seed-supabase.ps1
```

4. **Tell the user to clear local Room data** before opening the app. Stale PENDING rows on the device will sync back onto the empty server. Uninstall SplitEase, or Settings → Apps → SplitEase → Storage → Clear data.

Keep `-SkipAuthUsers` off unless the user explicitly wants to keep existing logins.

## What the scripts do

| Script | Effect |
| ------ | ------ |
| [`scripts/clear-supabase.ps1`](../scripts/clear-supabase.ps1) | Deletes all rows in public app tables (FK order), empties Storage buckets, deletes `auth.users`. Skips missing tables (HTTP 404). If GoTrue `GET /admin/users` returns 500 (common after anonymized/`deleted-*@deleted.invalid` users), falls back to `delete from auth.users` via the Management API. |
| [`scripts/apply-supabase-schema.ps1`](../scripts/apply-supabase-schema.ps1) | POSTs [`sql/migration_db.sql`](sql/migration_db.sql) to `https://api.supabase.com/v1/projects/{ref}/database/query`. Strips a UTF-8 BOM. Skips any `delete from storage.objects` (Postgres now blocks direct Storage table deletes; use the Storage API instead). Reloads the PostgREST schema cache. |
| [`scripts/seed-supabase.ps1`](../scripts/seed-supabase.ps1) | Creates confirmed Admin + Member auth users, profiles, mutual friendship, and “Seed Group”. |

Clipboard / SQL Editor alternative for schema only:

```powershell
.\scripts\build-supabase-bootstrap-sql.ps1 -CopyToClipboard
```

Then paste into the Supabase SQL Editor. Prefer `apply-supabase-schema.ps1` so the agent does not depend on the dashboard.

## Verify

After a full reset, these should all be **0** and `activity_events` must exist (including snapshot columns):

- `auth.users`
- `public.profiles` / `groups` / `expenses` / `activity_events`

`Content-Range: */0` on `GET /rest/v1/activity_events?select=id&limit=1` with the service role means PostgREST can see the table and it is empty.

## Known traps

- **“Server database”** = this Supabase project (`SUPABASE_URL` in `local.properties`). The mail-service repo has no app ledger.
- **Do not skip local wipe.** Room `fallbackToDestructiveMigration` only helps on schema bumps; a cloud wipe with leftover local rows re-uploads them.
- **Do not `DELETE FROM storage.objects` in SQL.** Use `POST /storage/v1/bucket/{id}/empty` (already in `clear-supabase.ps1`).
- **Do not POST the migration with PowerShell `ConvertTo-Json`.** Large SQL is sent as a nested object and the Management API returns `query: expected string`. Use the Node apply script.
- **GoTrue 500 `Database error finding users`** after account-deletion leftovers: SQL `delete from auth.users` is the fallback already in `clear-supabase.ps1`.
- Add new public tables to the FK-ordered list in `clear-supabase.ps1` when `migration_db.sql` grows.
