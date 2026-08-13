-- Allow ADJUSTMENT split type on expenses (run in Supabase SQL editor)
-- App UI + SplitCalculator already support ADJUSTMENT; remote push was failing
-- because expenses.split_type CHECK omitted it. Also ensures adjustment_amount
-- exists on expense_splits for sync payloads.

do $$
declare
  r record;
begin
  for r in
    select c.conname
    from pg_constraint c
    join pg_class t on c.conrelid = t.oid
    join pg_namespace n on t.relnamespace = n.oid
    where n.nspname = 'public'
      and t.relname = 'expenses'
      and c.contype = 'c'
      and pg_get_constraintdef(c.oid) ilike '%split_type%'
  loop
    execute format('alter table public.expenses drop constraint %I', r.conname);
  end loop;
end $$;

alter table public.expenses
  add constraint expenses_split_type_check
  check (split_type in ('EQUAL', 'UNEQUAL', 'PERCENTAGE', 'SHARES', 'ADJUSTMENT'));

alter table public.expense_splits
  add column if not exists adjustment_amount text;
