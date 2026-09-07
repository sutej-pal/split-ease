-- Activity sync (cross-device feed)
-- Apply on existing projects after migration_db.sql.
-- Fresh DBs already get this from migration_db.sql (safe to re-run).

create table if not exists public.activity_events (
  id uuid primary key,
  kind text not null,
  title text not null,
  subtitle text not null,
  amount_label text not null,
  actor_user_id uuid not null references auth.users (id) on delete cascade,
  -- App sends null here for EXPENSE_DELETED upserts (parent expense is already gone).
  related_expense_id uuid references public.expenses (id) on delete set null,
  involved_user_ids text not null,
  sort_epoch_ms bigint not null,
  created_at timestamptz not null default now()
);

create index if not exists activity_events_sort_idx on public.activity_events (sort_epoch_ms);
create index if not exists activity_events_actor_idx on public.activity_events (actor_user_id);

alter table public.activity_events enable row level security;

drop policy if exists "activity_events_select" on public.activity_events;
drop policy if exists "activity_events_insert" on public.activity_events;
drop policy if exists "activity_events_update" on public.activity_events;

-- Users can see events they performed or are involved in.
create policy "activity_events_select"
  on public.activity_events for select to authenticated
  using (
    actor_user_id = auth.uid()
    or involved_user_ids like '%,' || auth.uid()::text || ',%'
  );

-- Only the actor can insert their own events.
create policy "activity_events_insert"
  on public.activity_events for insert to authenticated
  with check (actor_user_id = auth.uid());

-- Upsert (ON CONFLICT UPDATE) requires an update policy for the actor.
create policy "activity_events_update"
  on public.activity_events for update to authenticated
  using (actor_user_id = auth.uid())
  with check (actor_user_id = auth.uid());
