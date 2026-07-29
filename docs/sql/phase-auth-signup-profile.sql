-- Signup profile fields: phone + preferred currency on public.profiles
-- Apply in Supabase SQL Editor (safe to re-run).
-- Creates the base profiles table if this project has not run Phase 3 / migration_db yet.

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

-- If profiles already existed without the signup columns, add them.
alter table public.profiles
  add column if not exists phone_country_code text;

alter table public.profiles
  add column if not exists phone_number text;

alter table public.profiles
  add column if not exists preferred_currency text;
