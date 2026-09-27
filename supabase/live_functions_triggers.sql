-- LIVE SUPABASE FUNCTIONS AND TRIGGERS SNAPSHOT
-- Project: UZZAPANDROID_BACKEND (hrjgblvxhtbopuyzyasu)
-- Generated: 2026-09-27
-- SCHEMA ONLY. No production rows or secrets.

-- FUNCTIONS
CREATE OR REPLACE FUNCTION public.apply_moderation_action_trigger()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  resolved_target uuid;
begin
  resolved_target := new.target_user_id;
  if resolved_target is null then
    select owner_id into resolved_target
    from public.profiles
    where lower(username)=lower(new.target_username)
    limit 1;
  end if;

  if new.action_type in ('mute','kick','ban') and resolved_target is not null then
    insert into public.room_sanctions(user_id,username,room_name,sanction,expires_at,reason,created_by)
    values(resolved_target,new.target_username,new.room_name,new.action_type,new.expires_at,left(coalesce(new.reason,''),500),new.moderator_id);
  elsif new.action_type in ('unmute','unkick','unban') and resolved_target is not null then
    update public.room_sanctions
    set active=false
    where user_id=resolved_target
      and sanction=case new.action_type when 'unmute' then 'mute' when 'unkick' then 'kick' else 'ban' end
      and active=true
      and (new.room_name is null or room_name=new.room_name);
  end if;
  return new;
end;
$function$

CREATE OR REPLACE FUNCTION public.can_moderate_target(p_target_id uuid)
 RETURNS boolean
 LANGUAGE plpgsql
 STABLE
 SET search_path TO 'public'
AS $function$
declare target_role text;
begin
  if (select auth.uid()) is null or p_target_id is null then return false; end if;
    if p_target_id = (select auth.uid()) then
        return public.is_moderator();
          end if;
            if public.is_admin() then return true; end if;
              if not public.is_moderator() then return false; end if;
                select role into target_role from public.moderator_roles where user_id=p_target_id limit 1;
                  return coalesce(target_role,'user') <> 'admin';
                  end;
                  $function$

CREATE OR REPLACE FUNCTION public.cleanup_stale_room_participants()
 RETURNS void
 LANGUAGE sql
 SET search_path TO ''
AS $function$
  delete from public.room_participants
  where last_ping < now() - interval '45 seconds';
$function$

CREATE OR REPLACE FUNCTION public.create_room_invitation(p_room_name text, p_invitee_username text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  inviter uuid := (select auth.uid());
  invitee uuid;
  room_ok boolean;
  inviter_name text;
  invitee_name text;
  inviter_allowed boolean;
begin
  if inviter is null then
    raise exception 'authentication_required';
  end if;

  select p.username into inviter_name
  from public.profiles p
  where p.owner_id = inviter
  limit 1;

  if inviter_name is null then
    raise exception 'profile_not_found';
  end if;

  select exists (
    select 1 from public.chat_rooms
    where name = trim(p_room_name)
  ) into room_ok;

  if not room_ok then
    return false;
  end if;

  select
    exists (
      select 1
      from public.room_participants rp
      where rp.room_name = trim(p_room_name)
        and rp.owner_id = inviter
        and rp.last_ping >= now() - interval '45 seconds'
    )
    or public.is_moderator()
  into inviter_allowed;

  if not inviter_allowed then
    return false;
  end if;

  select owner_id, username into invitee, invitee_name
  from public.profiles
  where lower(username) = lower(trim(p_invitee_username))
  limit 1;

  if invitee is null or invitee = inviter then
    return false;
  end if;

  insert into public.room_invitations(
    room_name, inviter_id, invitee_id, inviter_username, invitee_username
  ) values (
    trim(p_room_name), inviter, invitee, inviter_name, invitee_name
  ) on conflict do nothing;

  return true;
end;
$function$

CREATE OR REPLACE FUNCTION public.delete_my_account()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  uid uuid := auth.uid();
  r record;
begin
  if uid is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;

  for r in
    select
      n.nspname as schema_name,
      c.relname as table_name,
      a.attname as column_name,
      a.attnotnull as not_null,
      con.confdeltype as delete_action
    from pg_catalog.pg_constraint con
    join pg_catalog.pg_class c on c.oid = con.conrelid
    join pg_catalog.pg_namespace n on n.oid = c.relnamespace
    join pg_catalog.pg_class p on p.oid = con.confrelid
    join pg_catalog.pg_namespace pn on pn.oid = p.relnamespace
    join pg_catalog.pg_attribute a on a.attrelid = c.oid and a.attnum = con.conkey[1]
    where con.contype = 'f'
      and pn.nspname = 'auth'
      and p.relname = 'users'
      and n.nspname = 'public'
      and con.confdeltype = 'a'
  loop
    if r.not_null then
      execute format('delete from %I.%I where %I = $1', r.schema_name, r.table_name, r.column_name) using uid;
    else
      execute format('update %I.%I set %I = null where %I = $1', r.schema_name, r.table_name, r.column_name, r.column_name) using uid;
    end if;
  end loop;

  delete from public.direct_messages where sender_id = uid or recipient_id = uid;
  delete from public.room_messages where sender_id = uid;
  delete from public.user_reports where reporter_id = uid;
  delete from public.buddies where owner_id = uid;
  delete from public.blocked_users where owner_id = uid;
  delete from public.room_participants where owner_id = uid;
  delete from public.room_sanctions where user_id = uid or created_by = uid;
  delete from public.user_fcm_tokens where user_id = uid;
  delete from public.room_invitations where inviter_id = uid or invitee_id = uid;

  delete from public.uzzapbot_memory where user_id = uid;
  delete from public.uzzapbot_player_achievements where user_id = uid;
  delete from public.uzzapbot_player_activity where user_id = uid;
  delete from public.uzzapbot_player_stats where user_id = uid;
  delete from public.uzzapbot_player_titles where user_id = uid;
  delete from public.uzzapbot_powerup_uses where user_id = uid;
  delete from public.uzzapbot_powerups where user_id = uid;
  delete from public.uzzapbot_daily_challenges where user_id = uid;
  delete from public.uzzapbot_xp_history where user_id = uid;
  delete from public.uzzapbot_battles where challenger_id = uid or challenged_id = uid;
  delete from public.uzzapbot_room_challenges where creator_id = uid;
  delete from public.uzzapbot_tournaments where creator_id = uid;

  delete from public.profiles where owner_id = uid;
  delete from public.moderator_roles where user_id = uid;

  delete from auth.users where id = uid;

  if not found then
    raise exception 'account_not_found' using errcode = 'P0002';
  end if;
end;
$function$

CREATE OR REPLACE FUNCTION public.enforce_direct_message_rate_limit()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  uid uuid := (select auth.uid());
  recent_short integer;
  recent_minute integer;
begin
  if uid is null then return new; end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(uid::text, 1));

  select count(*) into recent_short
  from public.direct_messages
  where sender_id = uid
    and created_at > now() - interval '10 seconds';

  if recent_short >= 5 then
    raise exception using errcode = 'P0001', message = 'DIRECT_MESSAGE_RATE_LIMIT_10S';
  end if;

  select count(*) into recent_minute
  from public.direct_messages
  where sender_id = uid
    and created_at > now() - interval '1 minute';

  if recent_minute >= 30 then
    raise exception using errcode = 'P0001', message = 'DIRECT_MESSAGE_RATE_LIMIT_1M';
  end if;

  return new;
end;
$function$

CREATE OR REPLACE FUNCTION public.enforce_message_rate_limit()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  uid uuid := (select auth.uid());
  recent_short integer;
  recent_minute integer;
begin
  if uid is null then return new; end if;

  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(uid::text, 0));

  select count(*) into recent_short
  from public.room_messages
  where sender_id = uid
    and created_at > now() - interval '10 seconds';

  if recent_short >= 5 then
    raise exception using errcode = 'P0001', message = 'MESSAGE_RATE_LIMIT_10S';
  end if;

  select count(*) into recent_minute
  from public.room_messages
  where sender_id = uid
    and created_at > now() - interval '1 minute';

  if recent_minute >= 30 then
    raise exception using errcode = 'P0001', message = 'MESSAGE_RATE_LIMIT_1M';
  end if;

  return new;
end;
$function$

CREATE OR REPLACE FUNCTION public.enforce_room_capacity()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare
  v_capacity integer;
  v_active_count integer;
