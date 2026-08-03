-- Fix: friend/group expenses invisible to the other account.
--
-- Two root causes:
-- 1) Friendship rows are owner-only. Adding A→B never creates B→A, so the
--    invitee has no Friends list entry (and no friend-detail ledger).
-- 2) Inviter-side reconcile (promotePendingInviteIfJoined) remaps expense
--    splits only in Room, then marks the invite ACCEPTED. Remote
--    expense_splits keep the placeholder UUID, and accept_pending_invites
--    skips ACCEPTED invites — so the invitee never pulls those expenses.
--
-- Apply in Supabase SQL Editor (existing projects). Fresh DBs get the same
-- logic via docs/sql/migration_db.sql.

-- ---------------------------------------------------------------------------
-- Remap placeholder user ids on expenses / payments / group_members.
-- Callable by the friendship owner who still points at p_from or p_to.
-- ---------------------------------------------------------------------------
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
-- Ensure B→A friendship exists when A→B is created (security definer so the
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
