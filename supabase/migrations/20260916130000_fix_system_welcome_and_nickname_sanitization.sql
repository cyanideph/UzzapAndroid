-- Welcome messages are system messages and must not be rewritten as the joining user.
-- Nicknames are text-only; strip Uzzap color markup at the database boundary as defense in depth.

create or replace function public.sanitize_nickname(p_nickname text)
returns text
language plpgsql
immutable
as $$
declare
  v text := coalesce(p_nickname, '');
begin
  -- Remove [cNN] or [cNN followed by a separator space. The closing ] is optional.
  v := regexp_replace(v, '\\[c[0-9]{2}\\]? ?', '', 'gi');
  -- Color markup commonly uses an optional closing ] at the end of the nickname.
  if v like '%]' and p_nickname ~* '\\[c[0-9]{2}' then
    v := left(v, length(v) - 1);
  end if;
  return btrim(v);
end;
$$;

create or replace function public.set_profile_nickname_plain()
returns trigger
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
begin
  new.nickname := nullif(left(public.sanitize_nickname(new.nickname), 64), '');
  return new;
end;
$$;

drop trigger if exists trg_profile_nickname_plain on public.profiles;
create trigger trg_profile_nickname_plain
before insert or update of nickname on public.profiles
for each row execute function public.set_profile_nickname_plain();

create or replace function public.set_room_message_sender()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  actor uuid := auth.uid();
  display_name text;
begin
  if actor is null then
    -- Internal bot/system inserts may run without an authenticated user.
    if coalesce(new.is_system, false) then
      return new;
    end if;
    raise exception 'Authentication required';
  end if;

  -- System/bot messages keep their authoritative sender and must never become the joining user.
  if coalesce(new.is_system, false) then
    return new;
  end if;

  new.sender_id := actor;
  select coalesce(nullif(public.sanitize_nickname(p.nickname), ''), p.username, 'user_' || left(actor::text, 8))
    into display_name
  from public.profiles p
  where p.owner_id = actor
  limit 1;

  new.sender := coalesce(display_name, 'user_' || left(actor::text, 8));
  new.is_system := false;
  return new;
end;
$$;

revoke all on function public.sanitize_nickname(text) from public;
grant execute on function public.sanitize_nickname(text) to authenticated;
revoke all on function public.set_profile_nickname_plain() from public;
grant execute on function public.set_profile_nickname_plain() to authenticated;
revoke all on function public.set_room_message_sender() from public;
grant execute on function public.set_room_message_sender() to authenticated;

-- Normalize existing profile nicknames so future room messages use plain text.
update public.profiles
set nickname = nullif(left(public.sanitize_nickname(nickname), 64), '')
where nickname is not null;
