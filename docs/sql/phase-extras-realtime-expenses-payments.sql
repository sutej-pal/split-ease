-- Enable Supabase Realtime for group ledger tables (Slice 1 live updates).
-- Safe to re-run: ignores tables already in the publication.

do $$
begin
  begin
    alter publication supabase_realtime add table public.expenses;
  exception
    when duplicate_object then null;
  end;
  begin
    alter publication supabase_realtime add table public.payments;
  exception
    when duplicate_object then null;
  end;
end $$;

-- DELETE / UPDATE payloads need old row fields (group_id) for filtered channels.
alter table public.expenses replica identity full;
alter table public.payments replica identity full;
