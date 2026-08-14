-- SplitEase fresh DB migration (canonical — single file)
-- Apply once in Supabase SQL Editor for a greenfield database.
-- Safe to re-run for most objects (IF NOT EXISTS / CREATE OR REPLACE / DROP POLICY IF EXISTS).
--
-- Includes: profiles/friends/groups, invites, RLS helpers, expenses/splits,
-- expense comments/photos, payments/recurring columns, realtime publication,
-- device_tokens, pin_boards, Storage buckets (avatars, group covers, receipts, pin board),
-- auth_email_registered / auth_phone_registered, reciprocal-friend + remap RPCs,
-- share-link invite heal, optional FCM notify triggers (no-op until app.settings set).
-- Ops: FCM Edge Function + webhooks — see docs/fcm-setup.md

-- ============================================
-- Profiles, friends, groups, members
-- ============================================

create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  email text not null,
  display_name text not null,
  photo_url text,
  phone_country_code text,
  phone_number text,
  preferred_currency text,
  updated_at_epoch_ms bigint not null default 0
);

create unique index if not exists profiles_email_lower_idx
  on public.profiles (lower(email));

alter table public.profiles enable row level security;

-- Existing DBs that created profiles before signup fields:
alter table public.profiles
  add column if not exists phone_country_code text;

alter table public.profiles
  add column if not exists phone_number text;

alter table public.profiles
  add column if not exists preferred_currency text;

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
  updated_at_epoch_ms bigint not null default 0,
  cover_url text,
  photo_url text
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

-- Prefer phase-3d/3e policies (SECURITY DEFINER helpers) on live projects.
-- Greenfield: this select is replaced by phase-3d then phase-3e so co-members
-- can see each other without RLS recursion.
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


-- ============================================
-- Invites
-- ============================================

-- 1) Invites table
create table if not exists public.invites (
  id uuid primary key,
  token text not null unique,
  inviter_user_id uuid not null references auth.users (id) on delete cascade,
  email text not null,
  kind text not null check (kind in ('FRIEND', 'GROUP')),
  group_id uuid references public.groups (id) on delete cascade,
  friend_row_id uuid references public.friends (id) on delete set null,
  status text not null default 'PENDING'
    check (status in ('PENDING', 'ACCEPTED', 'CANCELLED')),
  created_at_epoch_ms bigint not null default 0
);

create index if not exists invites_email_lower_idx on public.invites (lower(email));
create index if not exists invites_inviter_idx on public.invites (inviter_user_id);
create index if not exists invites_token_idx on public.invites (token);

alter table public.invites enable row level security;

drop policy if exists "invites_select" on public.invites;
drop policy if exists "invites_insert_own" on public.invites;
drop policy if exists "invites_update_own" on public.invites;

-- Inviter sees their sent invites; recipient sees invites for their auth email
create policy "invites_select"
  on public.invites for select to authenticated
  using (
    auth.uid() = inviter_user_id
    or lower(email) = lower(coalesce(auth.jwt() ->> 'email', ''))
  );

create policy "invites_insert_own"
  on public.invites for insert to authenticated
  with check (auth.uid() = inviter_user_id);

create policy "invites_update_own"
  on public.invites for update to authenticated
  using (auth.uid() = inviter_user_id)
  with check (auth.uid() = inviter_user_id);

-- 2) Accept pending invites for the signed-in user's email (bypasses friend/member RLS safely)
create or replace function public.accept_pending_invites()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_email text;
  v_name text;
  inv record;
  accepted_count integer := 0;
  now_ms bigint := (extract(epoch from now()) * 1000)::bigint;
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  select lower(u.email),
         coalesce(nullif(u.raw_user_meta_data ->> 'display_name', ''), split_part(u.email, '@', 1))
    into v_email, v_name
  from auth.users u
  where u.id = v_uid;

  if v_email is null then
    return 0;
  end if;

  for inv in
    select * from public.invites
    where lower(email) = v_email
      and status = 'PENDING'
      and friend_row_id is not null
  loop
    update public.friends
    set friend_user_id = v_uid,
        display_name_snapshot = v_name,
        email_snapshot = v_email,
        updated_at_epoch_ms = now_ms
    where id = inv.friend_row_id;

    if inv.kind = 'GROUP' and inv.group_id is not null then
      insert into public.group_members (id, group_id, user_id, role, joined_at_epoch_ms)
      values (gen_random_uuid(), inv.group_id, v_uid, 'MEMBER', now_ms)
      on conflict (group_id, user_id) do nothing;
    end if;

    update public.invites
    set status = 'ACCEPTED'
    where id = inv.id;

    accepted_count := accepted_count + 1;
  end loop;

  return accepted_count;
end;
$$;

grant execute on function public.accept_pending_invites() to authenticated;

-- Invite deep-link preview + accept-by-token
-- (final accept bodies replaced later with expense remap + reciprocal friends)

