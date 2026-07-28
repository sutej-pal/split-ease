-- Phase 4b: allow group members to insert expenses even when someone else paid.
-- Run in Supabase SQL Editor after phase-4-expenses.sql and phase-3d-fix-groups-rls-recursion.sql.
--
-- Symptom fixed: expense saved on device, disappears after re-login (never reached cloud).
-- Uses is_group_member / is_group_creator (SECURITY DEFINER) to avoid RLS recursion 42P17.

drop policy if exists "expenses_insert" on public.expenses;
create policy "expenses_insert"
  on public.expenses for insert to authenticated
  with check (
    paid_by_user_id = auth.uid()
    or (group_id is not null and public.is_group_member(group_id))
    or (group_id is not null and public.is_group_creator(group_id))
  );

drop policy if exists "expense_splits_insert" on public.expense_splits;
create policy "expense_splits_insert"
  on public.expense_splits for insert to authenticated
  with check (
    exists (
      select 1 from public.expenses e
      where e.id = expense_id
        and (
          e.paid_by_user_id = auth.uid()
          or (e.group_id is not null and public.is_group_member(e.group_id))
          or (e.group_id is not null and public.is_group_creator(e.group_id))
        )
    )
  );
