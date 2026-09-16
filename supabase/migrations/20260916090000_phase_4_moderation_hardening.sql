-- Phase 4: moderation hardening.
-- Keep moderation authorization server-side and preserve an auditable action trail.

-- Reports are authenticated-user data, never anonymous/public data.
drop policy if exists user_reports_insert_own on public.user_reports;
drop policy if exists user_reports_read_own_or_moderator on public.user_reports;
create policy user_reports_insert_own on public.user_reports
for insert to authenticated
with check (reporter_id = (select auth.uid()));
create policy user_reports_read_own_or_moderator on public.user_reports
for select to authenticated
using (reporter_id = (select auth.uid()) or public.is_moderator());

-- Centralized role hierarchy: admins can moderate anyone; moderators cannot moderate admins
-- and nobody can apply sanctions to their own account.
create or replace function public.can_moderate_target(p_target_id uuid)
returns boolean
language plpgsql
stable
set search_path = public
as $$
declare target_role text;
begin
  if (select auth.uid()) is null or p_target_id is null then return false; end if;
  if p_target_id = (select auth.uid()) then return false; end if;
  if public.is_admin() then return true; end if;
  if not public.is_moderator() then return false; end if;
  select role into target_role from public.moderator_roles where user_id=p_target_id limit 1;
  return coalesce(target_role,'user') <> 'admin';
end;
$$;
revoke all on function public.can_moderate_target(uuid) from public;
grant execute on function public.can_moderate_target(uuid) to authenticated;

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
  if p_action not in ('mute','kick','unmute','unkick','warn','ban','unban') then raise exception 'invalid_moderation_action'; end if;
  if p_duration_minutes is not null and p_duration_minutes < 0 then raise exception 'invalid_duration'; end if;
  select owner_id into target_id from public.profiles where lower(username)=lower(trim(p_username)) limit 1;
  if target_id is null then return false; end if;
  if not public.can_moderate_target(target_id) then raise exception 'moderation_target_protected'; end if;
  expiry := case when p_duration_minutes is null or p_duration_minutes <= 0 then null else now()+make_interval(mins=>p_duration_minutes) end;
  insert into public.moderation_actions(moderator_id,target_user_id,target_username,action,action_type,room_name,reason,duration_minutes,expires_at)
  values ((select auth.uid()),target_id,p_username,p_action,p_action,p_room_name,left(coalesce(p_reason,''),500),p_duration_minutes,expiry);
  return true;
end;
$$;

-- The trigger must be able to materialize sanctions while direct client writes remain blocked.
create or replace function public.apply_moderation_action_trigger()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare resolved_target uuid;
begin
  resolved_target := new.target_user_id;
  if resolved_target is null then
    select owner_id into resolved_target from public.profiles where lower(username)=lower(new.target_username) limit 1;
  end if;
  if new.action_type in ('mute','kick','ban') and resolved_target is not null then
    insert into public.room_sanctions(user_id,username,room_name,sanction,expires_at,reason,created_by)
    values(resolved_target,new.target_username,new.room_name,new.action_type,new.expires_at,left(coalesce(new.reason,''),500),new.moderator_id);
  elsif new.action_type in ('unmute','unkick','unban') and resolved_target is not null then
    update public.room_sanctions set active=false
    where user_id=resolved_target and sanction=case new.action_type when 'unmute' then 'mute' when 'unkick' then 'kick' else 'ban' end
      and active=true and (new.room_name is null or room_name=new.room_name);
  end if;
  return new;
end;
$$;
revoke all on function public.apply_moderation_action_trigger() from public;

-- Only the trigger/RPC path can change sanctions; moderators can read current sanctions.
drop policy if exists room_sanctions_mod_write on public.room_sanctions;
drop policy if exists room_sanctions_mod_read on public.room_sanctions;
create policy room_sanctions_mod_read on public.room_sanctions
for select to authenticated using (public.is_moderator());

create or replace function public.revoke_moderation_action(p_action_id bigint)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare a public.moderation_actions%rowtype;
begin
  if not public.is_moderator() then raise exception 'moderator_required'; end if;
  select * into a from public.moderation_actions where id=p_action_id;
  if not found then return false; end if;
  if not public.can_moderate_target(a.target_user_id) then raise exception 'moderation_target_protected'; end if;
  if a.action_type in ('mute','kick','ban') then
    update public.room_sanctions set active=false
    where user_id=a.target_user_id and sanction=a.action_type and active=true
      and (a.room_name is null or room_name=a.room_name);
    return true;
  end if;
  return false;
end;
$$;
revoke all on function public.revoke_moderation_action(bigint) from public;
grant execute on function public.revoke_moderation_action(bigint) to authenticated;

create or replace function public.moderate_room_message(p_message_id bigint, p_reason text default '')
returns boolean
language plpgsql
set search_path = public
as $$
declare m public.room_messages%rowtype;
begin
  if not public.is_moderator() then raise exception 'moderator_required'; end if;
  select * into m from public.room_messages where id=p_message_id;
  if not found then return false; end if;
  if not public.can_moderate_target(m.sender_id) then raise exception 'moderation_target_protected'; end if;
  delete from public.room_messages where id=p_message_id;
  insert into public.moderation_actions(moderator_id,target_user_id,target_username,action,action_type,room_name,message_id,reason)
  values((select auth.uid()),m.sender_id,m.sender,'delete_message','delete_message',m.room_name,p_message_id,left(coalesce(p_reason,''),500));
  return true;
end;
$$;

revoke execute on function public.moderate_user(text,text,text,integer,text) from public;
grant execute on function public.moderate_user(text,text,text,integer,text) to authenticated;
revoke execute on function public.moderate_room_message(bigint,text) from public;
grant execute on function public.moderate_room_message(bigint,text) to authenticated;
revoke execute on function public.resolve_user_report(bigint,text,text) from public;
grant execute on function public.resolve_user_report(bigint,text,text) to authenticated;
