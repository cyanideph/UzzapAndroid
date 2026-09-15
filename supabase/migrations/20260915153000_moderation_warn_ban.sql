-- Extend moderation to support warn and global/room ban.
-- Feature branch only; not applied to production.

alter table public.moderation_actions drop constraint if exists moderation_actions_action_check;
alter table public.moderation_actions add constraint moderation_actions_action_check
  check (action = any (array['mute','kick','unmute','unkick','warn','ban','unban','delete_message','resolve_report']));

alter table public.moderation_actions drop constraint if exists moderation_actions_action_type_check;
alter table public.moderation_actions add constraint moderation_actions_action_type_check
  check (action_type = any (array['mute','kick','unmute','unkick','warn','ban','unban','delete_message','resolve_report']));

alter table public.room_sanctions drop constraint if exists room_sanctions_sanction_check;
alter table public.room_sanctions add constraint room_sanctions_sanction_check
  check (sanction = any (array['mute','kick','ban']));

create or replace function public.sync_moderation_action()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if new.action_type is null and new.action is not null then new.action_type := new.action; end if;
  if new.action is null and new.action_type is not null then new.action := new.action_type; end if;
  if new.action_type is null then raise exception 'action_type_required'; end if;
  if new.action_type not in ('mute','kick','unmute','unkick','warn','ban','unban','delete_message','resolve_report') then
    raise exception 'invalid_moderation_action';
  end if;
  return new;
end;
$$;

create or replace function public.moderate_user(
  p_username text,
  p_action text,
  p_room_name text default null,
  p_duration_minutes integer default null,
  p_reason text default ''
)
returns boolean
language plpgsql
set search_path = public
as $$
declare target_id uuid; expiry timestamptz;
begin
  if not public.is_moderator() then raise exception 'moderator_required'; end if;
  if p_action not in ('mute','kick','unmute','unkick','warn','ban','unban') then
    raise exception 'invalid_moderation_action';
  end if;

  select owner_id into target_id
  from public.profiles
  where lower(username)=lower(p_username)
  limit 1;
  if target_id is null then return false; end if;

  expiry := case when p_duration_minutes is null or p_duration_minutes <= 0
                 then null
                 else now()+make_interval(mins=>p_duration_minutes) end;

  insert into public.moderation_actions(
    moderator_id,target_user_id,target_username,action,action_type,room_name,
    reason,duration_minutes,expires_at
  ) values (
    auth.uid(),target_id,p_username,p_action,p_action,p_room_name,
    coalesce(p_reason,''),p_duration_minutes,expiry
  );
  return true;
end;
$$;

create or replace function public.apply_moderation_action_trigger()
returns trigger
language plpgsql
set search_path = public
as $$
declare resolved_target uuid;
begin
  resolved_target := new.target_user_id;
  if resolved_target is null then
    select owner_id into resolved_target
    from public.profiles
    where lower(username)=lower(new.target_username)
    limit 1;
  end if;

  if new.action_type in ('mute','kick','ban') and resolved_target is not null then
    insert into public.room_sanctions(
      user_id,username,room_name,sanction,expires_at,reason,created_by
    ) values (
      resolved_target,new.target_username,new.room_name,new.action_type,
      new.expires_at,coalesce(new.reason,''),new.moderator_id
    );
  elsif new.action_type in ('unmute','unkick','unban') and resolved_target is not null then
    update public.room_sanctions
       set active=false
     where user_id=resolved_target
       and sanction=case new.action_type
         when 'unmute' then 'mute'
         when 'unkick' then 'kick'
         else 'ban'
       end
       and active=true
       and (new.room_name is null or room_name=new.room_name);
  end if;
  return new;
end;
$$;

create or replace function public.revoke_moderation_action(p_action_id bigint)
returns boolean
language plpgsql
set search_path = public
as $$
declare a public.moderation_actions%rowtype;
begin
  if not public.is_moderator() then raise exception 'moderator_required'; end if;
  select * into a from public.moderation_actions where id=p_action_id;
  if not found then return false; end if;

  if a.action_type in ('mute','kick','ban') then
    update public.room_sanctions
       set active=false
     where user_id=a.target_user_id
       and sanction=a.action_type
       and active=true
       and (a.room_name is null or room_name=a.room_name);
    return true;
  end if;
  return false;
end;
$$;

-- A ban with no room scope blocks the account from all rooms; a room-scoped ban blocks that room.
drop policy if exists participants_insert_own on public.room_participants;
create policy participants_insert_own on public.room_participants
for insert to authenticated
with check (
  owner_id = (select auth.uid())
  and exists (
    select 1 from public.chat_rooms r
    where r.name = room_name
      and (not r.is_moderator_only or public.is_moderator())
  )
  and not exists (
    select 1 from public.room_sanctions s
    where s.user_id=(select auth.uid())
      and s.active=true
      and (s.expires_at is null or s.expires_at > now())
      and (
        s.sanction in ('kick','ban')
        and (s.room_name is null or s.room_name=room_name)
      )
  )
);

drop policy if exists room_messages_insert_auth on public.room_messages;
create policy room_messages_insert_auth on public.room_messages
for insert to authenticated
with check (
  sender_id=(select auth.uid())
  and is_system=false
  and exists (
    select 1 from public.chat_rooms r
    where r.name=room_name
      and (not r.is_moderator_only or public.is_moderator())
  )
  and not exists (
    select 1 from public.room_sanctions s
    where s.user_id=(select auth.uid())
      and s.active=true
      and (s.expires_at is null or s.expires_at > now())
      and (
        s.sanction in ('mute','kick','ban')
        and (s.room_name is null or s.room_name=room_name)
      )
  )
);
