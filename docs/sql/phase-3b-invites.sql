-- Phase 3b: email invites for non-users (run in Supabase SQL Editor after phase-3-schema.sql)

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
    where lower(email) = v_email and status = 'PENDING'
  loop
    if inv.friend_row_id is not null then
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

    update public.invites
    set status = 'ACCEPTED'
    where id = inv.id;

    accepted_count := accepted_count + 1;
  end loop;

  return accepted_count;
end;
$$;

grant execute on function public.accept_pending_invites() to authenticated;