begin
  perform pg_advisory_xact_lock(hashtextextended(new.room_name, 0));

  select capacity into v_capacity
  from public.chat_rooms
  where name = new.room_name;

  if v_capacity is null then
    raise exception 'ROOM_NOT_FOUND';
  end if;

  select count(*) into v_active_count
  from public.room_participants
  where room_name = new.room_name
    and last_ping >= now() - interval '45 seconds'
    and username <> new.username;

  if v_active_count >= v_capacity then
    raise exception 'ROOM_FULL';
  end if;

  return new;
end;
$function$

CREATE OR REPLACE FUNCTION public.get_live_room_counts()
 RETURNS TABLE(room_name text, user_count bigint)
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$
    select rp.room_name, count(*)::bigint
    from public.room_participants rp
    where rp.last_ping >= now() - interval '45 seconds'
    group by rp.room_name
    order by rp.room_name;
$function$

CREATE OR REPLACE FUNCTION public.get_online_user_count()
 RETURNS TABLE(user_count bigint)
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$
    select count(*)::bigint
    from public.profiles p
    where p.is_online = true;
$function$

CREATE OR REPLACE FUNCTION public.handle_new_user()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  base_username text;
  final_username text;
  requested_nickname text;
  requested_email text;
  suffix text;
begin
  requested_email := new.email;
  requested_nickname := coalesce(nullif(new.raw_user_meta_data ->> 'nickname', ''), split_part(coalesce(new.email, 'user'), '@', 1));
  base_username := coalesce(nullif(lower(regexp_replace(new.raw_user_meta_data ->> 'username', '[^a-zA-Z0-9_]+', '_', 'g')), ''), 'user_' || left(new.id::text, 8));
  final_username := left(base_username, 40);

  if exists (select 1 from public.profiles p where p.username = final_username and p.owner_id is distinct from new.id) then
    suffix := '_' || left(new.id::text, 8);
    final_username := left(base_username, 50 - length(suffix)) || suffix;
  end if;

  insert into public.profiles (id, owner_id, username, nickname, email, status, status_message, avatar, is_online, last_seen)
  values (new.id, new.id, final_username, requested_nickname, requested_email, 'Available', 'New Uzzap user :)', 'kool', true, now())
  on conflict (id) do update set
    owner_id = excluded.owner_id,
    email = excluded.email,
    nickname = excluded.nickname;

  return new;
end;
$function$

CREATE OR REPLACE FUNCTION public.is_active_room_member(p_room_name text)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
  select exists (
    select 1
    from public.room_participants rp
    where rp.room_name = p_room_name
      and rp.owner_id = (select auth.uid())
      and rp.last_ping >= now() - interval '45 seconds'
  );
$function$

CREATE OR REPLACE FUNCTION public.is_admin()
 RETURNS boolean
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$
  select exists (
    select 1 from public.moderator_roles mr
    where mr.user_id = (select auth.uid()) and mr.role = 'admin'
  );
$function$

CREATE OR REPLACE FUNCTION public.is_moderator()
 RETURNS boolean
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$
  select exists (
    select 1 from public.moderator_roles mr
    where mr.user_id = (select auth.uid())
  );
$function$

CREATE OR REPLACE FUNCTION public.is_moderator_or_admin()
 RETURNS boolean
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$ select public.is_moderator(); $function$

