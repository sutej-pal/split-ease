-- Pin board inline images (run in Supabase SQL editor)
-- Public Storage bucket + policies for pin-board-images/{groupId}/{imageId}.jpg

insert into storage.buckets (id, name, public)
values ('pin-board-images', 'pin-board-images', true)
on conflict (id) do update set public = excluded.public;

drop policy if exists "pin_board_images_select" on storage.objects;
drop policy if exists "pin_board_images_insert" on storage.objects;
drop policy if exists "pin_board_images_update" on storage.objects;
drop policy if exists "pin_board_images_delete" on storage.objects;

create policy "pin_board_images_select"
  on storage.objects for select to authenticated
  using (bucket_id = 'pin-board-images');

create policy "pin_board_images_insert"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'pin-board-images'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );

create policy "pin_board_images_update"
  on storage.objects for update to authenticated
  using (
    bucket_id = 'pin-board-images'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  )
  with check (
    bucket_id = 'pin-board-images'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );

create policy "pin_board_images_delete"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'pin-board-images'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );
