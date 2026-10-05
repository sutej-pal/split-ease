-- SplitEase SQL Patch: Account Settings Expansion
-- Run this in the Supabase SQL Editor for an existing database.
-- Safe to re-run.

alter table public.profiles add column if not exists time_zone text;
alter table public.profiles add column if not exists allow_friend_suggestions boolean not null default true;
alter table public.profiles add column if not exists deactivated_at timestamptz;

-- Secondary emails (Edge Function secondary-email uses the service role).
-- code_hash is an HMAC of the 6-digit code and is not granted to clients.
create table if not exists public.user_emails (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  email text not null,
  verified_at timestamptz,
  code_hash text,
  code_expires_at timestamptz,
  attempts int not null default 0,
  last_sent_at timestamptz default now(),
  created_at timestamptz default now()
);

alter table public.user_emails add column if not exists last_sent_at timestamptz default now();

create unique index if not exists idx_user_emails_email_lower
  on public.user_emails (lower(email));

create unique index if not exists idx_user_emails_verified_email
  on public.user_emails (lower(email))
  where verified_at is not null;

create index if not exists idx_user_emails_user_id
  on public.user_emails (user_id);

alter table public.user_emails enable row level security;

drop policy if exists "user_emails_select" on public.user_emails;
create policy "user_emails_select"
  on public.user_emails for select to authenticated
  using (user_id = auth.uid());

revoke all on table public.user_emails from anon, authenticated;
grant select (id, user_id, email, verified_at, created_at)
  on table public.user_emails to authenticated;

-- One row per verification email actually handed to the mail service.
create table if not exists public.user_email_send_log (
  id bigint generated always as identity primary key,
  user_id uuid not null,
  sent_at timestamptz not null default now()
);

create index if not exists idx_user_email_send_log_user_sent
  on public.user_email_send_log (user_id, sent_at desc);

alter table public.user_email_send_log enable row level security;
revoke all on table public.user_email_send_log from anon, authenticated;

-- Phone lookup. Signed-in callers are excluded via auth.uid(); anon cannot
-- pass another user's id. Drops the 3-arg overload so PostgREST is not ambiguous.
drop function if exists public.auth_phone_registered(text, text, uuid);
drop function if exists public.auth_phone_registered(text, text);

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
        where p.deleted_at is null
          and (auth.uid() is null or p.id <> auth.uid())
          and nullif(regexp_replace(coalesce(p.phone_number, ''), '\D', '', 'g'), '') = n.digits
          and coalesce(nullif(trim(p.phone_country_code), ''), '+91') = n.dial
      )
      or exists (
        select 1
        from auth.users u, normalized n
        where coalesce(u.banned_until, '-infinity'::timestamptz) <= now()
          and (auth.uid() is null or u.id <> auth.uid())
          and nullif(
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

-- Manual reactivation for admin/support:
--   update auth.users set banned_until = null where id = '<user_uuid>';
--   update public.profiles set deactivated_at = null where id = '<user_uuid>';

create or replace function public.deactivate_own_account()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  update public.profiles
  set deactivated_at = clock_timestamp()
  where id = v_uid;

  delete from public.device_tokens where user_id = v_uid;

  update auth.users
  set banned_until = 'infinity'::timestamptz
  where id = v_uid;

  begin
    delete from auth.sessions where user_id = v_uid;
  exception
    when undefined_table then
      null;
  end;

  begin
    delete from auth.refresh_tokens where user_id = v_uid::text;
  exception
    when undefined_table then
      null;
  end;
end;
$$;

revoke all on function public.deactivate_own_account() from public;
grant execute on function public.deactivate_own_account() to authenticated;

-- Hide deactivated accounts and suggestion opt-outs from directory search.
-- People who already share a ledger can still read the profile.
create or replace function public.can_see_profile(p_profile_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.profiles p
    where p.id = p_profile_id
      and (
        (
          p.deleted_at is null
          and p.deactivated_at is null
          and coalesce(p.allow_friend_suggestions, true)
        )
        or p.id = auth.uid()
        or exists (
          select 1
          from public.friends f
          where (f.owner_user_id = auth.uid() and f.friend_user_id = p.id)
             or (f.friend_user_id = auth.uid() and f.owner_user_id = p.id)
        )
        or exists (
          select 1
          from public.group_members me
          join public.group_members them on them.group_id = me.group_id
          where me.user_id = auth.uid()
            and them.user_id = p.id
        )
        or exists (
          select 1
          from public.expenses e
          where (
              e.paid_by_user_id = p.id
              or exists (
                select 1
                from public.expense_splits s
                where s.expense_id = e.id and s.user_id = p.id
              )
            )
            and (
              e.paid_by_user_id = auth.uid()
              or exists (
                select 1
                from public.expense_splits s2
                where s2.expense_id = e.id and s2.user_id = auth.uid()
              )
              or (
                e.group_id is not null
                and exists (
                  select 1
                  from public.group_members gm
                  where gm.group_id = e.group_id and gm.user_id = auth.uid()
                )
              )
            )
        )
        or exists (
          select 1
          from public.payments pmt
          where (pmt.from_user_id = p.id or pmt.to_user_id = p.id)
            and (
              pmt.from_user_id = auth.uid()
              or pmt.to_user_id = auth.uid()
              or (
                pmt.group_id is not null
                and exists (
                  select 1
                  from public.group_members gm
                  where gm.group_id = pmt.group_id and gm.user_id = auth.uid()
                )
              )
            )
        )
      )
  );
$$;

revoke all on function public.can_see_profile(uuid) from public;
grant execute on function public.can_see_profile(uuid) to authenticated;