-- 1) Public preview for invite landing (token is the capability)
create or replace function public.get_invite_preview(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  inv record;
  v_inviter_name text;
  v_group_name text;
  v_group_photo_url text;
  v_members jsonb;
begin
  if p_token is null or length(trim(p_token)) = 0 then
    return null;
  end if;

  select *
    into inv
  from public.invites
  where token = trim(p_token)
    and status = 'PENDING'
  limit 1;

  if not found then
    return null;
  end if;

  select coalesce(nullif(p.display_name, ''), split_part(p.email, '@', 1), 'A friend')
    into v_inviter_name
  from public.profiles p
  where p.id = inv.inviter_user_id;

  if inv.group_id is not null then
    select g.name, g.photo_url
      into v_group_name, v_group_photo_url
    from public.groups g
    where g.id = inv.group_id;

    select coalesce(jsonb_agg(row_data order by sort_name), '[]'::jsonb)
      into v_members
    from (
      select
        lower(coalesce(pr.display_name, pr.email, '')) as sort_name,
        jsonb_build_object(
          'display_name',
          coalesce(nullif(pr.display_name, ''), split_part(pr.email, '@', 1), 'Member'),
          'already_joined',
          true
        ) as row_data
      from public.group_members gm
      join public.profiles pr on pr.id = gm.user_id
      where gm.group_id = inv.group_id

      union all

      select
        lower(coalesce(f.display_name_snapshot, i.email, '')) as sort_name,
        jsonb_build_object(
          'display_name',
          coalesce(
            nullif(replace(f.display_name_snapshot, ' (invited)', ''), ''),
            split_part(i.email, '@', 1),
            'Guest'
          ),
          'already_joined',
          false
        ) as row_data
      from public.invites i
      left join public.friends f on f.id = i.friend_row_id
      where i.group_id = inv.group_id
        and i.status = 'PENDING'
        and i.token <> inv.token
        and i.friend_row_id is not null
    ) members;
  else
    v_members := '[]'::jsonb;
  end if;

  return jsonb_build_object(
    'token', inv.token,
    'kind', inv.kind,
    'email', inv.email,
    'inviter_name', coalesce(v_inviter_name, 'A friend'),
    'group_id', inv.group_id,
    'group_name', v_group_name,
    'group_photo_url', v_group_photo_url,
    'members', coalesce(v_members, '[]'::jsonb)
  );
end;
$$;

grant execute on function public.get_invite_preview(text) to anon, authenticated;

-- 2) Accept a specific invite by token (allows join-as-new with a different email)
create or replace function public.accept_invite_by_token(p_token text)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_email text;
  v_name text;
  inv record;
  old_friend_uid uuid;
  now_ms bigint := (extract(epoch from now()) * 1000)::bigint;
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  if p_token is null or length(trim(p_token)) = 0 then
    return 0;
  end if;

  select lower(u.email),
         coalesce(nullif(u.raw_user_meta_data ->> 'display_name', ''), split_part(u.email, '@', 1))
    into v_email, v_name
  from auth.users u
  where u.id = v_uid;

  if v_email is null then
    return 0;
  end if;

  select *
    into inv
  from public.invites
  where token = trim(p_token)
    and status = 'PENDING'
  for update;

  if not found then
    return 0;
  end if;

  -- Inviter opening their own share link must not consume / no-op join.
  if inv.inviter_user_id = v_uid then
    return 0;
  end if;

  old_friend_uid := null;

  if inv.friend_row_id is not null then
    select friend_user_id into old_friend_uid
    from public.friends
    where id = inv.friend_row_id;

    if old_friend_uid is not null and old_friend_uid <> v_uid then
      update public.expense_splits
      set user_id = v_uid
      where user_id = old_friend_uid;

      update public.expenses
      set paid_by_user_id = v_uid
      where paid_by_user_id = old_friend_uid;

      update public.group_members
      set user_id = v_uid
      where user_id = old_friend_uid
        and not exists (
          select 1 from public.group_members gm2
          where gm2.group_id = group_members.group_id
            and gm2.user_id = v_uid
        );
    end if;

    update public.friends
    set friend_user_id = v_uid,
        display_name_snapshot = v_name,
        email_snapshot = v_email,
        updated_at_epoch_ms = now_ms
    where id = inv.friend_row_id;
  end if;

  if inv.kind = 'GROUP' and inv.group_id is not null then
    insert into public.group_members (id, group_id, user_id, role, joined_at_epoch_ms)
    values (gen_random_uuid(), inv.group_id, v_uid, 'MEMBER', now_ms)
    on conflict (group_id, user_id) do nothing;
  end if;

  -- Person-specific invites are single-use; generic share links stay PENDING.
  if inv.friend_row_id is not null then
    update public.invites
    set status = 'ACCEPTED',
        email = v_email
    where id = inv.id;
  end if;

  return 1;
end;
$$;

grant execute on function public.accept_invite_by_token(text) to authenticated;

-- Heal burned share links (inviter email auto-accept) and normalize placeholder email.
-- No-op on fresh DBs that never used inviter email on share links.
update public.invites
set status = 'PENDING',
    email = 'group-share@splitease.invalid'
where kind = 'GROUP'
  and friend_row_id is null
  and status = 'ACCEPTED';

update public.invites
set email = 'group-share@splitease.invalid'
where kind = 'GROUP'
  and friend_row_id is null
  and status = 'PENDING'
  and email <> 'group-share@splitease.invalid';


-- ============================================
-- Groups RLS helpers (fix recursion 42P17)
-- ============================================
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
    or public.is_group_member(group_id)
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
  using (
    created_by_user_id = auth.uid()
    or public.is_group_member(id)
  )
  with check (
    created_by_user_id = auth.uid()
    or public.is_group_member(id)
  );

create policy "groups_delete"
  on public.groups for delete to authenticated
  using (auth.uid() = created_by_user_id);


-- ============================================
-- Expenses + splits
-- ============================================
-- Placeholder user ids are allowed on splits until invite accept remaps them.

