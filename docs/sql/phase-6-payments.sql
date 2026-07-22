-- Phase 6: payments + recurring columns on expenses
-- Run after phase-4-expenses.sql

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
