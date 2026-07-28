-- Device push tokens + notify helpers for group expense/payment FCM (Slice 2).
-- Apply after phase-3e and phase-6-payments.
--
-- Ops: deploy supabase/functions/notify-group-members and wire Database Webhooks
-- (or pg_net triggers below) — see docs/fcm-setup.md.

create table if not exists public.device_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  token text not null,
  platform text not null default 'android',
  updated_at_epoch_ms bigint not null default 0,
  unique (user_id, token)
);

create index if not exists device_tokens_user_idx on public.device_tokens (user_id);

alter table public.device_tokens enable row level security;

drop policy if exists "device_tokens_select_own" on public.device_tokens;
drop policy if exists "device_tokens_insert_own" on public.device_tokens;
drop policy if exists "device_tokens_update_own" on public.device_tokens;
drop policy if exists "device_tokens_delete_own" on public.device_tokens;

create policy "device_tokens_select_own"
  on public.device_tokens for select to authenticated
  using (auth.uid() = user_id);

create policy "device_tokens_insert_own"
  on public.device_tokens for insert to authenticated
  with check (auth.uid() = user_id);

create policy "device_tokens_update_own"
  on public.device_tokens for update to authenticated
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "device_tokens_delete_own"
  on public.device_tokens for delete to authenticated
  using (auth.uid() = user_id);

-- Service role / Edge Function reads all tokens; no extra policy needed for service_role.