create table if not exists public.expenses (
  id uuid primary key,
  description text not null,
  amount text not null,
  currency_code text not null,
  category_id text,
  paid_by_user_id uuid not null,
  group_id uuid references public.groups (id) on delete cascade,
  expense_date_epoch_ms bigint not null,
  split_type text not null
    check (split_type in ('EQUAL', 'UNEQUAL', 'PERCENTAGE', 'SHARES', 'ADJUSTMENT')),
  notes text,
  updated_at_epoch_ms bigint not null default 0
);

create index if not exists expenses_group_idx on public.expenses (group_id);
create index if not exists expenses_paid_by_idx on public.expenses (paid_by_user_id);

alter table public.expenses enable row level security;

create table if not exists public.expense_splits (
  id uuid primary key,
  expense_id uuid not null references public.expenses (id) on delete cascade,
  user_id uuid not null,
  owed_amount text not null,
  percentage text,
  shares integer,
  paid_amount text,
  adjustment_amount text,
  unique (expense_id, user_id)
);

create index if not exists expense_splits_expense_idx on public.expense_splits (expense_id);
create index if not exists expense_splits_user_idx on public.expense_splits (user_id);

alter table public.expense_splits add column if not exists paid_amount text;
alter table public.expense_splits add column if not exists adjustment_amount text;

alter table public.expense_splits enable row level security;

-- Helper: can the caller see this expense?
create or replace function public.can_access_expense(p_expense_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.expenses e
    where e.id = p_expense_id
      and (
        e.paid_by_user_id = auth.uid()
        or exists (
          select 1 from public.expense_splits s
          where s.expense_id = e.id and s.user_id = auth.uid()
        )
        or (
          e.group_id is not null
          and exists (
            select 1 from public.group_members gm
            where gm.group_id = e.group_id and gm.user_id = auth.uid()
          )
        )
        or (
          e.group_id is not null
          and exists (
            select 1 from public.groups g
            where g.id = e.group_id and g.created_by_user_id = auth.uid()
          )
        )
      )
  );
$$;

drop policy if exists "expenses_select" on public.expenses;
drop policy if exists "expenses_insert" on public.expenses;
drop policy if exists "expenses_update" on public.expenses;
drop policy if exists "expenses_delete" on public.expenses;

-- Inline column checks (not can_access_expense) so INSERT ... RETURNING works.
-- can_access_expense() re-queries expenses by id and cannot see the in-flight row.
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

create policy "expenses_insert"
  on public.expenses for insert to authenticated
  with check (
    paid_by_user_id = auth.uid()
    or (group_id is not null and public.is_group_member(group_id))
    or (group_id is not null and public.is_group_creator(group_id))
  );

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

create policy "expenses_delete"
  on public.expenses for delete to authenticated
  using (
    paid_by_user_id = auth.uid()
    or (
      group_id is not null
      and exists (
        select 1 from public.groups g
        where g.id = group_id and g.created_by_user_id = auth.uid()
      )
    )
  );

drop policy if exists "expense_splits_select" on public.expense_splits;
drop policy if exists "expense_splits_insert" on public.expense_splits;
drop policy if exists "expense_splits_update" on public.expense_splits;
drop policy if exists "expense_splits_delete" on public.expense_splits;

create policy "expense_splits_select"
  on public.expense_splits for select to authenticated
  using (public.can_access_expense(expense_id));

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

create policy "expense_splits_update"
  on public.expense_splits for update to authenticated
  using (public.can_access_expense(expense_id))
  with check (public.can_access_expense(expense_id));

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

-- ============================================
-- Expense comments + receipt photos
-- ============================================
create table if not exists public.expense_comments (
  id uuid primary key,
  expense_id uuid not null references public.expenses (id) on delete cascade,
  author_user_id uuid not null,
  body text not null,
  kind text not null check (kind in ('USER', 'SYSTEM')),
  created_at_epoch_ms bigint not null
);

create index if not exists expense_comments_expense_idx
  on public.expense_comments (expense_id);
create index if not exists expense_comments_created_idx
  on public.expense_comments (created_at_epoch_ms);

alter table public.expense_comments enable row level security;

drop policy if exists "expense_comments_select" on public.expense_comments;
drop policy if exists "expense_comments_insert" on public.expense_comments;
drop policy if exists "expense_comments_delete" on public.expense_comments;

create policy "expense_comments_select"
  on public.expense_comments for select to authenticated
  using (public.can_access_expense(expense_id));

create policy "expense_comments_insert"
  on public.expense_comments for insert to authenticated
  with check (
    author_user_id = auth.uid()
    and public.can_access_expense(expense_id)
  );

create policy "expense_comments_delete"
  on public.expense_comments for delete to authenticated
  using (
    author_user_id = auth.uid()
    or public.can_access_expense(expense_id)
  );

create table if not exists public.expense_photos (
  id uuid primary key,
  expense_id uuid not null references public.expenses (id) on delete cascade,
  created_by_user_id uuid not null,
  remote_url text,
  created_at_epoch_ms bigint not null
);

create index if not exists expense_photos_expense_idx
  on public.expense_photos (expense_id);
create index if not exists expense_photos_created_idx
  on public.expense_photos (created_at_epoch_ms);

alter table public.expense_photos enable row level security;

drop policy if exists "expense_photos_select" on public.expense_photos;
drop policy if exists "expense_photos_insert" on public.expense_photos;
drop policy if exists "expense_photos_update" on public.expense_photos;
drop policy if exists "expense_photos_delete" on public.expense_photos;

create policy "expense_photos_select"
  on public.expense_photos for select to authenticated
  using (public.can_access_expense(expense_id));

