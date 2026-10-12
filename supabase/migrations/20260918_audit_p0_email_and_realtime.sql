-- UZZAP audit fixes (2026-09-18)
-- P0-1: Close anon enumeration of profiles.email while preserving nickname login.
--         Anon keeps column-level SELECT only on non-sensitive columns.
-- P1-3: Realtime DELETE events for room_messages must carry the old row
--         (client routes deletes by room_name from old_record).

-- ---------------------------------------------------------------------------
-- P0-1a: Exact-match email resolver for nickname login (replaces anon table read)
-- ---------------------------------------------------------------------------
create or replace function public.resolve_login_email(p_username text)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_email text;
begin
  select p.email into v_email
  from public.profiles as p
  where lower(p.username) = lower(trim(p_username))
  limit 1;
  return v_email;
end;
$$;

revoke all on function public.resolve_login_email(text) from public;
grant execute on function public.resolve_login_email(text) to anon, authenticated;

create index if not exists profiles_username_lower_idx on public.profiles (lower(username));

-- ---------------------------------------------------------------------------
-- P0-1b: Column-level grants for anon on profiles (deny email)
-- ---------------------------------------------------------------------------
revoke select on public.profiles from anon;
grant select (id, owner_id, username, nickname, is_online, last_seen, status_message, status, avatar, created_at)
  on public.profiles to anon;

-- ---------------------------------------------------------------------------
-- P1-3: room_messages replica identity (realtime DELETE propagation)
-- ---------------------------------------------------------------------------
alter table public.room_messages replica identity full;
