-- FX snapshot on expenses (mixed-currency convert)
-- Apply on existing projects after migration_db.sql.
-- Fresh DBs already get this from migration_db.sql (safe to re-run).
--
-- Stores the rate captured when a non-default currency expense was created so
-- Convert Currencies can run after a cloud pull (the snapshot is local-only in Room
-- until these columns exist).

alter table public.expenses
  add column if not exists original_amount text;

alter table public.expenses
  add column if not exists original_currency_code text;

alter table public.expenses
  add column if not exists rate_to_default_currency text;

alter table public.expenses
  add column if not exists rate_source text;
