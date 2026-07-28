-- Fix infinite RLS recursion between public.groups and public.group_members.
-- Cross-table policy checks must go through SECURITY DEFINER helpers so Postgres
-- does not re-enter the other table's RLS (error 42P17).

create or replace function public.is_group_member(p_group_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1
    from public.group_members gm
    where gm.group_id = p_group_id
      and gm.user_id = auth.uid()
  );
$$;

create or replace function public.is_group_creator(p_group_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1
    from public.groups g
    where g.id = p_group_id
      and g.created_by_user_id = auth.uid()
  );
$$;

revoke all on function public.is_group_member(uuid) from public;
revoke all on function public.is_group_creator(uuid) from public;
grant execute on function public.is_group_member(uuid) to authenticated;
grant execute on function public.is_group_creator(uuid) to authenticated;

drop policy if exists "group_members_select" on public.group_members;
drop policy if exists "group_members_insert" on public.group_members;
drop policy if exists "group_members_delete" on public.group_members;

create policy "group_members_select"
  on public.group_members for select to authenticated
  using (
    user_id = auth.uid()
    or public.is_group_creator(group_id)
  );

create policy "group_members_insert"
  on public.group_members for insert to authenticated
  with check (
    auth.uid() = user_id
    or public.is_group_creator(group_id)
  );

create policy "group_members_delete"
  on public.group_members for delete to authenticated
  using (
    user_id = auth.uid()
    or public.is_group_creator(group_id)
  );

drop policy if exists "groups_select" on public.groups;
drop policy if exists "groups_insert" on public.groups;
drop policy if exists "groups_update" on public.groups;
drop policy if exists "groups_delete" on public.groups;

create policy "groups_select"
  on public.groups for select to authenticated
  using (
    created_by_user_id = auth.uid()
    or public.is_group_member(id)
  );

create policy "groups_insert"
  on public.groups for insert to authenticated
  with check (auth.uid() = created_by_user_id);

create policy "groups_update"
  on public.groups for update to authenticated
  using (auth.uid() = created_by_user_id)
  with check (auth.uid() = created_by_user_id);

create policy "groups_delete"
  on public.groups for delete to authenticated
  using (auth.uid() = created_by_user_id);
