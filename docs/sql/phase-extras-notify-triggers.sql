-- Optional: invoke notify-group-members via pg_net after expense/payment changes.
-- Requires: create extension if not exists pg_net with schema extensions;
-- Set app settings once (as postgres):
--   alter database postgres set app.settings.notify_function_url =
--     'https://<PROJECT_REF>.supabase.co/functions/v1/notify-group-members';
--   alter database postgres set app.settings.service_role_key = '<SERVICE_ROLE_KEY>';
-- Prefer Dashboard Database Webhooks if you do not want the key in DB settings
-- (see docs/fcm-setup.md).

create or replace function public.notify_group_ledger_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_url text := current_setting('app.settings.notify_function_url', true);
  v_key text := current_setting('app.settings.service_role_key', true);
  v_payload jsonb;
  v_record jsonb;
  v_old jsonb;
  v_group_id uuid;
begin
  if v_url is null or v_url = '' or v_key is null or v_key = '' then
    return coalesce(new, old);
  end if;

  v_group_id := coalesce(new.group_id, old.group_id);
  if v_group_id is null then
    return coalesce(new, old);
  end if;

  v_record := case when tg_op = 'DELETE' then null else to_jsonb(new) end;
  v_old := case when tg_op = 'INSERT' then null else to_jsonb(old) end;
  v_payload := jsonb_build_object(
    'type', tg_op,
    'table', tg_table_name,
    'record', v_record,
    'old_record', v_old
  );

  perform net.http_post(
    url := v_url,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || v_key
    ),
    body := v_payload
  );
  return coalesce(new, old);
end;
$$;

drop trigger if exists expenses_notify_group on public.expenses;
create trigger expenses_notify_group
  after insert or update or delete on public.expenses
  for each row
  execute function public.notify_group_ledger_change();

drop trigger if exists payments_notify_group on public.payments;
create trigger payments_notify_group
  after insert or update or delete on public.payments
  for each row
  execute function public.notify_group_ledger_change();
