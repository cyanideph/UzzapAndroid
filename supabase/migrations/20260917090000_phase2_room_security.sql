-- Uzzap Phase 2: Room Security P0

create or replace function public.is_active_room_member(p_room_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.room_participants rp
    where rp.room_name = p_room_name
      and rp.owner_id = (select auth.uid())
      and rp.last_ping >= now() - interval '45 seconds'
  );
$$;
revoke all on function public.is_active_room_member(text) from public, anon, authenticated;

create or replace function public.lock_room_participant_identity()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if TG_OP = 'UPDATE' then
    new.room_name := old.room_name;
    new.username := old.username;
    new.owner_id := old.owner_id;
  end if;
  return new;
end;
$$;
revoke all on function public.lock_room_participant_identity() from public, anon, authenticated;
drop trigger if exists trg_lock_room_participant_identity on public.room_participants;
create trigger trg_lock_room_participant_identity
before update on public.room_participants
for each row execute function public.lock_room_participant_identity();

drop policy if exists participants_insert_own on public.room_participants;
create policy participants_insert_own on public.room_participants
for insert to authenticated
with check (
  owner_id = (select auth.uid())
  and exists (
    select 1 from public.chat_rooms r
    where r.name = room_name
      and (r.is_moderator_only = false or (select public.is_moderator()))
  )
  and not exists (
    select 1 from public.room_sanctions s
    where s.user_id = (select auth.uid())
      and s.room_name = room_participants.room_name
      and s.sanction = 'kick'
      and s.active = true
      and (s.expires_at is null or s.expires_at > now())
  )
);

drop policy if exists participants_read on public.room_participants;
create policy participants_read on public.room_participants
for select to authenticated
using (
  last_ping >= now() - interval '45 seconds'
  and ((select public.is_moderator()) or (select public.is_active_room_member(room_name)))
);

drop policy if exists participants_update_own on public.room_participants;
create policy participants_update_own on public.room_participants
for update to authenticated
using (owner_id = (select auth.uid()))
with check (owner_id = (select auth.uid()));

drop policy if exists participants_delete_own on public.room_participants;
create policy participants_delete_own on public.room_participants
for delete to authenticated
using (owner_id = (select auth.uid()));

drop policy if exists room_messages_read on public.room_messages;
create policy room_messages_read on public.room_messages
for select to authenticated
using ((select public.is_moderator()) or (select public.is_active_room_member(room_name)));

drop policy if exists room_messages_insert_auth on public.room_messages;
create policy room_messages_insert_auth on public.room_messages
for insert to authenticated
with check (
  sender_id = (select auth.uid())
  and is_system = false
  and exists (
    select 1 from public.chat_rooms r
    where r.name = room_name
      and (r.is_moderator_only = false or (select public.is_moderator()))
  )
  and (select public.is_active_room_member(room_name))
  and not exists (
    select 1 from public.room_sanctions s
    where s.user_id = (select auth.uid())
      and s.room_name = room_messages.room_name
      and s.sanction in ('mute','kick')
      and s.active = true
      and (s.expires_at is null or s.expires_at > now())
  )
);

create or replace function public.enforce_room_capacity()
returns trigger
language plpgsql
set search_path = ''
as $$
declare v_capacity integer; v_active_count integer;
begin
  perform pg_advisory_xact_lock(hashtextextended(new.room_name, 0));
  select capacity into v_capacity from public.chat_rooms where name = new.room_name;
  if v_capacity is null then raise exception 'ROOM_NOT_FOUND'; end if;
  select count(*) into v_active_count
  from public.room_participants
  where room_name = new.room_name
    and last_ping >= now() - interval '45 seconds'
    and username <> new.username;
  if v_active_count >= v_capacity then raise exception 'ROOM_FULL'; end if;
  return new;
end;
$$;

revoke all on function public.cleanup_stale_room_participants() from public, anon, authenticated;
revoke all on function public.enforce_room_capacity() from public, anon, authenticated;
revoke all on function public.lock_room_participant_identity() from public, anon, authenticated;
