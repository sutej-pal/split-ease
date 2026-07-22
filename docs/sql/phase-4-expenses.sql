-- Phase 4: expenses + splits (run after phase-3 and phase-3b)
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
    check (split_type in ('EQUAL', 'UNEQUAL', 'PERCENTAGE', 'SHARES')),
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
  unique (expense_id, user_id)
);

create index if not exists expense_splits_expense_idx on public.expense_splits (expense_id);
create index if not exists expense_splits_user_idx on public.expense_splits (user_id);

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

create policy "expenses_select"
  on public.expenses for select to authenticated
  using (public.can_access_expense(id));

create policy "expenses_insert"
  on public.expenses for insert to authenticated
  with check (
    paid_by_user_id = auth.uid()
    or (
      group_id is not null
      and exists (
        select 1 from public.groups g
        where g.id = group_id and g.created_by_user_id = auth.uid()
      )
    )
  );

create policy "expenses_update"
  on public.expenses for update to authenticated
  using (public.can_access_expense(id))
  with check (public.can_access_expense(id));

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

create policy "expense_splits_insert"
  on public.expense_splits for insert to authenticated
  with check (public.can_access_expense(expense_id) or exists (
    select 1 from public.expenses e
    where e.id = expense_id and e.paid_by_user_id = auth.uid()
  ));

-- Insert of splits often happens in the same request as expense insert;
-- allow creator via paid_by on the parent (may not be visible yet in can_access for other participants).
drop policy if exists "expense_splits_insert" on public.expense_splits;
create policy "expense_splits_insert"
  on public.expense_splits for insert to authenticated
  with check (
    exists (
      select 1 from public.expenses e
      where e.id = expense_id
        and (
          e.paid_by_user_id = auth.uid()
          or (
            e.group_id is not null
            and exists (
              select 1 from public.groups g
              where g.id = e.group_id and g.created_by_user_id = auth.uid()
            )
          )
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
      where e.id = expense_id and e.paid_by_user_id = auth.uid()
    )
  );

-- Replace accept RPC to also remap expense payer/splits from placeholder → real user
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
    where lower(email) = v_email and status = 'PENDING'
  loop
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
    set status = 'ACCEPTED'
    where id = inv.id;

    accepted_count := accepted_count + 1;
  end loop;

  return accepted_count;
end;
$$;

grant execute on function public.accept_pending_invites() to authenticated;
grant execute on function public.can_access_expense(uuid) to authenticated;
