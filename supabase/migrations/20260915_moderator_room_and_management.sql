-- Uzzap moderator room + moderator management
-- Run in the linked Supabase project before enabling the matching Android UI.

create table if not exists public.user_roles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  role text not null default 'user' check (role in ('user','moderator','admin')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists user_roles_role_idx on public.user_roles(role);

alter table public.user_roles enable row level security;

create or replace function public.current_app_role()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select coalesce((select role from public.user_roles where user_id = auth.uid()), 'user');
$$;

revoke all on function public.current_app_role() from public;
grant execute on function public.current_app_role() to authenticated;

create or replace function public.is_moderator_or_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.current_app_role() in ('moderator','admin');
$$;

grant execute on function public.is_moderator_or_admin() to authenticated;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.current_app_role() = 'admin';
$$;

grant execute on function public.is_admin() to authenticated;

-- Admins can manage roles. Users can read their own role; moderators may read roles
-- for moderation UI without being allowed to promote anyone.
drop policy if exists user_roles_select on public.user_roles;
create policy user_roles_select on public.user_roles
for select to authenticated
using (user_id = auth.uid() or public.is_moderator_or_admin());

drop policy if exists user_roles_insert_admin on public.user_roles;
create policy user_roles_insert_admin on public.user_roles
for insert to authenticated
with check (public.is_admin());

drop policy if exists user_roles_update_admin on public.user_roles;
create policy user_roles_update_admin on public.user_roles
for update to authenticated
using (public.is_admin())
with check (public.is_admin());

drop policy if exists user_roles_delete_admin on public.user_roles;
create policy user_roles_delete_admin on public.user_roles
for delete to authenticated
using (public.is_admin());

-- Prevent clients from changing their own role through direct profile writes.
-- The app should read this table through the authenticated session.

create table if not exists public.moderator_room_members (
  user_id uuid primary key references auth.users(id) on delete cascade,
  granted_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now()
);

alter table public.moderator_room_members enable row level security;

drop policy if exists moderator_room_members_select on public.moderator_room_members;
create policy moderator_room_members_select on public.moderator_room_members
for select to authenticated
using (user_id = auth.uid() or public.is_moderator_or_admin());

drop policy if exists moderator_room_members_insert on public.moderator_room_members;
create policy moderator_room_members_insert on public.moderator_room_members
for insert to authenticated
with check (public.is_admin());

drop policy if exists moderator_room_members_delete on public.moderator_room_members;
create policy moderator_room_members_delete on public.moderator_room_members
for delete to authenticated
using (public.is_admin());

-- Owner bootstrap: if this account already exists, make it admin immediately.
-- If it does not exist yet, the trigger below assigns admin when the account is created.
insert into public.user_roles(user_id, role)
select id, 'admin' from auth.users
where lower(email) = lower('mharbalaba@gmail.com')
on conflict (user_id) do update set role = 'admin', updated_at = now();

create or replace function public.bootstrap_uzzap_owner_role()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if lower(coalesce(new.email,'')) = lower('mharbalaba@gmail.com') then
    insert into public.user_roles(user_id, role)
    values (new.id, 'admin')
    on conflict (user_id) do update set role = 'admin', updated_at = now();
  else
    insert into public.user_roles(user_id, role)
    values (new.id, 'user')
    on conflict (user_id) do nothing;
  end if;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_uzzap_roles on auth.users;
create trigger on_auth_user_created_uzzap_roles
after insert on auth.users
for each row execute function public.bootstrap_uzzap_owner_role();

-- Canonical moderator-only room. This is deliberately separate from province rooms.
insert into public.chat_categories(id, name, sort_order)
values ('moderation', 'Moderation', 0)
on conflict (id) do update set name = excluded.name;

insert into public.chat_rooms(category_id, name, capacity)
select 'moderation', 'Moderator Room', 100
where not exists (select 1 from public.chat_rooms where lower(name) = lower('Moderator Room'));

-- Room visibility is enforced server-side using the moderator role.
-- Adjust policies if the existing project uses different policy names/ownership.
drop policy if exists moderator_room_select on public.chat_rooms;
create policy moderator_room_select on public.chat_rooms
for select to authenticated
using (
  lower(name) <> lower('Moderator Room')
  or public.is_moderator_or_admin()
);

-- Moderator room messages are only readable/writable by moderators/admins.
-- Existing general room policies remain untouched.
drop policy if exists moderator_room_messages_select on public.room_messages;
create policy moderator_room_messages_select on public.room_messages
for select to authenticated
using (
  lower(room_name) <> lower('Moderator Room')
  or public.is_moderator_or_admin()
);

drop policy if exists moderator_room_messages_insert on public.room_messages;
create policy moderator_room_messages_insert on public.room_messages
for insert to authenticated
with check (
  lower(room_name) <> lower('Moderator Room')
  or public.is_moderator_or_admin()
);

-- Convenience RPC: admins promote/demote by email without exposing auth.users.
create or replace function public.set_moderator_by_email(target_email text, make_moderator boolean default true)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  target_id uuid;
begin
  if not public.is_admin() then
    raise exception 'Admin privileges required';
  end if;

  select id into target_id
  from auth.users
  where lower(email) = lower(trim(target_email))
  limit 1;

  if target_id is null then
    return false;
  end if;

  if make_moderator then
    insert into public.user_roles(user_id, role)
    values (target_id, 'moderator')
    on conflict (user_id) do update set role = 'moderator', updated_at = now();
    insert into public.moderator_room_members(user_id, granted_by)
    values (target_id, auth.uid())
    on conflict (user_id) do update set granted_by = auth.uid();
  else
    update public.user_roles
    set role = 'user', updated_at = now()
    where user_id = target_id and role = 'moderator';
    delete from public.moderator_room_members where user_id = target_id;
  end if;
  return true;
end;
$$;

revoke all on function public.set_moderator_by_email(text, boolean) from public;
grant execute on function public.set_moderator_by_email(text, boolean) to authenticated;
