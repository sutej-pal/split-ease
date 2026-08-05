-- Group cover photo sync (run in Supabase SQL editor)
-- 1) Column on groups
-- 2) Members can update groups (needed so any member can set cover_url)
-- 3) Public Storage bucket + policies for group-covers/{groupId}/cover.jpg

alter table public.groups
  add column if not exists cover_url text;

drop policy if exists "groups_update" on public.groups;
create policy "groups_update"
  on public.groups for update to authenticated
  using (
    created_by_user_id = auth.uid()
    or public.is_group_member(id)
  )
  with check (
    created_by_user_id = auth.uid()
    or public.is_group_member(id)
  );

insert into storage.buckets (id, name, public)
values ('group-covers', 'group-covers', true)
on conflict (id) do update set public = excluded.public;

drop policy if exists "group_covers_select" on storage.objects;
drop policy if exists "group_covers_insert" on storage.objects;
drop policy if exists "group_covers_update" on storage.objects;
drop policy if exists "group_covers_delete" on storage.objects;

-- Anyone authenticated can read public cover objects (bucket is public; policy still required for listing).
create policy "group_covers_select"
  on storage.objects for select to authenticated
  using (bucket_id = 'group-covers');

create policy "group_covers_insert"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'group-covers'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );

create policy "group_covers_update"
  on storage.objects for update to authenticated
  using (
    bucket_id = 'group-covers'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  )
  with check (
    bucket_id = 'group-covers'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );

create policy "group_covers_delete"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'group-covers'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );
