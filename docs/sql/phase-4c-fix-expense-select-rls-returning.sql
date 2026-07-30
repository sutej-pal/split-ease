-- Fix: expense INSERT via PostgREST fails with RLS 42501 when Prefer: return=representation.
--
-- Cause: expenses_select / expenses_update used can_access_expense(id), which re-SELECTs
-- from public.expenses. During INSERT ... RETURNING the in-flight row is not visible to that
-- subquery, so the SELECT policy fails and the whole insert is rolled back.
-- Symptom: expenses save locally as PENDING; public.expenses stays empty. Inserts with
-- Prefer: return=minimal succeed.
--
-- Fix: evaluate access on the current row's columns (and SECURITY DEFINER group helpers)
-- instead of re-querying expenses by id. Keep can_access_expense() for expense_splits
-- (parent row already committed when splits are written).
--
-- Safe to re-run.

drop policy if exists "expenses_select" on public.expenses;
create policy "expenses_select"
  on public.expenses for select to authenticated
  using (
    paid_by_user_id = auth.uid()
    or exists (
      select 1 from public.expense_splits s
      where s.expense_id = expenses.id and s.user_id = auth.uid()
    )
    or (group_id is not null and public.is_group_member(group_id))
    or (group_id is not null and public.is_group_creator(group_id))
  );

drop policy if exists "expenses_update" on public.expenses;
create policy "expenses_update"
  on public.expenses for update to authenticated
  using (
    paid_by_user_id = auth.uid()
    or exists (
      select 1 from public.expense_splits s
      where s.expense_id = expenses.id and s.user_id = auth.uid()
    )
    or (group_id is not null and public.is_group_member(group_id))
    or (group_id is not null and public.is_group_creator(group_id))
  )
  with check (
    paid_by_user_id = auth.uid()
    or exists (
      select 1 from public.expense_splits s
      where s.expense_id = expenses.id and s.user_id = auth.uid()
    )
    or (group_id is not null and public.is_group_member(group_id))
    or (group_id is not null and public.is_group_creator(group_id))
  );

-- Also widen split delete so non-payer group members can replace splits on edit
-- (push path: deleteSplits then upsertSplits).
drop policy if exists "expense_splits_delete" on public.expense_splits;
create policy "expense_splits_delete"
  on public.expense_splits for delete to authenticated
  using (
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
