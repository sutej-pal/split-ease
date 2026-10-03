-- SplitEase SQL Patch: Account Settings Expansion
-- Run this in Supabase SQL Editor for existing databases.

alter table public.profiles add column if not exists time_zone text;
alter table public.profiles add column if not exists allow_friend_suggestions boolean not null default true;

-- Secondary emails table (managed by Edge Function secondary-email / service role)
create table if not exists public.user_emails (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  email text not null,
  verified_at timestamptz,
  code_hash text,
  code_expires_at timestamptz,
  attempts int not null default 0,
  created_at timestamptz default now()
);

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

-- Phone registration check supporting optional user exclusion
create or replace function public.auth_phone_registered(
  p_country_code text,
  p_phone text,
  p_exclude_user_id uuid default null
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
        and (p_exclude_user_id is null or p.id <> p_exclude_user_id)
        and nullif(regexp_replace(coalesce(p.phone_number, ''), '\D', '', 'g'), '') = n.digits
        and coalesce(nullif(trim(p.phone_country_code), ''), '+91') = n.dial
    )
    or exists (
      select 1
      from auth.users u, normalized n
      where coalesce(u.banned_until, '-infinity'::timestamptz) <= now()
        and (p_exclude_user_id is null or u.id <> p_exclude_user_id)
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

revoke all on function public.auth_phone_registered(text, text, uuid) from public;
grant execute on function public.auth_phone_registered(text, text, uuid) to anon, authenticated;