create policy "expense_photos_insert"
  on public.expense_photos for insert to authenticated
  with check (
    created_by_user_id = auth.uid()
    and public.can_access_expense(expense_id)
  );

create policy "expense_photos_update"
  on public.expense_photos for update to authenticated
  using (
    created_by_user_id = auth.uid()
    and public.can_access_expense(expense_id)
  )
  with check (
    created_by_user_id = auth.uid()
    and public.can_access_expense(expense_id)
  );

create policy "expense_photos_delete"
  on public.expense_photos for delete to authenticated
  using (
    created_by_user_id = auth.uid()
    or public.can_access_expense(expense_id)
  );

insert into storage.buckets (id, name, public)
values ('expense-receipts', 'expense-receipts', true)
on conflict (id) do update set public = excluded.public;

drop policy if exists "expense_receipts_select" on storage.objects;
drop policy if exists "expense_receipts_insert" on storage.objects;
drop policy if exists "expense_receipts_update" on storage.objects;
drop policy if exists "expense_receipts_delete" on storage.objects;

-- Path: {expenseId}/{photoId}.jpg — folder name is the expense UUID.
-- Public bucket: receipt HTTP URLs are fetched outside the Supabase client. Allow select
-- without a user JWT when the URL is known (object keys are unguessable UUID paths).
create policy "expense_receipts_select"
  on storage.objects for select
  using (bucket_id = 'expense-receipts');

create policy "expense_receipts_insert"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'expense-receipts'
    and public.can_access_expense((storage.foldername(name))[1]::uuid)
  );

create policy "expense_receipts_update"
  on storage.objects for update to authenticated
  using (
    bucket_id = 'expense-receipts'
    and public.can_access_expense((storage.foldername(name))[1]::uuid)
  )
  with check (
    bucket_id = 'expense-receipts'
    and public.can_access_expense((storage.foldername(name))[1]::uuid)
  );

create policy "expense_receipts_delete"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'expense-receipts'
    and public.can_access_expense((storage.foldername(name))[1]::uuid)
  );

-- Replace accept RPC to also remap expense payer/splits from placeholder â†’ real user
create or replace function public.accept_pending_invites()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_email text;
  v_name text;
  inv record;
  old_friend_uid uuid;
  accepted_count integer := 0;
  now_ms bigint := (extract(epoch from now()) * 1000)::bigint;
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  select lower(u.email),
         coalesce(nullif(u.raw_user_meta_data ->> 'display_name', ''), split_part(u.email, '@', 1))
    into v_email, v_name
  from auth.users u
  where u.id = v_uid;

  if v_email is null then
    return 0;
  end if;

  for inv in
    select * from public.invites
    where lower(email) = v_email
      and status = 'PENDING'
      and friend_row_id is not null
  loop
    old_friend_uid := null;

    select friend_user_id into old_friend_uid
    from public.friends
    where id = inv.friend_row_id;

    if old_friend_uid is not null and old_friend_uid <> v_uid then
      update public.expense_splits
      set user_id = v_uid
      where user_id = old_friend_uid;

      update public.expenses
      set paid_by_user_id = v_uid
      where paid_by_user_id = old_friend_uid;

      update public.group_members
      set user_id = v_uid
      where user_id = old_friend_uid
        and not exists (
          select 1 from public.group_members gm2
          where gm2.group_id = group_members.group_id
            and gm2.user_id = v_uid
        );
    end if;

    update public.friends
    set friend_user_id = v_uid,
        display_name_snapshot = v_name,
        email_snapshot = v_email,
        updated_at_epoch_ms = now_ms
    where id = inv.friend_row_id;

    if inv.kind = 'GROUP' and inv.group_id is not null then
      insert into public.group_members (id, group_id, user_id, role, joined_at_epoch_ms)
      values (gen_random_uuid(), inv.group_id, v_uid, 'MEMBER', now_ms)
      on conflict (group_id, user_id) do nothing;
    end if;

    update public.invites
    set status = 'ACCEPTED'
    where id = inv.id;

    accepted_count := accepted_count + 1;
  end loop;

  return accepted_count;
end;
$$;

grant execute on function public.accept_pending_invites() to authenticated;
grant execute on function public.can_access_expense(uuid) to authenticated;


-- ============================================
-- Payments + recurring columns
-- ============================================

create table if not exists public.payments (
  id uuid primary key,
  from_user_id uuid not null,
  to_user_id uuid not null,
  amount text not null,
  currency_code text not null,
  group_id uuid references public.groups (id) on delete cascade,
  note text,
  paid_at_epoch_ms bigint not null,
  updated_at_epoch_ms bigint not null default 0,
  check (from_user_id <> to_user_id)
);

create index if not exists payments_from_idx on public.payments (from_user_id);
create index if not exists payments_to_idx on public.payments (to_user_id);
create index if not exists payments_group_idx on public.payments (group_id);
create index if not exists payments_paid_at_idx on public.payments (paid_at_epoch_ms);

alter table public.payments enable row level security;

create or replace function public.can_access_payment(p_payment_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.payments p
    where p.id = p_payment_id
      and (
        p.from_user_id = auth.uid()
        or p.to_user_id = auth.uid()
        or (
          p.group_id is not null
          and exists (
            select 1 from public.group_members gm
            where gm.group_id = p.group_id and gm.user_id = auth.uid()
          )
        )
      )
  );
$$;

drop policy if exists "payments_select" on public.payments;
drop policy if exists "payments_insert" on public.payments;
drop policy if exists "payments_update" on public.payments;
drop policy if exists "payments_delete" on public.payments;

