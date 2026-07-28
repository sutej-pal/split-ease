-- Allow any group member to SELECT all co-members (not only self / creator).
-- Depends on public.is_group_member / public.is_group_creator from
-- docs/sql/phase-3d-fix-groups-rls-recursion.sql (SECURITY DEFINER — no RLS recursion).
--
-- Before this fix, members could only see their own group_members row, so non-creators
-- synced a solo member list locally.

drop policy if exists "group_members_select" on public.group_members;

create policy "group_members_select"
  on public.group_members for select to authenticated
  using (
    user_id = auth.uid()
    or public.is_group_creator(group_id)
    or public.is_group_member(group_id)
  );
