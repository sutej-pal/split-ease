-- Expense comments + receipt photos (run in Supabase SQL editor)
-- 1) Tables with RLS via can_access_expense
-- 2) Public Storage bucket expense-receipts/{expenseId}/{photoId}.jpg

create table if not exists public.expense_comments (
  id uuid primary key,
  expense_id uuid not null references public.expenses (id) on delete cascade,
  author_user_id uuid not null,
  body text not null,
  kind text not null check (kind in ('USER', 'SYSTEM')),
  created_at_epoch_ms bigint not null
);

create index if not exists expense_comments_expense_idx
  on public.expense_comments (expense_id);
create index if not exists expense_comments_created_idx
  on public.expense_comments (created_at_epoch_ms);

alter table public.expense_comments enable row level security;

drop policy if exists "expense_comments_select" on public.expense_comments;
drop policy if exists "expense_comments_insert" on public.expense_comments;
drop policy if exists "expense_comments_delete" on public.expense_comments;

create policy "expense_comments_select"
  on public.expense_comments for select to authenticated
  using (public.can_access_expense(expense_id));

create policy "expense_comments_insert"
  on public.expense_comments for insert to authenticated
  with check (
    author_user_id = auth.uid()
    and public.can_access_expense(expense_id)
  );

create policy "expense_comments_delete"
  on public.expense_comments for delete to authenticated
  using (
    author_user_id = auth.uid()
    or public.can_access_expense(expense_id)
  );

create table if not exists public.expense_photos (
  id uuid primary key,
  expense_id uuid not null references public.expenses (id) on delete cascade,
  created_by_user_id uuid not null,
  remote_url text,
  created_at_epoch_ms bigint not null
);

create index if not exists expense_photos_expense_idx
  on public.expense_photos (expense_id);
create index if not exists expense_photos_created_idx
  on public.expense_photos (created_at_epoch_ms);

alter table public.expense_photos enable row level security;

drop policy if exists "expense_photos_select" on public.expense_photos;
drop policy if exists "expense_photos_insert" on public.expense_photos;
drop policy if exists "expense_photos_update" on public.expense_photos;
drop policy if exists "expense_photos_delete" on public.expense_photos;

create policy "expense_photos_select"
  on public.expense_photos for select to authenticated
  using (public.can_access_expense(expense_id));

create policy "expense_photos_insert"
  on public.expense_photos for insert to authenticated
  with check (
    created_by_user_id = auth.uid()
    and public.can_access_expense(expense_id)
  );

create policy "expense_photos_update"
  on public.expense_photos for update to authenticated
  using (
    created_by_user_id = auth.uid()
    and public.can_access_expense(expense_id)
  )
  with check (
    created_by_user_id = auth.uid()
    and public.can_access_expense(expense_id)
  );

create policy "expense_photos_delete"
  on public.expense_photos for delete to authenticated
  using (
    created_by_user_id = auth.uid()
    or public.can_access_expense(expense_id)
  );

insert into storage.buckets (id, name, public)
values ('expense-receipts', 'expense-receipts', true)
on conflict (id) do update set public = excluded.public;

drop policy if exists "expense_receipts_select" on storage.objects;
drop policy if exists "expense_receipts_insert" on storage.objects;
drop policy if exists "expense_receipts_update" on storage.objects;
drop policy if exists "expense_receipts_delete" on storage.objects;

-- Path: {expenseId}/{photoId}.jpg — folder name is the expense UUID.
create policy "expense_receipts_select"
  on storage.objects for select to authenticated
  using (bucket_id = 'expense-receipts');

create policy "expense_receipts_insert"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'expense-receipts'
    and public.can_access_expense((storage.foldername(name))[1]::uuid)
  );

create policy "expense_receipts_update"
  on storage.objects for update to authenticated
  using (
    bucket_id = 'expense-receipts'
    and public.can_access_expense((storage.foldername(name))[1]::uuid)
  )
  with check (
    bucket_id = 'expense-receipts'
    and public.can_access_expense((storage.foldername(name))[1]::uuid)
  );

create policy "expense_receipts_delete"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'expense-receipts'
    and public.can_access_expense((storage.foldername(name))[1]::uuid)
  );