create policy "payments_select"
  on public.payments for select to authenticated
  using (public.can_access_payment(id));

create policy "payments_insert"
  on public.payments for insert to authenticated
  with check (
    from_user_id = auth.uid() or to_user_id = auth.uid()
  );

create policy "payments_update"
  on public.payments for update to authenticated
  using (from_user_id = auth.uid() or to_user_id = auth.uid())
  with check (from_user_id = auth.uid() or to_user_id = auth.uid());

create policy "payments_delete"
  on public.payments for delete to authenticated
  using (from_user_id = auth.uid() or to_user_id = auth.uid());

-- Recurring metadata on expenses (optional; local Room is source of truth for MVP)
alter table public.expenses
  add column if not exists is_recurring boolean not null default false;

alter table public.expenses
  add column if not exists recurrence_frequency text not null default 'NONE';

alter table public.expenses
  add column if not exists next_occurrence_epoch_ms bigint;

alter table public.expenses
  add column if not exists recurring_template_id uuid references public.expenses (id) on delete set null;


-- ============================================
-- Realtime (group ledger)
-- ============================================
-- Safe to re-run: ignores tables already in the publication.

do $$
begin
  begin
    alter publication supabase_realtime add table public.expenses;
  exception
    when duplicate_object then null;
  end;
  begin
    alter publication supabase_realtime add table public.payments;
  exception
    when duplicate_object then null;
  end;
end $$;

-- DELETE / UPDATE payloads need old row fields (group_id) for filtered channels.
alter table public.expenses replica identity full;
alter table public.payments replica identity full;


-- ============================================
-- Device push tokens
-- ============================================
-- Ops: deploy supabase/functions/notify-group-members and wire Database Webhooks
-- (or pg_net notify triggers at end of this file) — see docs/fcm-setup.md.

create table if not exists public.device_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  token text not null,
  platform text not null default 'android',
  updated_at_epoch_ms bigint not null default 0,
  unique (user_id, token)
);

create index if not exists device_tokens_user_idx on public.device_tokens (user_id);

alter table public.device_tokens enable row level security;

drop policy if exists "device_tokens_select_own" on public.device_tokens;
drop policy if exists "device_tokens_insert_own" on public.device_tokens;
drop policy if exists "device_tokens_update_own" on public.device_tokens;
drop policy if exists "device_tokens_delete_own" on public.device_tokens;

create policy "device_tokens_select_own"
  on public.device_tokens for select to authenticated
  using (auth.uid() = user_id);

create policy "device_tokens_insert_own"
  on public.device_tokens for insert to authenticated
  with check (auth.uid() = user_id);

create policy "device_tokens_update_own"
  on public.device_tokens for update to authenticated
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "device_tokens_delete_own"
  on public.device_tokens for delete to authenticated
  using (auth.uid() = user_id);

-- Service role / Edge Function reads all tokens; no extra policy needed for service_role.


-- ============================================
-- Pin boards
-- ============================================
create table if not exists public.pin_boards (
  group_id uuid primary key references public.groups (id) on delete cascade,
  content  text not null default '',
  updated_by uuid references auth.users (id) on delete set null,
  updated_at timestamptz not null default now()
);

alter table public.pin_boards enable row level security;

drop policy if exists "pin_boards_select_member" on public.pin_boards;
drop policy if exists "pin_boards_insert_member" on public.pin_boards;
drop policy if exists "pin_boards_update_member" on public.pin_boards;

create policy "pin_boards_select_member"
  on public.pin_boards for select to authenticated
  using (
    exists (
      select 1 from public.group_members gm
      where gm.group_id = pin_boards.group_id and gm.user_id = auth.uid()
    )
  );

create policy "pin_boards_insert_member"
  on public.pin_boards for insert to authenticated
  with check (
    exists (
      select 1 from public.group_members gm
      where gm.group_id = pin_boards.group_id and gm.user_id = auth.uid()
    )
  );

create policy "pin_boards_update_member"
  on public.pin_boards for update to authenticated
  using (
    exists (
      select 1 from public.group_members gm
      where gm.group_id = pin_boards.group_id and gm.user_id = auth.uid()
    )
  )
  with check (
    exists (
      select 1 from public.group_members gm
      where gm.group_id = pin_boards.group_id and gm.user_id = auth.uid()
    )
  );

-- ============================================
-- Storage: profile avatars, group covers/photos, pin board images
-- ============================================
-- profiles.photo_url + groups.cover_url / groups.photo_url columns are above.
-- group-covers bucket holds cover.jpg and photo.jpg per group id.

insert into storage.buckets (id, name, public)
values ('user-avatars', 'user-avatars', true)
on conflict (id) do update set public = excluded.public;

drop policy if exists "user_avatars_select" on storage.objects;
drop policy if exists "user_avatars_insert" on storage.objects;
drop policy if exists "user_avatars_update" on storage.objects;
drop policy if exists "user_avatars_delete" on storage.objects;

-- Public bucket: avatar HTTP URLs are fetched without a user JWT (Image decode / HttpURLConnection).
create policy "user_avatars_select"
  on storage.objects for select
  using (bucket_id = 'user-avatars');

create policy "user_avatars_insert"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'user-avatars'
    and (storage.foldername(name))[1]::uuid = auth.uid()
  );

create policy "user_avatars_update"
  on storage.objects for update to authenticated
  using (
    bucket_id = 'user-avatars'
    and (storage.foldername(name))[1]::uuid = auth.uid()
  )
  with check (
    bucket_id = 'user-avatars'
    and (storage.foldername(name))[1]::uuid = auth.uid()
  );

create policy "user_avatars_delete"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'user-avatars'
    and (storage.foldername(name))[1]::uuid = auth.uid()
  );

insert into storage.buckets (id, name, public)
values ('group-covers', 'group-covers', true)
on conflict (id) do update set public = excluded.public;

drop policy if exists "group_covers_select" on storage.objects;
drop policy if exists "group_covers_insert" on storage.objects;
drop policy if exists "group_covers_update" on storage.objects;
drop policy if exists "group_covers_delete" on storage.objects;

create policy "group_covers_select"
  on storage.objects for select to authenticated
  using (bucket_id = 'group-covers');

create policy "group_covers_insert"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'group-covers'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );

create policy "group_covers_update"
  on storage.objects for update to authenticated
  using (
    bucket_id = 'group-covers'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  )
  with check (
    bucket_id = 'group-covers'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );

create policy "group_covers_delete"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'group-covers'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );

insert into storage.buckets (id, name, public)
values ('pin-board-images', 'pin-board-images', true)
on conflict (id) do update set public = excluded.public;

drop policy if exists "pin_board_images_select" on storage.objects;
drop policy if exists "pin_board_images_insert" on storage.objects;
drop policy if exists "pin_board_images_update" on storage.objects;
drop policy if exists "pin_board_images_delete" on storage.objects;

create policy "pin_board_images_select"
  on storage.objects for select to authenticated
  using (bucket_id = 'pin-board-images');

create policy "pin_board_images_insert"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'pin-board-images'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );

create policy "pin_board_images_update"
  on storage.objects for update to authenticated
  using (
    bucket_id = 'pin-board-images'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  )
  with check (
    bucket_id = 'pin-board-images'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );

create policy "pin_board_images_delete"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'pin-board-images'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );

-- ============================================
-- Auth lookup RPCs (signup duplicate checks)
-- ============================================
create or replace function public.auth_email_registered(p_email text)
returns boolean
language sql
security definer
set search_path = public, auth
stable
as $$
  select exists (
    select 1
    from auth.users u
    where lower(u.email) = lower(trim(p_email))
  );
$$;

revoke all on function public.auth_email_registered(text) from public;
grant execute on function public.auth_email_registered(text) to anon, authenticated;

-- BEGIN: auth_phone_registered (signup duplicate phone check)
create or replace function public.auth_phone_registered(
  p_country_code text,
  p_phone text
)
returns boolean
language sql
security definer
set search_path = public, auth
stable
as $$
  with normalized as (
    select
      coalesce(nullif(trim(p_country_code), ''), '+91') as dial,
      nullif(regexp_replace(coalesce(p_phone, ''), '\D', '', 'g'), '') as digits
  )
  select
    case
      when (select digits from normalized) is null then false
      else exists (
        select 1
        from public.profiles p, normalized n
        where nullif(regexp_replace(coalesce(p.phone_number, ''), '\D', '', 'g'), '') = n.digits
          and coalesce(nullif(trim(p.phone_country_code), ''), '+91') = n.dial
      )
      or exists (
        select 1
        from auth.users u, normalized n
        where nullif(
              regexp_replace(coalesce(u.raw_user_meta_data->>'phone_number', ''), '\D', '', 'g'),
              ''
            ) = n.digits
          and coalesce(
            nullif(trim(u.raw_user_meta_data->>'phone_country_code'), ''),
            '+91'
          ) = n.dial
      )
    end;
$$;

revoke all on function public.auth_phone_registered(text, text) from public;
grant execute on function public.auth_phone_registered(text, text) to anon, authenticated;


-- ============================================
-- Reciprocal friends + placeholder expense remap
-- ============================================

