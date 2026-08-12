-- Include group list photo on invite landing preview (run in Supabase SQL editor)
-- Requires public.groups.photo_url (see migration_group_photos.sql).

alter table public.groups
  add column if not exists photo_url text;

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