CREATE OR REPLACE FUNCTION public.join_room(p_room_name text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := (select auth.uid());
  v_username text;
  v_is_mod boolean := false;
  v_room public.chat_rooms%rowtype;
  v_sanction boolean := false;
begin
  if v_uid is null then raise exception 'AUTH_REQUIRED'; end if;
  select p.username into v_username from public.profiles p where p.owner_id = v_uid limit 1;
  if v_username is null then raise exception 'PROFILE_NOT_FOUND'; end if;
  select r.* into v_room from public.chat_rooms r where r.name = p_room_name;
  if not found then raise exception 'ROOM_NOT_FOUND'; end if;
  select exists (select 1 from public.moderator_roles mr where mr.user_id = v_uid and mr.role in ('owner','admin','moderator')) into v_is_mod;
  if v_room.is_moderator_only and not v_is_mod then raise exception 'MODERATOR_ONLY_ROOM'; end if;
  select exists (select 1 from public.room_sanctions rs where rs.user_id = v_uid and rs.room_name = p_room_name and rs.sanction = 'kick' and rs.active = true and (rs.expires_at is null or rs.expires_at > now())) into v_sanction;
  if v_sanction then raise exception 'ROOM_ACCESS_DENIED'; end if;
  insert into public.room_participants(room_name, username, owner_id, last_ping)
  values (p_room_name, v_username, v_uid, now())
  on conflict (room_name, username) do update set last_ping = now(), owner_id = excluded.owner_id;
  return jsonb_build_object('success', true, 'room_name', p_room_name, 'username', v_username);
end;
$function$

CREATE OR REPLACE FUNCTION public.lock_room_participant_identity()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  if TG_OP = 'UPDATE' then
    new.room_name := old.room_name;
    new.username := old.username;
    new.owner_id := old.owner_id;
  end if;
  return new;
end;
$function$

CREATE OR REPLACE FUNCTION public.moderate_room_message(p_message_id bigint, p_reason text DEFAULT ''::text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  m public.room_messages%rowtype;
begin
  if (select auth.uid()) is null then
    raise exception 'authentication_required';
  end if;

  if not public.is_moderator() then
    raise exception 'moderator_required';
  end if;

  select * into m
  from public.room_messages
  where id = p_message_id
  for update;

  if not found then
    return false;
  end if;

  if not public.can_moderate_target(m.sender_id) then
    raise exception 'moderation_target_protected';
  end if;

  -- Record the moderation action while the message still exists.
  -- moderation_actions.message_id has ON DELETE SET NULL, so the audit row
  -- remains after the message is removed.
  insert into public.moderation_actions(
    moderator_id,
    target_user_id,
    target_username,
    action,
    action_type,
    room_name,
    message_id,
    reason
  ) values (
    (select auth.uid()),
    m.sender_id,
    m.sender,
    'delete_message',
    'delete_message',
    m.room_name,
    p_message_id,
    left(coalesce(p_reason,''),500)
  );

  delete from public.room_messages
  where id = p_message_id;

  return true;
end;
$function$

CREATE OR REPLACE FUNCTION public.moderate_user(p_username text, p_action text, p_room_name text DEFAULT NULL::text, p_duration_minutes integer DEFAULT NULL::integer, p_reason text DEFAULT ''::text)
 RETURNS boolean
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$
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
$function$

CREATE OR REPLACE FUNCTION public.notify_buddy_request_push()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  perform net.http_post(
    url := 'https://hrjgblvxhtbopuyzyasu.supabase.co/functions/v1/send-push',
    body := jsonb_build_object(
      'type','INSERT',
      'table','buddy_requests',
      'schema','public',
      'record',jsonb_build_object(
        'id',new.id,
        'sender',new.requester_handle,
        'recipient',new.recipient_handle,
        'body',new.requester_handle || ' sent you a buddy request',
        'created_at',new.created_at
      ),
      'old_record',null
    ),
    headers := '{"Content-Type":"application/json"}'::jsonb,
    timeout_milliseconds := 1000
  );
  return new;
end;
$function$

CREATE OR REPLACE FUNCTION public.notify_direct_message_push()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  perform net.http_post(
    url := 'https://hrjgblvxhtbopuyzyasu.supabase.co/functions/v1/send-push',
    body := jsonb_build_object(
      'type','INSERT',
      'table','direct_messages',
      'schema','public',
      'record',to_jsonb(new),
      'old_record',null
    ),
    headers := '{"Content-Type":"application/json"}'::jsonb,
    timeout_milliseconds := 1000
  );
  return new;
end;
$function$

CREATE OR REPLACE FUNCTION public.notify_room_invitation_push()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  perform net.http_post(
    url := 'https://hrjgblvxhtbopuyzyasu.supabase.co/functions/v1/send-push',
    body := jsonb_build_object(
      'type','INSERT',
      'table','room_invitations',
      'schema','public',
      'record',to_jsonb(new),
      'old_record',null
    ),
    headers := '{"Content-Type":"application/json"}'::jsonb,
    timeout_milliseconds := 1000
  );
  return new;
end;
$function$

CREATE OR REPLACE FUNCTION public.resolve_login_email(p_username text)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_email text;
begin
  select p.email
    into v_email
  from public.profiles as p
  where lower(p.username) = lower(trim(p_username))
  limit 1;
  return v_email;
end;
$function$

CREATE OR REPLACE FUNCTION public.resolve_user_report(p_report_id bigint, p_status text, p_reason text DEFAULT ''::text)
 RETURNS boolean
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$
declare r public.user_reports%rowtype; target_id uuid;
begin
  if not public.is_moderator() then raise exception 'moderator_required'; end if;
  if p_status not in ('reviewing','resolved','dismissed') then raise exception 'invalid_report_status'; end if;
  update public.user_reports set status=p_status, reviewed_by=(select auth.uid()), reviewed_at=now(), details=case when p_reason='' then details else details || E'\n' || p_reason end where id=p_report_id returning * into r;
  if not found then return false; end if;
  select owner_id into target_id from public.profiles where lower(username)=lower(r.reported_username) limit 1;
  insert into public.moderation_actions(moderator_id,target_user_id,target_username,action,room_name,message_id,reason)
  values ((select auth.uid()),target_id,r.reported_username,'resolve_report',r.room_name,r.message_id,p_reason);
  return true;
end;
$function$

CREATE OR REPLACE FUNCTION public.respond_room_invitation(p_invitation_id bigint, p_accepted boolean)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  inv public.room_invitations%rowtype;
  join_result jsonb;
begin
  if (select auth.uid()) is null then
    raise exception 'authentication_required';
  end if;

  select * into inv
  from public.room_invitations
  where id = p_invitation_id
    and invitee_id = (select auth.uid())
    and status = 'pending'
  for update;

  if not found then
    return false;
  end if;

  if p_accepted then
    join_result := public.join_room(inv.room_name);
    if coalesce((join_result ->> 'success')::boolean, false) is not true then
      raise exception 'room_join_failed';
    end if;

    update public.room_invitations
      set status = 'accepted', responded_at = now()
      where id = inv.id;
  else
    update public.room_invitations
      set status = 'declined', responded_at = now()
      where id = inv.id;
  end if;

  return true;
end;
$function$

CREATE OR REPLACE FUNCTION public.respond_to_buddy_request(p_request_id bigint, p_accept boolean)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare r public.buddy_requests%rowtype; requester_owner uuid;
begin
  select * into r from public.buddy_requests where id = p_request_id and status = 'pending' for update;
  if not found then return false; end if;
  if not exists (select 1 from public.profiles p where p.owner_id = auth.uid() and lower(p.username) = lower(r.recipient_handle)) then raise exception 'not allowed'; end if;
  if p_accept then
    insert into public.buddies(user_handle, buddy_handle, group_name, owner_id)
      values (r.recipient_handle, r.requester_handle, 'My Friends', auth.uid())
      on conflict do nothing;
    select owner_id into requester_owner from public.profiles where lower(username)=lower(r.requester_handle) limit 1;
    insert into public.buddies(user_handle, buddy_handle, group_name, owner_id)
      values (r.requester_handle, r.recipient_handle, 'My Friends', requester_owner)
      on conflict do nothing;
  end if;
  update public.buddy_requests set status = case when p_accept then 'accepted' else 'rejected' end, responded_at = now() where id = p_request_id;
  return true;
end;
$function$

CREATE OR REPLACE FUNCTION public.revoke_moderation_action(p_action_id bigint)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
$function$

CREATE OR REPLACE FUNCTION public.room_bot_message(p_room text, p_body text, p_is_system boolean DEFAULT true)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_body text;
begin
  v_body := coalesce(p_body,'');
  for i in 1..10 loop
    v_body := regexp_replace(
      v_body,
      '^\s*(\[[^\]]+\])?\s*(🤖\s*)?(UzzapBot|Uzzap\s*Bot|bot)\s*([:\-–—]\s*)?(\r?\n\s*)?',
      '',
      'i'
    );
  end loop;
  insert into public.room_messages (room_name, sender, body, is_system, sender_id)
  values (p_room, 'UzzapBot', trim(v_body), coalesce(p_is_system, true), null);
end;
$function$

CREATE OR REPLACE FUNCTION public.room_participant_join_bot()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  display_name text;
  welcome_text text;
  enabled boolean;
begin
  select coalesce(rs.wcbot,false) into enabled
  from public.uzzapbot_room_settings rs
  where rs.room_name=new.room_name;
  if coalesce(enabled,false) = false then
    return new;
  end if;

  select coalesce(nullif(public.sanitize_nickname(p.nickname), ''), p.username, new.username)
    into display_name
  from public.profiles p
  where p.owner_id = new.owner_id
  limit 1;

  select coalesce(nullif(rs.welcome_message,''),'welcome to {room} {nickname}')
    into welcome_text
  from public.uzzapbot_room_settings rs
  where rs.room_name=new.room_name;

  welcome_text := replace(welcome_text,'{room}',new.room_name);
  welcome_text := replace(welcome_text,'{nickname}',coalesce(display_name,new.username));
  welcome_text := replace(welcome_text,'{username}',new.username);

  perform public.room_bot_message(new.room_name,welcome_text,true);
  return new;
end;
$function$

CREATE OR REPLACE FUNCTION public.room_participant_leave_bot()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  if old.last_ping >= now() - interval '45 seconds' then
    perform public.room_bot_message(
      old.room_name,
      old.username || ' left the room. ' || public.uzzapbot_random_emoticon()
    );
  end if;
  return old;
end;
$function$

CREATE OR REPLACE FUNCTION public.sanitize_nickname(p_nickname text)
 RETURNS text
 LANGUAGE plpgsql
 IMMUTABLE
 SET search_path TO ''
AS $function$
declare
  v text := coalesce(p_nickname, '');
begin
  v := regexp_replace(v, '\[c[0-9]{2}\]? ?', '', 'gi');
  if v like '%]' and p_nickname ~* '\[c[0-9]{2}' then
    v := left(v, length(v) - 1);
  end if;
  return btrim(v);
end;
$function$

CREATE OR REPLACE FUNCTION public.set_buddy_owner()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$ begin if TG_OP = 'INSERT' then if auth.uid() is null then raise exception 'Authentication required'; end if; new.owner_id := auth.uid(); new.user_handle := coalesce((select p.username from public.profiles p where p.owner_id = auth.uid() limit 1), 'user_' || left(auth.uid()::text, 8)); end if; return new; end; $function$

CREATE OR REPLACE FUNCTION public.set_moderator_by_email(target_email text, make_moderator boolean)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$ declare target_id uuid; begin if not public.is_admin() then raise exception 'admin_required'; end if; select id into target_id from auth.users where lower(email)=lower(trim(target_email)) limit 1; if target_id is null then return false; end if; if make_moderator then insert into public.moderator_roles(user_id,role,created_by) values(target_id,'moderator',auth.uid()) on conflict(user_id) do update set role='moderator',created_by=auth.uid(); else delete from public.moderator_roles where user_id=target_id; end if; return true; end; $function$

CREATE OR REPLACE FUNCTION public.set_participant_owner()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
  NEW.owner_id := auth.uid();
  NEW.username := COALESCE((SELECT p.username FROM public.profiles p WHERE p.owner_id = auth.uid() LIMIT 1), 'user_' || left(auth.uid()::text, 8));
  NEW.last_ping := now();
  RETURN NEW;
END;
$function$

CREATE OR REPLACE FUNCTION public.set_profile_nickname_plain()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  new.nickname := nullif(left(public.sanitize_nickname(new.nickname), 64), '');
  return new;
end;
$function$

CREATE OR REPLACE FUNCTION public.set_room_message_sender()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$ declare actor uuid := auth.uid(); display_name text; request_role text := coalesce(current_setting('request.jwt.claim.role', true), (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role')); begin if actor is null then if request_role = 'service_role' then new.sender_id := null; new.sender := coalesce(nullif(new.sender, ''), 'UzzapBot'); new.is_system := coalesce(new.is_system, false); return new; end if; if coalesce(new.is_system, false) then return new; end if; raise exception 'Authentication required'; end if; if coalesce(new.is_system, false) then return new; end if; new.sender_id := actor; select coalesce(nullif(public.sanitize_nickname(p.nickname), ''), p.username, 'user_' || left(actor::text, 8)) into display_name from public.profiles p where p.owner_id = actor limit 1; new.sender := coalesce(display_name, 'user_' || left(actor::text, 8)); new.is_system := false; return new; end; $function$

CREATE OR REPLACE FUNCTION public.sync_moderation_action()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$ begin if new.action_type is null and new.action is not null then new.action_type:=new.action; end if; if new.action is null and new.action_type is not null then new.action:=new.action_type; end if; if new.action_type is null then raise exception 'action_type_required'; end if; if new.action_type not in ('mute','kick','unmute','unkick','warn','ban','unban','delete_message','resolve_report') then raise exception 'invalid_moderation_action'; end if; return new; end; $function$

CREATE OR REPLACE FUNCTION public.touch_my_presence(p_is_online boolean DEFAULT true)
 RETURNS void
 LANGUAGE sql
 SET search_path TO 'public'
AS $function$ update public.profiles set is_online=p_is_online,last_seen=now() where owner_id=auth.uid(); $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_activity_tick()
 RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_token text;
  v_req bigint;
BEGIN
  SELECT decrypted_secret INTO v_token
  FROM vault.decrypted_secrets
  WHERE name='uzzapbot_dispatch_token'
  LIMIT 1;
  IF v_token IS NULL THEN
    RAISE EXCEPTION 'UzzapBot dispatch secret is unavailable';
  END IF;
  SELECT net.http_post(
    url := 'https://hrjgblvxhtbopuyzyasu.supabase.co/functions/v1/uzzapbot',
    headers := jsonb_build_object('Content-Type','application/json','x-uzzapbot-internal-token',v_token),
    body := jsonb_build_object('action','activity_tick')
  ) INTO v_req;
  RETURN v_req;
END;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_add_game_player(p_session_id bigint, p_user_id uuid, p_username text, p_nickname text)
 RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE v_id bigint; v_room text;
BEGIN
 IF p_session_id IS NULL OR p_user_id IS NULL THEN RAISE EXCEPTION 'session id and user id are required'; END IF;
 SELECT room_name INTO v_room FROM public.game_sessions WHERE id=p_session_id;
 IF v_room IS NULL THEN RAISE EXCEPTION 'game session not found'; END IF;
 INSERT INTO public.game_players(session_id,user_id,username,nickname,score,correct,attempts,clues_used)
 VALUES(p_session_id,p_user_id,p_username,p_nickname,0,0,0,0)
 RETURNING id INTO v_id;
 RETURN v_id;
END $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_advance_cursor(p_previous_id bigint, p_message_id bigint)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  if auth.role() <> 'service_role' then
    raise exception 'service role required';
  end if;

  if p_message_id <= p_previous_id then
    return false;
  end if;

  update public.uzzapbot_cursor
     set last_message_id = p_message_id,
         updated_at = now()
   where bot_name = 'default'
     and last_message_id = p_previous_id;

  return found;
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_ai_admit(p_room text, p_min_interval_seconds integer DEFAULT 3, p_window_seconds integer DEFAULT 60, p_window_limit integer DEFAULT 8)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare r public.uzzapbot_ai_rate_state;
begin
  select * into r from public.uzzapbot_ai_rate_state where room_name=p_room for update;
  if not found then
    insert into public.uzzapbot_ai_rate_state(room_name,window_started_at,request_count,last_request_at)
    values(p_room,now(),1,now());
    return true;
  end if;
  if now() - r.window_started_at >= make_interval(secs => p_window_seconds) then
    update public.uzzapbot_ai_rate_state
      set window_started_at=now(),request_count=1,last_request_at=now(),updated_at=now()
      where room_name=p_room;
    return true;
  end if;
  if r.request_count >= p_window_limit then return false; end if;
  if r.last_request_at is not null and now() - r.last_request_at < make_interval(secs => p_min_interval_seconds) then return false; end if;
  update public.uzzapbot_ai_rate_state
    set request_count=request_count+1,last_request_at=now(),updated_at=now()
    where room_name=p_room;
  return true;
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_audit(action text, target_type text DEFAULT NULL::text, target_id text DEFAULT NULL::text, details jsonb DEFAULT '{}'::jsonb)
 RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_id bigint;
begin
 if not public.uzzapbot_is_admin() then raise exception 'admin access required'; end if;
 insert into public.uzzapbot_audit_log(actor_id,action,target_type,target_id,details)
 values(auth.uid(),action,target_type,target_id,coalesce(details,'{}'::jsonb))
 returning id into v_id;
 return v_id;
end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_cc_audit()
 RETURNS SETOF uzzapbot_audit_log
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
select * from public.uzzapbot_audit_log where public.uzzapbot_is_admin() order by created_at desc limit 100;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_cc_commands(p_limit integer)
 RETURNS TABLE(message_id bigint, room_name text, sender text, body text, received_at timestamp with time zone, processed_at timestamp with time zone, status text, error_text text, latency_ms numeric)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin if not public.uzzapbot_is_admin() then raise exception 'Admin access required'; end if; return query select * from public.uzzapbot_command_monitor(least(greatest(coalesce(p_limit,50),1),500)); end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_cc_events(p_limit integer)
 RETURNS TABLE(message_id bigint, received_at timestamp with time zone, processed_at timestamp with time zone, status text, error_text text)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin if not public.uzzapbot_is_admin() then raise exception 'Admin access required'; end if; return query select * from public.uzzapbot_event_monitor(least(greatest(coalesce(p_limit,50),1),500)); end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_cc_games(p_limit integer)
 RETURNS TABLE(room_name text, game text, mode text, question_number integer, points integer, limit_count integer, paused boolean, updated_at timestamp with time zone, players bigint)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin if not public.uzzapbot_is_admin() then raise exception 'Admin access required'; end if; return query select * from public.uzzapbot_game_monitor(least(greatest(coalesce(p_limit,50),1),100)); end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_cc_metrics()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin if not public.uzzapbot_is_admin() then raise exception 'Admin access required'; end if; return public.uzzapbot_control_metrics(); end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_cc_performance()
 RETURNS TABLE(processed bigint, failed bigint, avg_latency_ms numeric, p95_latency_ms numeric, last_processed_at timestamp with time zone)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin if not public.uzzapbot_is_admin() then raise exception 'Admin access required'; end if; return query select * from public.uzzapbot_performance_24h(); end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_cc_question_stats()
 RETURNS TABLE(question_id bigint, uses bigint, correct bigint, incorrect bigint, revealed bigint, skipped bigint, avg_response_ms numeric, last_used_at timestamp with time zone)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin if not public.uzzapbot_is_admin() then raise exception 'Admin access required'; end if; return query select * from public.uzzapbot_question_usage_stats(); end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_claim_message(p_message_id bigint)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  if auth.role() <> 'service_role' then raise exception 'service role required'; end if;
  insert into public.uzzapbot_processed_messages(message_id, claimed_at, completed_at)
  values (p_message_id, now(), null)
  on conflict (message_id) do update
    set claimed_at = now(), completed_at = null
    where public.uzzapbot_processed_messages.completed_at is null
      and public.uzzapbot_processed_messages.claimed_at < now() - interval '5 minutes';
  return found;
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_claim_next_message(p_after_id bigint, p_bot_sender_id text, p_bot_name text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_cursor bigint:=p_after_id; v_row record; v_claimed boolean; v_completed timestamptz;
begin
 if auth.role() <> 'service_role' then raise exception 'service role required'; end if;
 for v_row in select id,room_name,sender,body,is_system,created_at,sender_id from public.room_messages where id>v_cursor order by id limit 100 loop
  if coalesce(v_row.sender_id::text,'')=coalesce(p_bot_sender_id,'') or lower(coalesce(v_row.sender,''))=lower(coalesce(p_bot_name,'')) then
   update public.uzzapbot_cursor set last_message_id=v_row.id,updated_at=now() where bot_name='default' and last_message_id=v_cursor;
   if not found then return null; end if;
   v_cursor:=v_row.id; continue;
  end if;
  insert into public.uzzapbot_processed_messages(message_id,claimed_at,completed_at) values(v_row.id,now(),null)
  on conflict(message_id) do update set claimed_at=now(),completed_at=null where public.uzzapbot_processed_messages.completed_at is null and public.uzzapbot_processed_messages.claimed_at < now()-interval '5 minutes';
  v_claimed:=found;
  if v_claimed then
   return jsonb_build_object('previous_id',v_cursor,'message',jsonb_build_object('id',v_row.id,'room_name',v_row.room_name,'sender',v_row.sender,'body',v_row.body,'is_system',v_row.is_system,'created_at',v_row.created_at,'sender_id',v_row.sender_id));
  end if;
  select completed_at into v_completed from public.uzzapbot_processed_messages where message_id=v_row.id;
  if v_completed is not null then
   update public.uzzapbot_cursor set last_message_id=v_row.id,updated_at=now() where bot_name='default' and last_message_id=v_cursor;
   if not found then return null; end if;
   v_cursor:=v_row.id; continue;
  end if;
  return null;
 end loop;
 return jsonb_build_object('previous_id',v_cursor,'message',null);
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_claim_receipt(p_message_id bigint)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_status text;
begin
  insert into public.uzzapbot_event_receipts(message_id,status,received_at,last_attempt_at,attempt_count)
  values(p_message_id,'received',now(),now(),1)
  on conflict (message_id) do update
    set last_attempt_at = now(),
        attempt_count = public.uzzapbot_event_receipts.attempt_count + 1,
        retry_count = public.uzzapbot_event_receipts.retry_count
          + case when public.uzzapbot_event_receipts.status = 'failed' then 1 else 0 end,
        duplicate_count = public.uzzapbot_event_receipts.duplicate_count
          + case when public.uzzapbot_event_receipts.status = 'processed' then 1 else 0 end;
  select status into v_status
  from public.uzzapbot_event_receipts
  where message_id=p_message_id;
  return coalesce(v_status,'received');
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_claim_welcome(p_room_name text, p_username text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_room text := nullif(trim(p_room_name), '');
  v_username text := nullif(trim(p_username), '');
  v_inserted boolean;
begin
  if v_room is null or v_username is null then
    return false;
  end if;

  insert into public.uzzapbot_welcomes (room_name, username_key)
  values (v_room, lower(v_username))
  on conflict (room_name, username_key) do nothing;

  get diagnostics v_inserted = row_count;
  return v_inserted;
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_cleanup_memory_v1(p_user_id uuid, p_room_name text DEFAULT NULL::text, p_max_per_room integer DEFAULT 50)
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_deleted integer := 0;
  v_trimmed integer := 0;
  v_max integer := greatest(1, least(coalesce(p_max_per_room,50),50));
begin
  if p_user_id is null then raise exception 'user_id is required'; end if;

  delete from public.uzzapbot_memory
  where user_id = p_user_id
    and room_name is not distinct from p_room_name
    and (
      (expires_at is not null and expires_at <= now())
      or (memory_scope = 'session' and updated_at < now() - interval '3 days')
      or (memory_scope = 'room' and updated_at < now() - interval '30 days')
      or (memory_scope = 'long_term' and importance < 80 and updated_at < now() - interval '180 days')
      or (memory_scope = 'long_term' and importance >= 80 and updated_at < now() - interval '365 days')
    );
  get diagnostics v_deleted = row_count;

  with ranked as (
    select id,
           row_number() over (
             order by importance asc, confidence asc, updated_at asc, id asc
           ) as rn
    from public.uzzapbot_memory
    where user_id = p_user_id
      and room_name is not distinct from p_room_name
  )
  delete from public.uzzapbot_memory m
  using ranked r
  where m.id = r.id and r.rn > v_max;
  get diagnostics v_trimmed = row_count;

  return v_deleted + v_trimmed;
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_command_monitor(p_limit integer DEFAULT 50)
 RETURNS TABLE(message_id bigint, room_name text, sender text, body text, received_at timestamp with time zone, processed_at timestamp with time zone, status text, error_text text, latency_ms numeric)
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
 if not public.uzzapbot_is_admin() then raise exception 'admin access required'; end if;
 return query
 select m.id,m.room_name,m.sender,m.body,r.received_at,r.processed_at,r.status,r.error_text,
        case when r.processed_at is not null then round((extract(epoch from(r.processed_at-r.received_at))*1000)::numeric,1) end
 from public.room_messages m
 join public.uzzapbot_event_receipts r on r.message_id=m.id
 order by r.received_at desc
 limit greatest(1,least(coalesce(p_limit,50),200));
end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_complete_message(p_previous_id bigint, p_message_id bigint)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_claimed_at timestamptz;
begin
  if auth.role() <> 'service_role' then raise exception 'service role required'; end if;
  if p_message_id <= p_previous_id then return false; end if;
  select claimed_at into v_claimed_at
    from public.uzzapbot_processed_messages
   where message_id=p_message_id and completed_at is null for update;
  if not found or v_claimed_at < now() - interval '5 minutes' then return false; end if;
  update public.uzzapbot_processed_messages set completed_at=now()
   where message_id=p_message_id and completed_at is null;
  if not found then return false; end if;
  update public.uzzapbot_cursor set last_message_id=p_message_id,updated_at=now()
   where bot_name='default' and last_message_id=p_previous_id;
  return true;
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_control_metrics()
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare result jsonb;
begin
 if not public.uzzapbot_is_admin() then raise exception 'admin access required'; end if;
 select jsonb_build_object(
   'processed',coalesce((select count(*) from public.uzzapbot_event_receipts where status='processed'),0),
   'failed',coalesce((select count(*) from public.uzzapbot_event_receipts where status='failed'),0),
   'received',coalesce((select count(*) from public.uzzapbot_event_receipts where status='received'),0),
   'games',coalesce((select count(*) from public.game_sessions),0),
   'players',coalesce((select count(*) from public.game_players),0)
 ) into result;
 return result;
end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_control_players(p_room_name text)
 RETURNS TABLE(username text, nickname text, score integer, correct integer, attempts integer, clues_used integer, joined_at timestamp with time zone, updated_at timestamp with time zone)
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
  select gp.username,gp.nickname,gp.score,gp.correct,gp.attempts,gp.clues_used,gp.joined_at,gp.updated_at
  from public.game_sessions gs join public.game_players gp on gp.session_id=gs.id
  where public.uzzapbot_is_admin() and gs.room_name=trim(p_room_name)
  order by gp.score desc,gp.updated_at desc;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_control_rooms()
 RETURNS TABLE(room_name text, activated boolean, locked boolean, wcbot boolean, welcome_message text, challenge_room text, activity_enabled boolean, activity_state text, last_human_activity_at timestamp with time zone, last_bot_activity_at timestamp with time zone)
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
  select r.name, coalesce(s.activated,false), coalesce(s.locked,false), coalesce(s.wcbot,false),
         coalesce(s.welcome_message,'welcome to {room} {nickname}'), coalesce(s.challenge_room,''),
         coalesce(a.enabled,false), coalesce(a.activity_state,'INACTIVE'), a.last_human_activity_at, a.last_bot_activity_at
  from public.chat_rooms r
  left join public.uzzapbot_room_settings s on s.room_name=r.name
  left join public.uzzapbot_room_activity a on a.room_name=r.name
  where public.uzzapbot_is_admin()
  order by r.name;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_dispatch_new_message()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
  v_token text;
BEGIN
  SELECT decrypted_secret INTO v_token
  FROM vault.decrypted_secrets
  WHERE name='uzzapbot_dispatch_token'
  LIMIT 1;
  IF v_token IS NULL THEN
    RAISE EXCEPTION 'UzzapBot dispatch secret is unavailable';
  END IF;
  PERFORM net.http_post(
    url := 'https://hrjgblvxhtbopuyzyasu.supabase.co/functions/v1/uzzapbot',
    headers := jsonb_build_object(
      'Content-Type','application/json',
      'x-uzzapbot-internal-token',v_token
    ),
    body := jsonb_build_object('message_id',NEW.id)
  );
  RETURN NEW;
END;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_event_monitor(p_limit integer DEFAULT 50)
 RETURNS TABLE(message_id bigint, received_at timestamp with time zone, processed_at timestamp with time zone, status text, error_text text)
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
 if not public.uzzapbot_is_admin() then raise exception 'admin access required'; end if;
 return query select r.message_id,r.received_at,r.processed_at,r.status,r.error_text
 from public.uzzapbot_event_receipts r
 order by r.received_at desc limit greatest(1,least(coalesce(p_limit,50),200));
end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_finish_receipt(p_message_id bigint, p_status text, p_error text DEFAULT NULL::text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  if p_status not in ('received','processed','failed') then raise exception 'invalid receipt status'; end if;
  update public.uzzapbot_event_receipts
  set status=p_status,processed_at=case when p_status in ('processed','failed') then now() else null end,error_text=p_error
  where message_id=p_message_id;
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_game_monitor(p_limit integer DEFAULT 50)
 RETURNS TABLE(room_name text, game text, mode text, question_number integer, points integer, limit_count integer, paused boolean, updated_at timestamp with time zone, players bigint)
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
 if not public.uzzapbot_is_admin() then raise exception 'admin access required'; end if;
 return query
 select s.room_name,s.game,s.mode,s.question_number,s.points,s.limit_count,s.paused,s.updated_at,
        (select count(*) from public.game_players p where p.session_id=s.id)::bigint
 from public.game_sessions s
 order by s.updated_at desc
 limit greatest(1,least(coalesce(p_limit,50),200));
end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_get_cursor()
 RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_cursor bigint;
  v_latest bigint;
begin
  if auth.role() <> 'service_role' then
    raise exception 'service role required';
  end if;

  select last_message_id
    into v_cursor
    from public.uzzapbot_cursor
   where bot_name = 'default';

  if found then
    return v_cursor;
  end if;

  select coalesce(max(id), 0)
    into v_latest
    from public.room_messages;

  insert into public.uzzapbot_cursor(bot_name, last_message_id)
  values ('default', v_latest)
  on conflict (bot_name) do nothing;

  select last_message_id
    into v_cursor
    from public.uzzapbot_cursor
   where bot_name = 'default';

  return coalesce(v_cursor, v_latest, 0);
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_get_memory(p_user_id uuid, p_room_name text DEFAULT NULL::text)
 RETURNS SETOF uzzapbot_memory
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select *
  from public.uzzapbot_memory
  where (auth.role() = 'service_role' or p_user_id = auth.uid())
    and user_id = p_user_id
    and (p_room_name is null or room_name is null or lower(room_name)=lower(p_room_name))
  order by updated_at desc
  limit 50;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_get_memory_v2(p_user_id uuid, p_room_name text DEFAULT NULL::text, p_limit integer DEFAULT 20)
 RETURNS SETOF uzzapbot_memory
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$ with ranked as (select m.*,row_number() over (partition by m.memory_key order by case when p_room_name is not null and m.room_name is not null and lower(m.room_name)=lower(p_room_name) then 0 when m.room_name is null then 1 else 2 end,case when m.source='user_confirmed' then 0 when m.source='explicit' then 1 else 2 end,m.confidence desc nulls last,m.updated_at desc) as rn from public.uzzapbot_memory m where (auth.role()='service_role' or p_user_id=auth.uid()) and m.user_id=p_user_id and (p_room_name is null or m.room_name is null or lower(m.room_name)=lower(p_room_name))) select id,user_id,room_name,memory_key,memory_value,memory_type,source,confidence,created_at,updated_at from ranked where rn=1 order by case when p_room_name is not null and room_name is not null and lower(room_name)=lower(p_room_name) then 0 when room_name is null then 1 else 2 end,source,confidence desc nulls last,updated_at desc limit greatest(1,least(coalesce(p_limit,20),50)); $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_get_memory_v3(p_user_id uuid, p_room_name text DEFAULT NULL::text, p_query text DEFAULT NULL::text, p_limit integer DEFAULT 8)
 RETURNS TABLE(id bigint, user_id uuid, room_name text, memory_key text, memory_value text, memory_type text, source text, confidence numeric, importance smallint, memory_scope text, expires_at timestamp with time zone, created_at timestamp with time zone, updated_at timestamp with time zone)
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
with candidates as (
  select
    m.*,
    case
      when p_room_name is not null
       and m.room_name is not null
       and lower(m.room_name)=lower(p_room_name) then 100
      when m.room_name is null then 70
      else 20
    end as room_score,
    case
      when p_query is null or btrim(p_query)='' then 0
      when lower(m.memory_key || ' ' || m.memory_value) like '%' || lower(btrim(p_query)) || '%' then 100
      when lower(m.memory_key) like '%' || lower(split_part(btrim(p_query),' ',1)) || '%' then 60
      when lower(m.memory_value) like '%' || lower(split_part(btrim(p_query),' ',1)) || '%' then 40
      else 0
    end as query_score,
    case
      when m.source='user_confirmed' then 100
      when m.source='explicit' then 80
      when m.source='bot_inferred' then 50
      else 30
    end as source_score
  from public.uzzapbot_memory m
  where m.user_id=p_user_id
    and (p_room_name is null or m.room_name is null or lower(m.room_name)=lower(p_room_name))
    and (m.expires_at is null or m.expires_at > now())
),
ranked as (
  select c.*,
    row_number() over (
      partition by c.memory_key
      order by
        (c.room_score + c.query_score + c.source_score
         + (c.importance::integer * 2)
         + round(c.confidence * 50)::integer
         + greatest(0, 30 - least(30, floor(extract(epoch from (now()-c.updated_at))/86400)))::integer
        ) desc,
        c.updated_at desc
    ) as rn
  from candidates c
)
select
  r.id,r.user_id,r.room_name,r.memory_key,r.memory_value,r.memory_type,
  r.source,r.confidence,r.importance,r.memory_scope,r.expires_at,
  r.created_at,r.updated_at
from ranked r
where r.rn=1
order by
  (r.room_score + r.query_score + r.source_score
   + (r.importance::integer * 2)
   + round(r.confidence * 50)::integer
   + greatest(0, 30 - least(30, floor(extract(epoch from (now()-r.updated_at))/86400)))::integer
  ) desc,
  r.updated_at desc
limit greatest(1,least(coalesce(p_limit,8),8));
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_grant_default_powerups(p_user_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
BEGIN
 IF p_user_id IS NULL THEN RAISE EXCEPTION 'user id is required'; END IF;
 INSERT INTO public.uzzapbot_powerups(user_id,powerup,charges)
 SELECT p_user_id,x,0 FROM unnest(ARRAY['hint','fifty','skip','double','time','shield']) AS x
 ON CONFLICT (user_id,powerup) DO NOTHING;
END $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_handle_challenge_message()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
BEGIN
  RAISE EXCEPTION 'uzzapbot_handle_challenge_message is retired; use the live uzzapbot Edge Function dispatch path';
END;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_is_admin()
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$ select coalesce(auth.uid() is not null and exists(
 select 1 from public.moderator_roles mr
 where mr.user_id=auth.uid() and mr.role='admin'
),false) $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_issue_command(p_room_name text, p_command text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_username text; v_message_id bigint;
begin
  if not public.uzzapbot_is_admin() then raise exception 'Admin access required'; end if;
  if nullif(trim(p_room_name),'') is null or nullif(trim(p_command),'') is null then raise exception 'Room and command are required'; end if;
  if not exists (select 1 from public.chat_rooms where name=trim(p_room_name)) then raise exception 'Room not found'; end if;
  select coalesce(p.username,p.nickname,split_part(coalesce(u.email,''),'@',1),'admin') into v_username
  from auth.users u left join public.profiles p on p.owner_id=u.id or p.id=u.id
  where u.id=auth.uid() limit 1;
  insert into public.room_messages(room_name,sender,body,is_system,sender_id)
  values(trim(p_room_name),coalesce(v_username,'admin'),trim(p_command),false,auth.uid())
  returning id into v_message_id;
  perform public.uzzapbot_audit('control.command','room',trim(p_room_name),jsonb_build_object('command',trim(p_command),'message_id',v_message_id));
  return jsonb_build_object('ok',true,'message_id',v_message_id,'room',trim(p_room_name),'command',trim(p_command));
end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_memory_semantic_search_v1(p_user_id uuid, p_room_name text, query_embedding vector, p_match_threshold real DEFAULT 0.55, p_match_count integer DEFAULT 8)
 RETURNS TABLE(id bigint, user_id uuid, room_name text, memory_key text, memory_value text, memory_type text, source text, confidence numeric, importance smallint, memory_scope text, expires_at timestamp with time zone, similarity real, updated_at timestamp with time zone)
 LANGUAGE sql
 STABLE
 SET search_path TO 'public', 'extensions'
AS $function$
  select
    m.id,m.user_id,m.room_name,m.memory_key,m.memory_value,m.memory_type,
    m.source,m.confidence,m.importance,m.memory_scope,m.expires_at,
    ((m.embedding <#> query_embedding) * -1)::real as similarity,
    m.updated_at
  from public.uzzapbot_memory m
  where m.embedding is not null
    and m.user_id = p_user_id
    and m.room_name is not distinct from p_room_name
    and (m.expires_at is null or m.expires_at > now())
    and ((m.embedding <#> query_embedding) * -1) >= p_match_threshold
  order by m.embedding <#> query_embedding
  limit greatest(1,least(coalesce(p_match_count,8),20));
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_performance_24h()
 RETURNS TABLE(processed bigint, failed bigint, avg_latency_ms numeric, p95_latency_ms numeric, last_processed_at timestamp with time zone)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
  select
    count(*) filter(where status='processed')::bigint,
    count(*) filter(where status='failed')::bigint,
    round((avg(extract(epoch from (processed_at-received_at))*1000) filter(where status='processed' and processed_at is not null))::numeric,1),
    round((percentile_cont(0.95) within group(order by extract(epoch from (processed_at-received_at))*1000) filter(where status='processed' and processed_at is not null))::numeric,1),
    max(processed_at) filter(where status='processed')
  from public.uzzapbot_event_receipts
  where received_at >= now()-interval '24 hours'
    and public.uzzapbot_is_admin();
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_player_progress(p_user_id uuid)
 RETURNS TABLE(user_id uuid, xp bigint, level integer, current_streak integer, best_streak integer, total_games integer, total_correct integer, total_attempts integer)
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
  SELECT s.user_id,s.xp,s.level,s.current_streak,s.best_streak,s.total_games,s.total_correct,s.total_attempts
  FROM public.uzzapbot_player_stats s
  WHERE s.user_id=p_user_id;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_question_usage_stats()
 RETURNS TABLE(question_id bigint, uses bigint, correct bigint, incorrect bigint, revealed bigint, skipped bigint, avg_response_ms numeric, last_used_at timestamp with time zone)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
 select q.id,count(u.id),count(u.id) filter(where u.outcome='correct'),count(u.id) filter(where u.outcome='incorrect'),count(u.id) filter(where u.outcome='revealed'),count(u.id) filter(where u.outcome='skipped'),round(avg(u.response_ms)::numeric,1),max(u.used_at)
 from public.uzzapbot_questions q left join public.uzzapbot_question_usage u on u.question_id=q.id
 where public.uzzapbot_is_admin()
 group by q.id;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_question_validation(p_question text, p_answer text, p_aliases text[] DEFAULT '{}'::text[], p_category text DEFAULT 'trivia'::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare q text:=btrim(coalesce(p_question,'')); a text:=btrim(coalesce(p_answer,'')); score integer:=100; issues text[]:='{}'; dup_id bigint;
begin
 if not public.uzzapbot_is_admin() then raise exception 'admin access required'; end if;
 if length(q)<10 then score:=score-30; issues:=array_append(issues,'Question is too short'); end if;
 if length(q)>500 then score:=score-20; issues:=array_append(issues,'Question is too long'); end if;
 if length(a)<1 then score:=score-50; issues:=array_append(issues,'Answer is required'); end if;
 if length(a)>200 then score:=score-20; issues:=array_append(issues,'Answer is too long'); end if;
 if lower(q)=lower(a) then score:=score-40; issues:=array_append(issues,'Question and answer are identical'); end if;
 select id into dup_id from public.uzzapbot_questions where lower(btrim(question))=lower(q) and lower(category)=lower(coalesce(p_category,'trivia')) and status<>'archived' order by id limit 1;
 if dup_id is not null then score:=score-50; issues:=array_append(issues,'Possible duplicate question'); end if;
 score:=greatest(0,least(100,score));
 return jsonb_build_object('score',score,'issues',to_jsonb(issues),'duplicate_of',dup_id);
end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_random_emoticon()
 RETURNS text
 LANGUAGE sql
 SET search_path TO ''
AS $function$
  select (array[
    ':e',
    '>|',
    ':>',
    ':DD',
    'o/',
    ':Oo',
    '>,',
    ':|',
    ':B,',
    ':OOo',
    ':Zz.',
    'O:)',
    '))(',
    '>><)',
    '<:D',
    '(cU)',
    '<:)',
    '(+)',
    '@};-'
  ])[1 + floor(random() * 19)::int];
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_random_word(p_dataset_id text, p_min_len integer DEFAULT 3, p_max_len integer DEFAULT 15)
 RETURNS TABLE(word text)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$ with candidates as (select e.word,(select string_agg(c,'' order by c) from regexp_split_to_table(lower(e.word),'') c) as sig from public.uzzapbot_word_dataset_entries e where e.dataset_id=p_dataset_id and e.word ~ '^[A-Za-z]+$' and length(e.word) between p_min_len and p_max_len) select c.word from candidates c where not exists (select 1 from candidates d where d.sig=c.sig and d.word<>c.word) order by random() limit 1; $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_record_game_question_usage()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_usage_id bigint;
begin
  if coalesce(new.question,'') = '' or coalesce(new.question_number,0) <= 0 then
    return new;
  end if;

  if tg_op = 'INSERT' then
    insert into public.uzzapbot_question_usage(question_id,room_name,game,used_at,outcome,response_ms)
    values (null,new.room_name,coalesce(new.current_game,new.mode,new.game,'unknown'),coalesce(new.updated_at,now()),'pending',null)
    returning id into v_usage_id;

    new.state_json := jsonb_set(coalesce(new.state_json,'{}'::jsonb),'{question_usage_id}',to_jsonb(v_usage_id),true);
    return new;
  end if;

  if new.question_number is distinct from old.question_number
     or new.question is distinct from old.question
     or new.current_game is distinct from old.current_game then
    insert into public.uzzapbot_question_usage(question_id,room_name,game,used_at,outcome,response_ms)
    values (null,new.room_name,coalesce(new.current_game,new.mode,new.game,'unknown'),coalesce(new.updated_at,now()),'pending',null)
    returning id into v_usage_id;

    new.state_json := jsonb_set(coalesce(new.state_json,'{}'::jsonb),'{question_usage_id}',to_jsonb(v_usage_id),true);
  end if;

  return new;
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_record_game_start(p_user_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
BEGIN
 IF p_user_id IS NULL THEN RAISE EXCEPTION 'user id is required'; END IF;
 INSERT INTO public.uzzapbot_player_stats(user_id,total_games)
 VALUES(p_user_id,1)
 ON CONFLICT(user_id) DO UPDATE SET total_games=public.uzzapbot_player_stats.total_games+1,updated_at=now();
END $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_save_game_state(p_state jsonb)
 RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v_room text; v_id bigint; v_player jsonb;
begin
  if p_state is null or jsonb_typeof(p_state) <> 'object' then raise exception 'Invalid game state'; end if;
  v_room := nullif(trim(p_state->>'room'), '');
  if v_room is null then raise exception 'Game state room is required'; end if;
  select id into v_id from public.game_sessions where room_name=v_room order by id limit 1 for update;
  if v_id is null then
    insert into public.game_sessions(room_name,game,mode,current_game,points,limit_count,endless,paused,question_number,question,answer,clue_text,used_questions,state_json)
    values(v_room,coalesce(nullif(p_state->>'game',''),'math'),coalesce(nullif(p_state->>'mode',''),coalesce(nullif(p_state->>'game',''),'math')),coalesce(nullif(p_state->>'current_game',''),coalesce(nullif(p_state->>'game',''),'math')),coalesce((p_state->>'points')::integer,10),coalesce((p_state->>'limit')::integer,100),coalesce((p_state->>'endless')::boolean,false),coalesce((p_state->>'paused')::boolean,false),coalesce((p_state->>'number')::integer,0),coalesce(p_state->>'question',''),coalesce(p_state->>'answer',''),coalesce(p_state->>'clue_text',''),coalesce(p_state->'used_questions','[]'::jsonb),p_state)
    returning id into v_id;
  else
    update public.game_sessions set game=coalesce(nullif(p_state->>'game',''),'math'),mode=coalesce(nullif(p_state->>'mode',''),coalesce(nullif(p_state->>'game',''),'math')),current_game=coalesce(nullif(p_state->>'current_game',''),coalesce(nullif(p_state->>'game',''),'math')),points=coalesce((p_state->>'points')::integer,10),limit_count=coalesce((p_state->>'limit')::integer,100),endless=coalesce((p_state->>'endless')::boolean,false),paused=coalesce((p_state->>'paused')::boolean,false),question_number=coalesce((p_state->>'number')::integer,0),question=coalesce(p_state->>'question',''),answer=coalesce(p_state->>'answer',''),clue_text=coalesce(p_state->>'clue_text',''),used_questions=coalesce(p_state->'used_questions','[]'::jsonb),state_json=p_state,updated_at=now() where id=v_id;
  end if;
  delete from public.game_players where session_id=v_id;
  if jsonb_typeof(coalesce(p_state->'players','[]'::jsonb))='array' then
    for v_player in select value from jsonb_array_elements(p_state->'players') loop
      insert into public.game_players(session_id,user_id,username,nickname,score,correct,attempts,clues_used)
      values(v_id,nullif(v_player->>'user_id','')::uuid,coalesce(v_player->>'username',''),coalesce(v_player->>'nickname',''),coalesce((v_player->>'score')::integer,0),coalesce((v_player->>'correct')::integer,0),coalesce((v_player->>'attempts')::integer,0),coalesce((v_player->>'clues_used')::integer,0));
    end loop;
  end if;
  return v_id;
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_set_room_control(p_room_name text, p_activated boolean, p_locked boolean, p_wcbot boolean, p_welcome_message text, p_challenge_room text, p_activity_enabled boolean)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  if not public.uzzapbot_is_admin() then raise exception 'Admin access required'; end if;
  if not exists(select 1 from public.chat_rooms where name=trim(p_room_name)) then raise exception 'Room not found'; end if;
  insert into public.uzzapbot_room_settings(room_name,activated,locked,wcbot,welcome_message,challenge_room,updated_at)
  values(trim(p_room_name),coalesce(p_activated,false),coalesce(p_locked,false),coalesce(p_wcbot,false),coalesce(nullif(p_welcome_message,''),'welcome to {room} {nickname}'),coalesce(p_challenge_room,''),now())
  on conflict(room_name) do update set activated=excluded.activated,locked=excluded.locked,wcbot=excluded.wcbot,welcome_message=excluded.welcome_message,challenge_room=excluded.challenge_room,updated_at=now();
  insert into public.uzzapbot_room_activity(room_name,enabled,updated_at)
  values(trim(p_room_name),coalesce(p_activity_enabled,false),now())
  on conflict(room_name) do update set enabled=excluded.enabled,updated_at=now();
  perform public.uzzapbot_audit('room.settings.update','room',trim(p_room_name),jsonb_build_object('activated',p_activated,'locked',p_locked,'wcbot',p_wcbot,'welcome_message',p_welcome_message,'challenge_room',p_challenge_room,'activity_enabled',p_activity_enabled));
  return jsonb_build_object('ok',true);
end $function$

CREATE OR REPLACE FUNCTION public.uzzapbot_upsert_memory(p_user_id uuid, p_room_name text, p_memory_key text, p_memory_value text, p_memory_type text DEFAULT 'fact'::text, p_source text DEFAULT 'explicit'::text, p_confidence numeric DEFAULT 1.00)
 RETURNS uzzapbot_memory
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_row public.uzzapbot_memory;
begin
  if auth.role() <> 'service_role' and p_user_id <> auth.uid() then
    raise exception 'not authorized';
  end if;

  insert into public.uzzapbot_memory(
    user_id,room_name,memory_key,memory_value,memory_type,source,confidence
  )
  values(
    p_user_id,p_room_name,p_memory_key,p_memory_value,p_memory_type,p_source,p_confidence
  )
  on conflict (user_id,room_name,memory_key)
  do update set
    memory_value=excluded.memory_value,
    memory_type=excluded.memory_type,
    source=excluded.source,
    confidence=excluded.confidence,
    updated_at=now()
  returning * into v_row;

  return v_row;
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_upsert_memory_v3(p_user_id uuid, p_room_name text, p_memory_key text, p_memory_value text, p_memory_type text DEFAULT 'fact'::text, p_source text DEFAULT 'explicit'::text, p_confidence numeric DEFAULT 1.00, p_importance smallint DEFAULT 50, p_memory_scope text DEFAULT 'long_term'::text, p_expires_at timestamp with time zone DEFAULT NULL::timestamp with time zone)
 RETURNS uzzapbot_memory
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_row public.uzzapbot_memory;
begin
  if p_user_id is null then raise exception 'user_id is required'; end if;
  if p_memory_key is null or btrim(p_memory_key)='' then raise exception 'memory_key is required'; end if;
  if p_memory_value is null or btrim(p_memory_value)='' then raise exception 'memory_value is required'; end if;
  if p_importance < 0 or p_importance > 100 then raise exception 'importance must be 0..100'; end if;
  if p_memory_scope not in ('long_term','room','session','temporary') then raise exception 'invalid memory_scope'; end if;
  if p_memory_scope='temporary' and p_expires_at is null then raise exception 'temporary memory requires expires_at'; end if;

  insert into public.uzzapbot_memory(
    user_id,room_name,memory_key,memory_value,memory_type,source,
    confidence,importance,memory_scope,expires_at,updated_at
  )
  values(
    p_user_id,p_room_name,btrim(p_memory_key),btrim(p_memory_value),p_memory_type,
    p_source,greatest(0,least(1,p_confidence)),p_importance,p_memory_scope,p_expires_at,now()
  )
  on conflict (user_id,room_name,memory_key)
  do update set
    memory_value=excluded.memory_value,
    memory_type=excluded.memory_type,
    source=excluded.source,
    confidence=excluded.confidence,
    importance=excluded.importance,
    memory_scope=excluded.memory_scope,
    expires_at=excluded.expires_at,
    updated_at=now()
  returning * into v_row;

  return v_row;
end;
$function$

CREATE OR REPLACE FUNCTION public.uzzapbot_validate_dispatch_token(p_token text)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
  SELECT EXISTS (
    SELECT 1
    FROM vault.decrypted_secrets
    WHERE name='uzzapbot_dispatch_token'
      AND decrypted_secret = p_token
  );
$function$


-- TRIGGERS
CREATE TRIGGER trg_buddy_owner BEFORE INSERT OR UPDATE ON public.buddies FOR EACH ROW EXECUTE FUNCTION set_buddy_owner();
CREATE TRIGGER on_buddy_request_push AFTER INSERT ON public.buddy_requests FOR EACH ROW EXECUTE FUNCTION notify_buddy_request_push();
CREATE TRIGGER on_direct_message_push AFTER INSERT ON public.direct_messages FOR EACH ROW EXECUTE FUNCTION notify_direct_message_push();
CREATE TRIGGER trg_direct_message_rate_limit BEFORE INSERT ON public.direct_messages FOR EACH ROW EXECUTE FUNCTION enforce_direct_message_rate_limit();
CREATE TRIGGER trg_uzzapbot_game_question_usage BEFORE INSERT OR UPDATE OF question_number, question, current_game, state_json ON public.game_sessions FOR EACH ROW EXECUTE FUNCTION uzzapbot_record_game_question_usage();
CREATE TRIGGER trg_apply_moderation_action AFTER INSERT ON public.moderation_actions FOR EACH ROW EXECUTE FUNCTION apply_moderation_action_trigger();
CREATE TRIGGER trg_sync_moderation_action BEFORE INSERT OR UPDATE ON public.moderation_actions FOR EACH ROW EXECUTE FUNCTION sync_moderation_action();
CREATE TRIGGER trg_profile_nickname_plain BEFORE INSERT OR UPDATE OF nickname ON public.profiles FOR EACH ROW EXECUTE FUNCTION set_profile_nickname_plain();
CREATE TRIGGER on_room_invitation_push AFTER INSERT ON public.room_invitations FOR EACH ROW EXECUTE FUNCTION notify_room_invitation_push();
CREATE TRIGGER trg_room_message_rate_limit BEFORE INSERT ON public.room_messages FOR EACH ROW EXECUTE FUNCTION enforce_message_rate_limit();
CREATE TRIGGER trg_room_message_sender BEFORE INSERT ON public.room_messages FOR EACH ROW EXECUTE FUNCTION set_room_message_sender();
CREATE TRIGGER trg_uzzapbot_dispatch AFTER INSERT ON public.room_messages FOR EACH ROW WHEN ((COALESCE(new.sender, ''::text) <> ALL (ARRAY['UzzapBot'::text, 'uzzapbot'::text]))) EXECUTE FUNCTION uzzapbot_dispatch_new_message();
CREATE TRIGGER trg_lock_room_participant_identity BEFORE UPDATE ON public.room_participants FOR EACH ROW EXECUTE FUNCTION lock_room_participant_identity();
CREATE TRIGGER trg_participant_owner BEFORE INSERT OR UPDATE ON public.room_participants FOR EACH ROW EXECUTE FUNCTION set_participant_owner();
CREATE TRIGGER trg_room_participant_leave_bot AFTER DELETE ON public.room_participants FOR EACH ROW EXECUTE FUNCTION room_participant_leave_bot();
CREATE TRIGGER trg_room_participant_welcome AFTER INSERT ON public.room_participants FOR EACH ROW EXECUTE FUNCTION room_participant_join_bot();
CREATE TRIGGER trg_room_participants_capacity BEFORE INSERT ON public.room_participants FOR EACH ROW EXECUTE FUNCTION enforce_room_capacity();