create or replace function public.remap_placeholder_user(p_from uuid, p_to uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  if p_from is null or p_to is null or p_from = p_to then
    return;
  end if;

  if not exists (
    select 1
    from public.friends f
    where f.owner_user_id = auth.uid()
      and f.friend_user_id in (p_from, p_to)
  ) then
    raise exception 'Not allowed to remap these users';
  end if;

  update public.expense_splits
  set user_id = p_to
  where user_id = p_from
    and not exists (
      select 1
      from public.expense_splits s2
      where s2.expense_id = expense_splits.expense_id
        and s2.user_id = p_to
    );

  delete from public.expense_splits
  where user_id = p_from;

  update public.expenses
  set paid_by_user_id = p_to
  where paid_by_user_id = p_from;

  update public.group_members
  set user_id = p_to
  where user_id = p_from
    and not exists (
      select 1
      from public.group_members gm2
      where gm2.group_id = group_members.group_id
        and gm2.user_id = p_to
    );

  delete from public.group_members
  where user_id = p_from;

  update public.payments
  set from_user_id = p_to
  where from_user_id = p_from;

  update public.payments
  set to_user_id = p_to
  where to_user_id = p_from;

  update public.friends
  set friend_user_id = p_to,
      updated_at_epoch_ms = (extract(epoch from now()) * 1000)::bigint
  where owner_user_id = auth.uid()
    and friend_user_id = p_from
    and not exists (
      select 1
      from public.friends f2
      where f2.owner_user_id = auth.uid()
        and f2.friend_user_id = p_to
    );
end;
$$;

grant execute on function public.remap_placeholder_user(uuid, uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Ensure Bâ†’A friendship exists when Aâ†’B is created (security definer so the
-- inviter can insert a row owned by the invitee).
-- ---------------------------------------------------------------------------
create or replace function public.ensure_reciprocal_friend(
  p_owner_user_id uuid,
  p_friend_user_id uuid,
  p_email text,
  p_display_name text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  now_ms bigint := (extract(epoch from now()) * 1000)::bigint;
  v_email text := coalesce(nullif(trim(p_email), ''), '');
  v_name text := coalesce(nullif(trim(p_display_name), ''), split_part(coalesce(p_email, 'Friend'), '@', 1));
begin
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  if p_owner_user_id is null or p_friend_user_id is null then
    return;
  end if;

  if p_owner_user_id = p_friend_user_id then
    return;
  end if;

  -- Either party may ensure the reverse edge.
  if auth.uid() not in (p_owner_user_id, p_friend_user_id) then
    raise exception 'Not allowed';
  end if;

  if v_email = '' then
    select coalesce(nullif(email, ''), v_email) into v_email
    from public.profiles
    where id = p_friend_user_id;
  end if;

  if v_name = '' or v_name = 'Friend' then
    select coalesce(nullif(display_name, ''), v_name) into v_name
    from public.profiles
    where id = p_friend_user_id;
  end if;

  insert into public.friends (
    id,
    owner_user_id,
    friend_user_id,
    email_snapshot,
    display_name_snapshot,
    updated_at_epoch_ms
  )
  values (
    gen_random_uuid(),
    p_owner_user_id,
    p_friend_user_id,
    coalesce(nullif(v_email, ''), p_friend_user_id::text),
    coalesce(nullif(v_name, ''), 'Friend'),
    now_ms
  )
  on conflict (owner_user_id, friend_user_id) do update
  set email_snapshot = excluded.email_snapshot,
      display_name_snapshot = excluded.display_name_snapshot,
      updated_at_epoch_ms = excluded.updated_at_epoch_ms;
end;
$$;

grant execute on function public.ensure_reciprocal_friend(uuid, uuid, text, text) to authenticated;

-- ---------------------------------------------------------------------------
-- Accept RPCs: remap expenses + create reciprocal friendship.
-- ---------------------------------------------------------------------------
create or replace function public.accept_pending_invites()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_email text;
  v_name text;
  inv record;
  old_friend_uid uuid;
  inviter_email text;
  inviter_name text;
  accepted_count integer := 0;
  now_ms bigint := (extract(epoch from now()) * 1000)::bigint;
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  select lower(u.email),
         coalesce(nullif(u.raw_user_meta_data ->> 'display_name', ''), split_part(u.email, '@', 1))
    into v_email, v_name
  from auth.users u
  where u.id = v_uid;

  if v_email is null then
    return 0;
  end if;

  for inv in
    select * from public.invites
    where lower(email) = v_email
      and status = 'PENDING'
      and friend_row_id is not null
  loop
    old_friend_uid := null;

    select friend_user_id into old_friend_uid
    from public.friends
    where id = inv.friend_row_id;

    if old_friend_uid is not null and old_friend_uid <> v_uid then
      update public.expense_splits
      set user_id = v_uid
      where user_id = old_friend_uid
        and not exists (
          select 1 from public.expense_splits s2
          where s2.expense_id = expense_splits.expense_id
            and s2.user_id = v_uid
        );

      delete from public.expense_splits
      where user_id = old_friend_uid;

      update public.expenses
      set paid_by_user_id = v_uid
      where paid_by_user_id = old_friend_uid;

      update public.group_members
      set user_id = v_uid
      where user_id = old_friend_uid
        and not exists (
          select 1 from public.group_members gm2
          where gm2.group_id = group_members.group_id
            and gm2.user_id = v_uid
        );

      delete from public.group_members
      where user_id = old_friend_uid;

      update public.payments
      set from_user_id = v_uid
      where from_user_id = old_friend_uid;

      update public.payments
      set to_user_id = v_uid
      where to_user_id = old_friend_uid;
    end if;

    update public.friends
    set friend_user_id = v_uid,
        display_name_snapshot = v_name,
        email_snapshot = v_email,
        updated_at_epoch_ms = now_ms
    where id = inv.friend_row_id;

    if inv.kind = 'GROUP' and inv.group_id is not null then
      insert into public.group_members (id, group_id, user_id, role, joined_at_epoch_ms)
      values (gen_random_uuid(), inv.group_id, v_uid, 'MEMBER', now_ms)
      on conflict (group_id, user_id) do nothing;
    end if;

    -- Reciprocal: invitee owns a friendship pointing at the inviter.
    select coalesce(p.email, ''), coalesce(p.display_name, 'Friend')
      into inviter_email, inviter_name
    from public.profiles p
    where p.id = inv.inviter_user_id;

    insert into public.friends (
      id, owner_user_id, friend_user_id, email_snapshot, display_name_snapshot, updated_at_epoch_ms
    )
    values (
      gen_random_uuid(),
      v_uid,
      inv.inviter_user_id,
      coalesce(nullif(inviter_email, ''), inv.inviter_user_id::text),
      coalesce(nullif(inviter_name, ''), 'Friend'),
      now_ms
    )
    on conflict (owner_user_id, friend_user_id) do update
    set email_snapshot = excluded.email_snapshot,
        display_name_snapshot = excluded.display_name_snapshot,
        updated_at_epoch_ms = excluded.updated_at_epoch_ms;

    update public.invites
    set status = 'ACCEPTED'
    where id = inv.id;

    accepted_count := accepted_count + 1;
  end loop;

  return accepted_count;
end;
$$;

grant execute on function public.accept_pending_invites() to authenticated;

create or replace function public.accept_invite_by_token(p_token text)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_email text;
  v_name text;
  inv record;
  old_friend_uid uuid;
  inviter_email text;
  inviter_name text;
  now_ms bigint := (extract(epoch from now()) * 1000)::bigint;
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  if p_token is null or length(trim(p_token)) = 0 then
    return 0;
  end if;

  select lower(u.email),
         coalesce(nullif(u.raw_user_meta_data ->> 'display_name', ''), split_part(u.email, '@', 1))
    into v_email, v_name
  from auth.users u
  where u.id = v_uid;

  if v_email is null then
    return 0;
  end if;

  select *
    into inv
  from public.invites
  where token = trim(p_token)
    and status = 'PENDING'
  for update;

  if not found then
    return 0;
  end if;

  if inv.inviter_user_id = v_uid then
    return 0;
  end if;

  old_friend_uid := null;

  if inv.friend_row_id is not null then
    select friend_user_id into old_friend_uid
    from public.friends
    where id = inv.friend_row_id;

    if old_friend_uid is not null and old_friend_uid <> v_uid then
      update public.expense_splits
      set user_id = v_uid
      where user_id = old_friend_uid
        and not exists (
          select 1 from public.expense_splits s2
          where s2.expense_id = expense_splits.expense_id
            and s2.user_id = v_uid
        );

      delete from public.expense_splits
      where user_id = old_friend_uid;

      update public.expenses
      set paid_by_user_id = v_uid
      where paid_by_user_id = old_friend_uid;

      update public.group_members
      set user_id = v_uid
      where user_id = old_friend_uid
        and not exists (
          select 1 from public.group_members gm2
          where gm2.group_id = group_members.group_id
            and gm2.user_id = v_uid
        );

      delete from public.group_members
      where user_id = old_friend_uid;

      update public.payments
      set from_user_id = v_uid
      where from_user_id = old_friend_uid;

      update public.payments
      set to_user_id = v_uid
      where to_user_id = old_friend_uid;
    end if;

    update public.friends
    set friend_user_id = v_uid,
        display_name_snapshot = v_name,
        email_snapshot = v_email,
        updated_at_epoch_ms = now_ms
    where id = inv.friend_row_id;
  end if;

  if inv.kind = 'GROUP' and inv.group_id is not null then
    insert into public.group_members (id, group_id, user_id, role, joined_at_epoch_ms)
    values (gen_random_uuid(), inv.group_id, v_uid, 'MEMBER', now_ms)
    on conflict (group_id, user_id) do nothing;
  end if;

  -- Reciprocal for person invites (and group invites that carried a friend row).
  if inv.friend_row_id is not null then
    select coalesce(p.email, ''), coalesce(p.display_name, 'Friend')
      into inviter_email, inviter_name
    from public.profiles p
    where p.id = inv.inviter_user_id;

    insert into public.friends (
      id, owner_user_id, friend_user_id, email_snapshot, display_name_snapshot, updated_at_epoch_ms
    )
    values (
      gen_random_uuid(),
      v_uid,
      inv.inviter_user_id,
      coalesce(nullif(inviter_email, ''), inv.inviter_user_id::text),
      coalesce(nullif(inviter_name, ''), 'Friend'),
      now_ms
    )
    on conflict (owner_user_id, friend_user_id) do update
    set email_snapshot = excluded.email_snapshot,
        display_name_snapshot = excluded.display_name_snapshot,
        updated_at_epoch_ms = excluded.updated_at_epoch_ms;

    update public.invites
    set status = 'ACCEPTED',
        email = v_email
    where id = inv.id;
  end if;

  return 1;
end;
$$;

grant execute on function public.accept_invite_by_token(text) to authenticated;


-- ============================================
-- Optional: FCM notify triggers (pg_net)
-- No-op until app.settings.notify_function_url + service_role_key are set.
-- Prefer Dashboard Database Webhooks if you do not want the key in DB settings
-- (see docs/fcm-setup.md). Requires: create extension if not exists pg_net;
-- ============================================

create or replace function public.notify_group_ledger_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_url text := current_setting('app.settings.notify_function_url', true);
  v_key text := current_setting('app.settings.service_role_key', true);
  v_payload jsonb;
  v_record jsonb;
  v_old jsonb;
  v_group_id uuid;
begin
  if v_url is null or v_url = '' or v_key is null or v_key = '' then
    return coalesce(new, old);
  end if;

  v_group_id := coalesce(new.group_id, old.group_id);
  if v_group_id is null then
    return coalesce(new, old);
  end if;

  v_record := case when tg_op = 'DELETE' then null else to_jsonb(new) end;
  v_old := case when tg_op = 'INSERT' then null else to_jsonb(old) end;
  v_payload := jsonb_build_object(
    'type', tg_op,
    'table', tg_table_name,
    'record', v_record,
    'old_record', v_old
  );

  perform net.http_post(
    url := v_url,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || v_key
    ),
    body := v_payload
  );
  return coalesce(new, old);
end;
$$;

drop trigger if exists expenses_notify_group on public.expenses;
create trigger expenses_notify_group
  after insert or update or delete on public.expenses
  for each row
  execute function public.notify_group_ledger_change();

drop trigger if exists payments_notify_group on public.payments;
create trigger payments_notify_group
  after insert or update or delete on public.payments
  for each row
  execute function public.notify_group_ledger_change();
