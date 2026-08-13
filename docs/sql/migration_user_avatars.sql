-- Profile photos (run in Supabase SQL editor)
-- Public Storage bucket + policies for user-avatars/{userId}/photo.jpg
-- profiles.photo_url already exists; this only adds Storage so other devices can load avatars.

insert into storage.buckets (id, name, public)
values ('user-avatars', 'user-avatars', true)
on conflict (id) do update set public = excluded.public;

drop policy if exists "user_avatars_select" on storage.objects;
drop policy if exists "user_avatars_insert" on storage.objects;
drop policy if exists "user_avatars_update" on storage.objects;
drop policy if exists "user_avatars_delete" on storage.objects;

-- Anyone authenticated can read public avatar objects.
create policy "user_avatars_select"
  on storage.objects for select to authenticated
  using (bucket_id = 'user-avatars');

create policy "user_avatars_insert"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'user-avatars'
    and (storage.foldername(name))[1]::uuid = auth.uid()
  );

create policy "user_avatars_update"
  on storage.objects for update to authenticated
  using (
    bucket_id = 'user-avatars'
    and (storage.foldername(name))[1]::uuid = auth.uid()
  )
  with check (
    bucket_id = 'user-avatars'
    and (storage.foldername(name))[1]::uuid = auth.uid()
  );

create policy "user_avatars_delete"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'user-avatars'
    and (storage.foldername(name))[1]::uuid = auth.uid()
  );
