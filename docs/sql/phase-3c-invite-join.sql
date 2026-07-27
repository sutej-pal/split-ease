-- Phase 3c: invite deep-link preview + accept-by-token
-- Run in Supabase SQL Editor after phase-3b-invites.sql (and ideally after phase-4
-- so accept_pending_invites includes expense remaps).

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
    select g.name into v_group_name from public.groups g where g.id = inv.group_id;

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

  update public.invites
  set status = 'ACCEPTED',
      email = v_email
  where id = inv.id;

  return 1;
end;
$$;

grant execute on function public.accept_invite_by_token(text) to authenticated;
