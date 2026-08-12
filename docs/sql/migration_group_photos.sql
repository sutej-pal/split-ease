-- Group list photo sync (run in Supabase SQL editor)
-- 1) Column on groups for the square list/settings avatar
-- 2) Reuses the existing public `group-covers` Storage bucket
--    Object key: group-covers/{groupId}/photo.jpg

alter table public.groups
  add column if not exists photo_url text;

-- Storage policies already cover the whole group-covers bucket (select/insert/update/delete
-- for group members). No new bucket policies required for photo.jpg objects.
