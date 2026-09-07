-- SplitEase account deletion (soft-delete / anonymize)
-- Apply after docs/sql/migration_db.sql (existing projects: run this file in the SQL Editor).
-- Safe to re-run (IF NOT EXISTS / CREATE OR REPLACE / DROP POLICY IF EXISTS).
--
-- Soft-delete only. Never DELETE profiles / auth.users or cascade historical
-- expenses, splits, or payments — other members' ledgers keep paid_by / user_id.

-- ============================================
-- profiles.deleted_at
-- ============================================

alter table public.profiles
  add column if not exists deleted_at timestamptz;

create index if not exists profiles_deleted_at_idx
  on public.profiles (deleted_at)
  where deleted_at is not null;

-- ============================================
-- Visibility: active profiles stay directory-searchable;
-- deleted/anonymized rows are only readable by people who share a ledger
-- (group, expense, payment, or friendship) so history still resolves "Deleted user".
-- ============================================

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
        p.deleted_at is null
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

drop policy if exists "profiles_select_authenticated" on public.profiles;
drop policy if exists "profiles_insert_own" on public.profiles;
drop policy if exists "profiles_update_own" on public.profiles;

create policy "profiles_select_authenticated"
  on public.profiles for select to authenticated
  using (public.can_see_profile(id));

create policy "profiles_insert_own"
  on public.profiles for insert to authenticated
  with check (auth.uid() = id and deleted_at is null);

create policy "profiles_update_own"
  on public.profiles for update to authenticated
  using (auth.uid() = id and deleted_at is null)
  with check (auth.uid() = id and deleted_at is null);

-- Phone duplicate-check must ignore anonymized rows so the number can be reused.
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
          and nullif(regexp_replace(coalesce(p.phone_number, ''), '\D', '', 'g'), '') = n.digits
          and coalesce(nullif(trim(p.phone_country_code), ''), '+91') = n.dial
      )
      or exists (
        select 1
        from auth.users u, normalized n
        where coalesce(u.banned_until, '-infinity'::timestamptz) <= now()
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

-- Email duplicate-check ignores banned Auth rows so a deleted user's original
-- address can be registered on a new auth.users row (email is also scrambled
-- by delete_own_account; this is a safety net if ban and scramble ever diverge).
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
      and coalesce(u.banned_until, '-infinity'::timestamptz) <= now()
  );
$$;

revoke all on function public.auth_email_registered(text) from public;
grant execute on function public.auth_email_registered(text) to anon, authenticated;

-- ============================================
-- Balance recompute (scale 2, same convention as domain.balance.BalanceCalculator)
-- net > 0 ⇒ owed to the user; net < 0 ⇒ user owes.
-- ============================================

create or replace function public.se_plain_numeric(p_text text)
returns numeric
language sql
immutable
as $$
  select case
    when p_text is null or btrim(p_text) = '' then 0::numeric
    when btrim(p_text) ~ '^-?[0-9]+(\.[0-9]+)?$' then btrim(p_text)::numeric
    else 0::numeric
  end;
$$;

revoke all on function public.se_plain_numeric(text) from public;

create or replace function public.account_deletion_blocking_groups(p_user_id uuid)
returns table(group_id uuid, group_name text)
language sql
stable
security definer
set search_path = public
as $$
  with scoped_expenses as (
    select e.*
    from public.expenses e
    where e.paid_by_user_id = p_user_id
       or exists (
         select 1 from public.expense_splits s
         where s.expense_id = e.id and s.user_id = p_user_id
       )
       or (
         e.group_id is not null
         and exists (
           select 1 from public.group_members gm
           where gm.group_id = e.group_id and gm.user_id = p_user_id
         )
       )
  ),
  multi_flag as (
    select
      e.id as expense_id,
      bool_or(nullif(btrim(s.paid_amount), '') is not null) as is_multi
    from scoped_expenses e
    left join public.expense_splits s on s.expense_id = e.id
    group by e.id
  ),
  expense_deltas as (
    select
      e.group_id,
      e.currency_code,
      e.paid_by_user_id as user_id,
      public.se_plain_numeric(e.amount) as delta
    from scoped_expenses e
    join multi_flag m on m.expense_id = e.id
    where not coalesce(m.is_multi, false)
    union all
    select
      e.group_id,
      e.currency_code,
      s.user_id,
      public.se_plain_numeric(s.paid_amount) as delta
    from scoped_expenses e
    join multi_flag m on m.expense_id = e.id
    join public.expense_splits s on s.expense_id = e.id
    where coalesce(m.is_multi, false)
      and public.se_plain_numeric(s.paid_amount) <> 0
    union all
    select
      e.group_id,
      e.currency_code,
      s.user_id,
      -public.se_plain_numeric(s.owed_amount) as delta
    from scoped_expenses e
    join public.expense_splits s on s.expense_id = e.id
  ),
  scoped_payments as (
    select p.*
    from public.payments p
    where p.from_user_id = p_user_id
       or p.to_user_id = p_user_id
       or (
         p.group_id is not null
         and exists (
           select 1 from public.group_members gm
           where gm.group_id = p.group_id and gm.user_id = p_user_id
         )
       )
  ),
  payment_deltas as (
    select p.group_id, p.currency_code, p.from_user_id as user_id, public.se_plain_numeric(p.amount) as delta
    from scoped_payments p
    union all
    select p.group_id, p.currency_code, p.to_user_id as user_id, -public.se_plain_numeric(p.amount) as delta
    from scoped_payments p
  ),
  nets as (
    select
      group_id,
      currency_code,
      user_id,
      round(sum(delta), 2) as net
    from (
      select * from expense_deltas
      union all
      select * from payment_deltas
    ) d
    group by group_id, currency_code, user_id
  )
  select distinct
    n.group_id,
    coalesce(g.name, 'Non-group expenses') as group_name
  from nets n
  left join public.groups g on g.id = n.group_id
  where n.user_id = p_user_id
    and n.net <> 0;
$$;

revoke all on function public.account_deletion_blocking_groups(uuid) from public;

-- ============================================
-- delete_own_account(): authenticated caller, own auth.uid() only
-- ============================================

create or replace function public.delete_own_account()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_uid uuid := auth.uid();
  v_deleted_email text;
  v_now_ms bigint := (extract(epoch from clock_timestamp()) * 1000)::bigint;
  v_payload jsonb;
begin
  if v_uid is null then
    raise exception 'Not authenticated';
  end if;

  select jsonb_agg(jsonb_build_object('id', coalesce(b.group_id::text, ''), 'name', b.group_name) order by b.group_name)
    into v_payload
  from public.account_deletion_blocking_groups(v_uid) b;

  if v_payload is not null then
    raise exception '%',
      jsonb_build_object(
        'code', 'ACCOUNT_HAS_BALANCE',
        'groups', v_payload
      )::text;
  end if;

  v_deleted_email := 'deleted-' || v_uid::text || '@deleted.invalid';

  update public.profiles
  set
    email = v_deleted_email,
    display_name = 'Deleted user',
    photo_url = null,
    phone_country_code = null,
    phone_number = null,
    preferred_currency = null,
    updated_at_epoch_ms = v_now_ms,
    deleted_at = clock_timestamp()
  where id = v_uid;

  if not found then
    insert into public.profiles (
      id, email, display_name, photo_url, phone_country_code, phone_number,
      preferred_currency, updated_at_epoch_ms, deleted_at
    ) values (
      v_uid, v_deleted_email, 'Deleted user', null, null, null,
      null, v_now_ms, clock_timestamp()
    );
  end if;

  update public.friends
  set
    display_name_snapshot = 'Deleted user',
    email_snapshot = v_deleted_email,
    updated_at_epoch_ms = v_now_ms
  where friend_user_id = v_uid;

  update public.invites
  set status = 'CANCELLED'
  where inviter_user_id = v_uid
    and status = 'PENDING';

  delete from public.device_tokens where user_id = v_uid;
  delete from public.notification_prefs where user_id = v_uid;

  -- Ban + scramble identity. Do not DELETE auth.users (would cascade profiles
  -- and break historical paid_by / user_id references that share the UUID).
  update auth.users
  set
    banned_until = 'infinity'::timestamptz,
    email = v_deleted_email,
    phone = null,
    raw_user_meta_data = jsonb_build_object(
      'display_name', 'Deleted user',
      'account_deleted', true
    ),
    raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb)
      || jsonb_build_object('account_deleted', true)
  where id = v_uid;

  delete from auth.identities where user_id = v_uid;
  -- Identities are dropped so the same Google (or email) provider account can
  -- create a *new* auth.users row. This banned row is kept for expense/payment FKs.

  begin
    delete from auth.sessions where user_id = v_uid;
  exception
    when undefined_table then
      null;
  end;

  begin
    -- GoTrue stores refresh_tokens.user_id as varchar, not uuid.
    delete from auth.refresh_tokens where user_id = v_uid::text;
  exception
    when undefined_table then
      null;
  end;
end;
$$;

revoke all on function public.delete_own_account() from public;
grant execute on function public.delete_own_account() to authenticated;
