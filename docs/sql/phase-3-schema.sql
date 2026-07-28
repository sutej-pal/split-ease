-- Phase 3 schema for SplitEase (run once in Supabase SQL Editor)
-- Apply in order. Safe to re-run for tables that already exist (IF NOT EXISTS),
-- but policies may error if re-created — drop them first if re-applying.
-- If groups/group_members were created before the recursion fix, also run
-- docs/sql/phase-3d-fix-groups-rls-recursion.sql (already applied on this project).

-- 1) Profiles
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  email text not null,
  display_name text not null,
  photo_url text,
  updated_at_epoch_ms bigint not null default 0
);

create unique index if not exists profiles_email_lower_idx
  on public.profiles (lower(email));

alter table public.profiles enable row level security;

drop policy if exists "profiles_select_authenticated" on public.profiles;
drop policy if exists "profiles_insert_own" on public.profiles;
drop policy if exists "profiles_update_own" on public.profiles;

create policy "profiles_select_authenticated"
  on public.profiles for select to authenticated using (true);

create policy "profiles_insert_own"
  on public.profiles for insert to authenticated
  with check (auth.uid() = id);

create policy "profiles_update_own"
  on public.profiles for update to authenticated
  using (auth.uid() = id) with check (auth.uid() = id);

-- 2) Friends
create table if not exists public.friends (
  id uuid primary key,
  owner_user_id uuid not null references auth.users (id) on delete cascade,
  friend_user_id uuid not null,
  email_snapshot text not null,
  display_name_snapshot text not null,
  updated_at_epoch_ms bigint not null default 0,
  unique (owner_user_id, friend_user_id)
);

create index if not exists friends_owner_idx on public.friends (owner_user_id);

alter table public.friends enable row level security;

drop policy if exists "friends_select_own" on public.friends;
drop policy if exists "friends_insert_own" on public.friends;
drop policy if exists "friends_update_own" on public.friends;
drop policy if exists "friends_delete_own" on public.friends;

create policy "friends_select_own"
  on public.friends for select to authenticated
  using (auth.uid() = owner_user_id);

create policy "friends_insert_own"
  on public.friends for insert to authenticated
  with check (auth.uid() = owner_user_id);

create policy "friends_update_own"
  on public.friends for update to authenticated
  using (auth.uid() = owner_user_id) with check (auth.uid() = owner_user_id);

create policy "friends_delete_own"
  on public.friends for delete to authenticated
  using (auth.uid() = owner_user_id);

-- 3) Groups (table first; policies after group_members)
create table if not exists public.groups (
  id uuid primary key,
  name text not null,
  default_currency_code text not null,
  created_by_user_id uuid not null references auth.users (id) on delete cascade,
  updated_at_epoch_ms bigint not null default 0
);

alter table public.groups enable row level security;

-- 4) Group members
create table if not exists public.group_members (
  id uuid primary key,
  group_id uuid not null references public.groups (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  role text not null check (role in ('OWNER', 'MEMBER')),
  joined_at_epoch_ms bigint not null default 0,
  unique (group_id, user_id)
);

create index if not exists group_members_group_idx on public.group_members (group_id);
create index if not exists group_members_user_idx on public.group_members (user_id);

alter table public.group_members enable row level security;

drop policy if exists "group_members_select" on public.group_members;
drop policy if exists "group_members_insert" on public.group_members;
drop policy if exists "group_members_delete" on public.group_members;

create policy "group_members_select"
  on public.group_members for select to authenticated
  using (
    user_id = auth.uid()
    or exists (
      select 1 from public.groups g
      where g.id = group_id and g.created_by_user_id = auth.uid()
    )
  );

create policy "group_members_insert"
  on public.group_members for insert to authenticated
  with check (
    auth.uid() = user_id
    or exists (
      select 1 from public.groups g
      where g.id = group_id and g.created_by_user_id = auth.uid()
    )
  );

create policy "group_members_delete"
  on public.group_members for delete to authenticated
  using (
    auth.uid() = user_id
    or exists (
      select 1 from public.groups g
      where g.id = group_id and g.created_by_user_id = auth.uid()
    )
  );

-- 5) Groups policies (after group_members exists)
drop policy if exists "groups_select" on public.groups;
drop policy if exists "groups_insert" on public.groups;
drop policy if exists "groups_update" on public.groups;
drop policy if exists "groups_delete" on public.groups;

create policy "groups_select"
  on public.groups for select to authenticated
  using (
    created_by_user_id = auth.uid()
    or exists (
      select 1 from public.group_members gm
      where gm.group_id = id and gm.user_id = auth.uid()
    )
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
