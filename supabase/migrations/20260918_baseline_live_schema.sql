-- ============================================================================
-- UZZAP CANONICAL BASELINE — generated 2026-09-18 from the LIVE database
-- (pg_get_functiondef / pg_get_triggerdef / pg_policies / information_schema).
--
-- This file is the SOURCE OF TRUTH. Earlier migration files are historical;
-- this baseline converges any environment (fresh or drifted) to the exact
-- live schema. All statements are idempotent (create-if-not-exists,
-- drop-if-exists, create-or-replace), so it is SAFE to run on the live DB.
--
-- Notes:
--  * Primary keys / unique constraints were reconstructed from usage patterns
--    (ON CONFLICT clauses, trigger bodies). `id` columns are identity columns.
--  * cleanup_stale_room_participants() is invoked by a scheduled job
--    (pg_cron) on live; recreate the cron job in Dashboard if missing.
--  * The edge function send-push + its secret are managed separately.
--  * ROLE MANAGEMENT (f18): live grants owner/admin/moderator roles MANUALLY
--    via moderator_roles — there is no automatic bootstrap. To grant a role
--    (run as service role / in SQL Editor):
--      insert into public.moderator_roles (user_id, role, created_by)
--      values ('<user-uuid>', 'admin', '<your-uuid>');
--    Valid roles observed: 'owner', 'admin', 'moderator'.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- TABLES (columns captured from live information_schema)
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
  id uuid not null default gen_random_uuid(),
  owner_id uuid,
  username text not null,
  nickname text not null,
  email text,
  status text default 'Available'::text,
  status_message text default ''::text,
  avatar text default 'kool'::text,
  is_online boolean default true,
  last_seen timestamp with time zone default now(),
  created_at timestamp with time zone default now(),
  constraint profiles_pkey primary key (id),
  constraint profiles_owner_id_key unique (owner_id)
);

create table if not exists public.chat_categories (
  id text not null,
  name text not null,
  sort_order integer default 0,
  constraint chat_categories_pkey primary key (id)
);

create table if not exists public.chat_rooms (
  id text not null,
  name text not null,
  category_id text,
  topic text default ''::text,
  capacity integer default 50,
  is_moderator_only boolean not null default false,
  created_at timestamp with time zone default now(),
  constraint chat_rooms_pkey primary key (id),
  constraint chat_rooms_name_key unique (name)
);

create table if not exists public.room_messages (
  id bigint generated always as identity,
  room_name text not null,
  sender text not null,
  sender_id uuid,
  body text not null,
  is_system boolean default false,
  created_at timestamp with time zone default now(),
  constraint room_messages_pkey primary key (id)
);

create table if not exists public.room_participants (
  id bigint generated always as identity,
  room_name text not null,
  username text not null,
  owner_id uuid,
  last_ping timestamp with time zone default now(),
  constraint room_participants_pkey primary key (id),
  constraint room_participants_room_username_key unique (room_name, username)
);

create table if not exists public.buddies (
  id bigint generated always as identity,
  owner_id uuid,
  user_handle text not null,
  buddy_handle text not null,
  group_name text default 'My Friends'::text,
  created_at timestamp with time zone default now(),
  constraint buddies_pkey primary key (id),
  constraint buddies_user_buddy_key unique (user_handle, buddy_handle)
);

create table if not exists public.direct_messages (
  id bigint generated always as identity,
  sender text not null,
  sender_id uuid,
  recipient text not null,
  recipient_id uuid,
  body text not null,
  is_read boolean default false,
  created_at timestamp with time zone default now(),
  constraint direct_messages_pkey primary key (id)
);

create table if not exists public.blocked_users (
  id bigint generated always as identity,
  owner_id uuid not null,
  blocked_username text not null,
  created_at timestamp with time zone not null default now(),
  constraint blocked_users_pkey primary key (id)
);

create table if not exists public.user_reports (
  id bigint generated always as identity,
  reporter_id uuid not null,
  reported_username text not null,
  room_name text,
  message_id bigint,
  details text not null default ''::text,
  status text not null default 'pending',
  reviewed_by uuid,
  reviewed_at timestamp with time zone,
  created_at timestamp with time zone not null default now(),
  constraint user_reports_pkey primary key (id)
);

create table if not exists public.moderator_roles (
  user_id uuid not null,
  role text not null,
  created_by uuid,
  created_at timestamp with time zone not null default now()
);

create table if not exists public.room_sanctions (
  id bigint generated always as identity,
  user_id uuid not null,
  username text not null,
  room_name text,
  sanction text not null,
  active boolean not null default true,
  expires_at timestamp with time zone,
  reason text not null default ''::text,
  created_by uuid not null,
  created_at timestamp with time zone not null default now(),
  constraint room_sanctions_pkey primary key (id)
);

create table if not exists public.moderation_actions (
  id bigint generated always as identity,
  moderator_id uuid not null,
  target_user_id uuid,
  target_username text not null,
  action text,
  action_type text not null,
  room_name text,
  message_id bigint,
  reason text not null default ''::text,
  duration_minutes integer,
  expires_at timestamp with time zone,
  created_at timestamp with time zone not null default now(),
  constraint moderation_actions_pkey primary key (id)
);

create table if not exists public.room_invitations (
  id bigint generated always as identity,
  room_name text not null,
  inviter_id uuid not null,
  invitee_id uuid not null,
  inviter_username text not null,
  invitee_username text not null,
  status text not null default 'pending'::text,
  responded_at timestamp with time zone,
  created_at timestamp with time zone not null default now(),
  constraint room_invitations_pkey primary key (id)
);

create table if not exists public.buddy_requests (
  id bigint generated always as identity,
  requester_handle text not null,
  recipient_handle text not null,
  status text not null default 'pending'::text,
  responded_at timestamp with time zone,
  created_at timestamp with time zone not null default now(),
  constraint buddy_requests_pkey primary key (id)
);

create table if not exists public.user_fcm_tokens (
  id bigint generated always as identity,
  user_id uuid,
  username text not null,
  fcm_token text not null,
  updated_at timestamp with time zone default now(),
  constraint user_fcm_tokens_pkey primary key (id),
  constraint user_fcm_tokens_token_key unique (fcm_token)
);

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.chat_categories enable row level security;
alter table public.chat_rooms enable row level security;
alter table public.room_messages enable row level security;
alter table public.room_participants enable row level security;
alter table public.buddies enable row level security;
alter table public.direct_messages enable row level security;
alter table public.blocked_users enable row level security;
alter table public.user_reports enable row level security;
alter table public.moderator_roles enable row level security;
alter table public.room_sanctions enable row level security;
alter table public.moderation_actions enable row level security;
alter table public.room_invitations enable row level security;
alter table public.buddy_requests enable row level security;
alter table public.user_fcm_tokens enable row level security;

-- Realtime: full old rows for DELETE propagation (room deletes are client-routed by room_name)
alter table public.room_messages replica identity full;
alter table public.direct_messages replica identity full;


-- ---------------------------------------------------------------------------
-- POLICIES (captured from live pg_policies)
-- ---------------------------------------------------------------------------

drop policy if exists "blocked_users_delete_own" on public.blocked_users;
create policy "blocked_users_delete_own" on public.blocked_users for DELETE to public
  using ((owner_id = ( SELECT auth.uid() AS uid)))
  with check (null);

drop policy if exists "blocked_users_insert_own" on public.blocked_users;
create policy "blocked_users_insert_own" on public.blocked_users for INSERT to public
  using (null)
  with check ((owner_id = ( SELECT auth.uid() AS uid)));

drop policy if exists "blocked_users_read_own" on public.blocked_users;
create policy "blocked_users_read_own" on public.blocked_users for SELECT to public
  using ((owner_id = ( SELECT auth.uid() AS uid)))
  with check (null);

drop policy if exists "buddies_delete_own" on public.buddies;
create policy "buddies_delete_own" on public.buddies for DELETE to authenticated
  using ((owner_id = ( SELECT auth.uid() AS uid)))
  with check (null);

drop policy if exists "buddies_insert_own" on public.buddies;
create policy "buddies_insert_own" on public.buddies for INSERT to authenticated
  using (null)
  with check ((owner_id = ( SELECT auth.uid() AS uid)));

drop policy if exists "buddies_read_own" on public.buddies;
create policy "buddies_read_own" on public.buddies for SELECT to authenticated
  using ((owner_id = ( SELECT auth.uid() AS uid)))
  with check (null);

drop policy if exists "buddies_update_own" on public.buddies;
create policy "buddies_update_own" on public.buddies for UPDATE to authenticated
  using ((owner_id = ( SELECT auth.uid() AS uid)))
  with check ((owner_id = ( SELECT auth.uid() AS uid)));

drop policy if exists "buddy_requests_insert_own" on public.buddy_requests;
create policy "buddy_requests_insert_own" on public.buddy_requests for INSERT to authenticated
  using (null)
  with check ((lower(requester_handle) = lower(COALESCE(( SELECT profiles.username
   FROM profiles
  WHERE (profiles.owner_id = ( SELECT auth.uid() AS uid))
 LIMIT 1), ''::text))));

drop policy if exists "buddy_requests_select_own" on public.buddy_requests;
create policy "buddy_requests_select_own" on public.buddy_requests for SELECT to authenticated
  using (((lower(requester_handle) = lower(COALESCE(( SELECT profiles.username
   FROM profiles
  WHERE (profiles.owner_id = ( SELECT auth.uid() AS uid))
 LIMIT 1), ''::text))) OR (lower(recipient_handle) = lower(COALESCE(( SELECT profiles.username
   FROM profiles
  WHERE (profiles.owner_id = ( SELECT auth.uid() AS uid))
 LIMIT 1), ''::text)))))
  with check (null);

drop policy if exists "buddy_requests_update_recipient" on public.buddy_requests;
create policy "buddy_requests_update_recipient" on public.buddy_requests for UPDATE to authenticated
  using ((lower(recipient_handle) = lower(COALESCE(( SELECT profiles.username
   FROM profiles
  WHERE (profiles.owner_id = ( SELECT auth.uid() AS uid))
 LIMIT 1), ''::text))))
  with check ((lower(recipient_handle) = lower(COALESCE(( SELECT profiles.username
   FROM profiles
  WHERE (profiles.owner_id = ( SELECT auth.uid() AS uid))
 LIMIT 1), ''::text))));

drop policy if exists "categories_read" on public.chat_categories;
create policy "categories_read" on public.chat_categories for SELECT to anon,authenticated
  with check (null);

drop policy if exists "rooms_read" on public.chat_rooms;
create policy "rooms_read" on public.chat_rooms for SELECT to anon,authenticated
  with check (null);

drop policy if exists "dm_insert_sender" on public.direct_messages;
create policy "dm_insert_sender" on public.direct_messages for INSERT to authenticated
  using (null)
  with check (((sender_id = ( SELECT auth.uid() AS uid)) AND (recipient_id <> ( SELECT auth.uid() AS uid)) AND (lower(sender) = lower(COALESCE(( SELECT p.username
   FROM profiles p
  WHERE (p.owner_id = ( SELECT auth.uid() AS uid))
 LIMIT 1), ''::text))) AND (lower(recipient) = lower(COALESCE(( SELECT p.username
   FROM profiles p
  WHERE (p.owner_id = direct_messages.recipient_id)
 LIMIT 1), ''::text))) AND (NOT (EXISTS ( SELECT 1
   FROM blocked_users b
  WHERE ((b.owner_id = ( SELECT auth.uid() AS uid)) AND (lower(b.blocked_username) = lower(direct_messages.recipient)))))) AND (NOT (EXISTS ( SELECT 1
   FROM blocked_users b
  WHERE ((b.owner_id = direct_messages.recipient_id) AND (lower(b.blocked_username) = lower(direct_messages.sender))))))));

drop policy if exists "dm_read_participant" on public.direct_messages;
create policy "dm_read_participant" on public.direct_messages for SELECT to authenticated
  using (((sender_id = ( SELECT auth.uid() AS uid)) OR (recipient_id = ( SELECT auth.uid() AS uid))))
  with check (null);

drop policy if exists "dm_update_recipient" on public.direct_messages;
create policy "dm_update_recipient" on public.direct_messages for UPDATE to authenticated
  using ((recipient_id = ( SELECT auth.uid() AS uid)))
  with check ((recipient_id = ( SELECT auth.uid() AS uid)));

drop policy if exists "moderation_actions_mod_delete" on public.moderation_actions;
create policy "moderation_actions_mod_delete" on public.moderation_actions for DELETE to authenticated
  using ((( SELECT is_moderator() AS is_moderator) AND (moderator_id = ( SELECT auth.uid() AS uid))))
  with check (null);

drop policy if exists "moderation_actions_mod_insert" on public.moderation_actions;
create policy "moderation_actions_mod_insert" on public.moderation_actions for INSERT to authenticated
  using (null)
  with check ((( SELECT is_moderator() AS is_moderator) AND (moderator_id = ( SELECT auth.uid() AS uid))));

drop policy if exists "moderation_actions_mod_read" on public.moderation_actions;
create policy "moderation_actions_mod_read" on public.moderation_actions for SELECT to authenticated
  using (( SELECT is_moderator() AS is_moderator))
  with check (null);

drop policy if exists "moderator_roles_self_read" on public.moderator_roles;
create policy "moderator_roles_self_read" on public.moderator_roles for SELECT to authenticated
  using ((user_id = ( SELECT auth.uid() AS uid)))
  with check (null);

drop policy if exists "profiles_delete_own" on public.profiles;
create policy "profiles_delete_own" on public.profiles for DELETE to authenticated
  using ((owner_id = ( SELECT auth.uid() AS uid)))
  with check (null);

drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own" on public.profiles for INSERT to authenticated
  using (null)
  with check ((owner_id = ( SELECT auth.uid() AS uid)));

drop policy if exists "profiles_read" on public.profiles;
create policy "profiles_read" on public.profiles for SELECT to anon,authenticated
  with check (null);

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own" on public.profiles for UPDATE to authenticated
  using ((owner_id = ( SELECT auth.uid() AS uid)))
  with check ((owner_id = ( SELECT auth.uid() AS uid)));

drop policy if exists "room_invitations_read_own" on public.room_invitations;
create policy "room_invitations_read_own" on public.room_invitations for SELECT to authenticated
  using (((invitee_id = ( SELECT auth.uid() AS uid)) OR (inviter_id = ( SELECT auth.uid() AS uid))))
  with check (null);

drop policy if exists "room_messages_insert_auth" on public.room_messages;
create policy "room_messages_insert_auth" on public.room_messages for INSERT to authenticated
  using (null)
  with check (((sender_id = ( SELECT auth.uid() AS uid)) AND (is_system = false) AND (EXISTS ( SELECT 1
   FROM chat_rooms r
  WHERE ((r.name = room_messages.room_name) AND ((r.is_moderator_only = false) OR ( SELECT is_moderator() AS is_moderator))))) AND ( SELECT is_active_room_member(room_messages.room_name) AS is_active_room_member) AND (NOT (EXISTS ( SELECT 1
   FROM room_sanctions s
  WHERE ((s.user_id = ( SELECT auth.uid() AS uid)) AND (s.room_name = room_messages.room_name) AND (s.sanction = ANY (ARRAY['mute'::text, 'kick'::text])) AND (s.active = true) AND ((s.expires_at IS NULL) OR (s.expires_at > now()))))))));

drop policy if exists "room_messages_moderator_delete" on public.room_messages;
create policy "room_messages_moderator_delete" on public.room_messages for DELETE to authenticated
  using (( SELECT is_moderator() AS is_moderator))
  with check (null);

drop policy if exists "room_messages_read" on public.room_messages;
create policy "room_messages_read" on public.room_messages for SELECT to authenticated
  using ((( SELECT is_moderator() AS is_moderator) OR ( SELECT is_active_room_member(room_messages.room_name) AS is_active_room_member)))
  with check (null);

drop policy if exists "participants_delete_own" on public.room_participants;
create policy "participants_delete_own" on public.room_participants for DELETE to authenticated
  using ((owner_id = ( SELECT auth.uid() AS uid)))
  with check (null);

drop policy if exists "participants_insert_own" on public.room_participants;
create policy "participants_insert_own" on public.room_participants for INSERT to authenticated
  using (null)
  with check (((owner_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM chat_rooms r
  WHERE ((r.name = room_participants.room_name) AND ((r.is_moderator_only = false) OR ( SELECT is_moderator() AS is_moderator))))) AND (NOT (EXISTS ( SELECT 1
   FROM room_sanctions s
  WHERE ((s.user_id = ( SELECT auth.uid() AS uid)) AND (s.room_name = room_participants.room_name) AND (s.sanction = 'kick'::text) AND (s.active = true) AND ((s.expires_at IS NULL) OR (s.expires_at > now()))))))));

drop policy if exists "participants_read" on public.room_participants;
create policy "participants_read" on public.room_participants for SELECT to authenticated
  using (((last_ping >= (now() - '00:00:45'::interval)) AND (( SELECT is_moderator() AS is_moderator) OR ( SELECT is_active_room_member(room_participants.room_name) AS is_active_room_member))))
  with check (null);

drop policy if exists "participants_update_own" on public.room_participants;
create policy "participants_update_own" on public.room_participants for UPDATE to authenticated
  using ((owner_id = ( SELECT auth.uid() AS uid)))
  with check ((owner_id = ( SELECT auth.uid() AS uid)));

drop policy if exists "room_sanctions_mod_read" on public.room_sanctions;
create policy "room_sanctions_mod_read" on public.room_sanctions for SELECT to authenticated
  using (is_moderator())
  with check (null);

drop policy if exists "Service role full access on fcm tokens" on public.user_fcm_tokens;
create policy "Service role full access on fcm tokens" on public.user_fcm_tokens for ALL to service_role;

drop policy if exists "Users can manage their own fcm tokens" on public.user_fcm_tokens;
create policy "Users can manage their own fcm tokens" on public.user_fcm_tokens for ALL to authenticated
  using ((user_id = ( SELECT auth.uid() AS uid)))
  with check ((user_id = ( SELECT auth.uid() AS uid)));

drop policy if exists "user_reports_insert_own" on public.user_reports;
create policy "user_reports_insert_own" on public.user_reports for INSERT to authenticated
  using (null)
  with check ((reporter_id = ( SELECT auth.uid() AS uid)));

drop policy if exists "user_reports_moderator_update" on public.user_reports;
create policy "user_reports_moderator_update" on public.user_reports for UPDATE to authenticated
  using (( SELECT is_moderator() AS is_moderator))
  with check (( SELECT is_moderator() AS is_moderator));

drop policy if exists "user_reports_read_own_or_moderator" on public.user_reports;
create policy "user_reports_read_own_or_moderator" on public.user_reports for SELECT to authenticated
  using (((reporter_id = ( SELECT auth.uid() AS uid)) OR is_moderator()))
  with check (null);


-- ---------------------------------------------------------------------------
-- FUNCTIONS (captured verbatim from live pg_get_functiondef)
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.apply_moderation_action_trigger()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
$function$;

revoke all on function public.apply_moderation_action_trigger() from public;

grant execute on function public.apply_moderation_action_trigger() to authenticated;

CREATE OR REPLACE FUNCTION public.can_moderate_target(p_target_id uuid)
 RETURNS boolean
 LANGUAGE plpgsql
 STABLE
 SET search_path TO 'public'
AS $function$
declare target_role text;
begin
  if (select auth.uid()) is null or p_target_id is null then return false; end if;
  if p_target_id = (select auth.uid()) then return false; end if;
  if public.is_admin() then return true; end if;
  if not public.is_moderator() then return false; end if;
  select role into target_role from public.moderator_roles where user_id=p_target_id limit 1;
  return coalesce(target_role,'user') <> 'admin';
end;
$function$;

revoke all on function public.can_moderate_target(p_target_id uuid) from public;

grant execute on function public.can_moderate_target(p_target_id uuid) to authenticated;

CREATE OR REPLACE FUNCTION public.cleanup_stale_room_participants()
 RETURNS void
 LANGUAGE sql
 SET search_path TO ''
AS $function$
  delete from public.room_participants
  where last_ping < now() - interval '45 seconds';
$function$;

revoke all on function public.cleanup_stale_room_participants() from public;

grant execute on function public.cleanup_stale_room_participants() to authenticated;

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

  select exists(
    select 1 from public.chat_rooms
    where name = trim(p_room_name)
  ) into room_ok;

  if not room_ok then
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
$function$;

revoke all on function public.create_room_invitation(p_room_name text, p_invitee_username text) from public;

grant execute on function public.create_room_invitation(p_room_name text, p_invitee_username text) to authenticated;

CREATE OR REPLACE FUNCTION public.enforce_direct_message_rate_limit()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  uid uuid := auth.uid();
  recent_short integer;
  recent_minute integer;
begin
  if uid is null then
    return new;
  end if;

  perform pg_advisory_xact_lock(hashtextextended(uid::text, 1));

  select count(*) into recent_short
  from public.direct_messages
  where sender_id = uid
    and created_at > now() - interval '10 seconds';

  if recent_short >= 5 then
    raise exception using
      errcode = 'P0001',
      message = 'DIRECT_MESSAGE_RATE_LIMIT_10S';
  end if;

  select count(*) into recent_minute
  from public.direct_messages
  where sender_id = uid
    and created_at > now() - interval '1 minute';

  if recent_minute >= 30 then
    raise exception using
      errcode = 'P0001',
      message = 'DIRECT_MESSAGE_RATE_LIMIT_1M';
  end if;

  return new;
end;
$function$;

revoke all on function public.enforce_direct_message_rate_limit() from public;

grant execute on function public.enforce_direct_message_rate_limit() to authenticated;

CREATE OR REPLACE FUNCTION public.enforce_message_rate_limit()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  uid uuid := auth.uid();
  recent_short integer;
  recent_minute integer;
begin
  if uid is null then
    return new;
  end if;

  perform pg_advisory_xact_lock(hashtextextended(uid::text, 0));

  select count(*) into recent_short
  from public.room_messages
  where sender_id = uid
    and created_at > now() - interval '10 seconds';

  if recent_short >= 5 then
    raise exception using
      errcode = 'P0001',
      message = 'MESSAGE_RATE_LIMIT_10S';
  end if;

  select count(*) into recent_minute
  from public.room_messages
  where sender_id = uid
    and created_at > now() - interval '1 minute';

  if recent_minute >= 30 then
    raise exception using
      errcode = 'P0001',
      message = 'MESSAGE_RATE_LIMIT_1M';
  end if;

  return new;
end;
$function$;

revoke all on function public.enforce_message_rate_limit() from public;

grant execute on function public.enforce_message_rate_limit() to authenticated;

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
$function$;

revoke all on function public.enforce_room_capacity() from public;

grant execute on function public.enforce_room_capacity() to authenticated;

CREATE OR REPLACE FUNCTION public.get_live_room_counts()
 RETURNS TABLE(room_name text, user_count bigint)
 LANGUAGE sql
 SET search_path TO ''
AS $function$
  select rp.room_name, count(*)::bigint
  from public.room_participants rp
  where rp.last_ping >= now() - interval '45 seconds'
  group by rp.room_name;
$function$;

revoke all on function public.get_live_room_counts() from public;

grant execute on function public.get_live_room_counts() to authenticated;

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
$function$;

revoke all on function public.handle_new_user() from public;

grant execute on function public.handle_new_user() to authenticated;

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
$function$;

revoke all on function public.is_active_room_member(p_room_name text) from public;

grant execute on function public.is_active_room_member(p_room_name text) to authenticated;

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
$function$;

revoke all on function public.is_admin() from public;

grant execute on function public.is_admin() to authenticated;

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
$function$;

revoke all on function public.is_moderator() from public;

grant execute on function public.is_moderator() to authenticated;

CREATE OR REPLACE FUNCTION public.is_moderator_or_admin()
 RETURNS boolean
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$ select public.is_moderator(); $function$;

revoke all on function public.is_moderator_or_admin() from public;

grant execute on function public.is_moderator_or_admin() to authenticated;

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
$function$;

revoke all on function public.join_room(p_room_name text) from public;

grant execute on function public.join_room(p_room_name text) to authenticated;

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
$function$;

revoke all on function public.lock_room_participant_identity() from public;

grant execute on function public.lock_room_participant_identity() to authenticated;

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
  select * into m from public.room_messages where id = p_message_id for update;
  if not found then return false; end if;
  if not public.can_moderate_target(m.sender_id) then
    raise exception 'moderation_target_protected';
  end if;
  delete from public.room_messages where id = p_message_id;
  insert into public.moderation_actions(
    moderator_id,target_user_id,target_username,action,action_type,room_name,message_id,reason
  ) values (
    (select auth.uid()),m.sender_id,m.sender,'delete_message','delete_message',m.room_name,p_message_id,left(coalesce(p_reason,''),500)
  );
  return true;
end;
$function$;

revoke all on function public.moderate_room_message(p_message_id bigint, p_reason text) from public;

grant execute on function public.moderate_room_message(p_message_id bigint, p_reason text) to authenticated;

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
$function$;

revoke all on function public.moderate_user(p_username text, p_action text, p_room_name text, p_duration_minutes integer, p_reason text) from public;

grant execute on function public.moderate_user(p_username text, p_action text, p_room_name text, p_duration_minutes integer, p_reason text) to authenticated;

CREATE OR REPLACE FUNCTION public.notify_buddy_request_push()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'extensions'
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
$function$;

revoke all on function public.notify_buddy_request_push() from public;

grant execute on function public.notify_buddy_request_push() to authenticated;

CREATE OR REPLACE FUNCTION public.notify_direct_message_push()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'net'
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
$function$;

revoke all on function public.notify_direct_message_push() from public;

grant execute on function public.notify_direct_message_push() to authenticated;

CREATE OR REPLACE FUNCTION public.notify_room_invitation_push()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'net'
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
$function$;

revoke all on function public.notify_room_invitation_push() from public;

grant execute on function public.notify_room_invitation_push() to authenticated;

CREATE OR REPLACE FUNCTION public.resolve_login_email(p_username text)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_email text;
  begin
    select p.email into v_email
      from public.profiles as p
        where lower(p.username) = lower(trim(p_username))
          limit 1;
            return v_email;
            end;
            $function$;

revoke all on function public.resolve_login_email(p_username text) from public;

grant execute on function public.resolve_login_email(p_username text) to anon, authenticated;

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
$function$;

revoke all on function public.resolve_user_report(p_report_id bigint, p_status text, p_reason text) from public;

grant execute on function public.resolve_user_report(p_report_id bigint, p_status text, p_reason text) to authenticated;

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
$function$;

revoke all on function public.respond_room_invitation(p_invitation_id bigint, p_accepted boolean) from public;

grant execute on function public.respond_room_invitation(p_invitation_id bigint, p_accepted boolean) to authenticated;

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
$function$;

revoke all on function public.respond_to_buddy_request(p_request_id bigint, p_accept boolean) from public;

grant execute on function public.respond_to_buddy_request(p_request_id bigint, p_accept boolean) to authenticated;

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
$function$;

revoke all on function public.revoke_moderation_action(p_action_id bigint) from public;

grant execute on function public.revoke_moderation_action(p_action_id bigint) to authenticated;

CREATE OR REPLACE FUNCTION public.room_bot_message(p_room text, p_body text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  insert into public.room_messages (room_name, sender, body, is_system, sender_id)
  values (p_room, 'UzzapBot', p_body, true, null);
end;
$function$;

revoke all on function public.room_bot_message(p_room text, p_body text) from public;

grant execute on function public.room_bot_message(p_room text, p_body text) to authenticated;

CREATE OR REPLACE FUNCTION public.room_participant_join_bot()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  perform public.room_bot_message(
    new.room_name,
    'Welcome to ' || new.room_name || ', ' || new.username || '!'
  );
  return new;
end;
$function$;

revoke all on function public.room_participant_join_bot() from public;

grant execute on function public.room_participant_join_bot() to authenticated;

CREATE OR REPLACE FUNCTION public.room_participant_leave_bot()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  -- Only announce an actual/active departure. Stale cleanup removes rows
  -- older than 45 seconds and should not generate leave spam.
  if old.last_ping >= now() - interval '45 seconds' then
    perform public.room_bot_message(
      old.room_name,
      old.username || ' left the room. 👋'
    );
  end if;
  return old;
end;
$function$;

revoke all on function public.room_participant_leave_bot() from public;

grant execute on function public.room_participant_leave_bot() to authenticated;

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
$function$;

revoke all on function public.sanitize_nickname(p_nickname text) from public;

grant execute on function public.sanitize_nickname(p_nickname text) to authenticated;

CREATE OR REPLACE FUNCTION public.set_buddy_owner()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$ begin if TG_OP = 'INSERT' then if auth.uid() is null then raise exception 'Authentication required'; end if; new.owner_id := auth.uid(); new.user_handle := coalesce((select p.username from public.profiles p where p.owner_id = auth.uid() limit 1), 'user_' || left(auth.uid()::text, 8)); end if; return new; end; $function$;

revoke all on function public.set_buddy_owner() from public;

grant execute on function public.set_buddy_owner() to authenticated;

CREATE OR REPLACE FUNCTION public.set_moderator_by_email(target_email text, make_moderator boolean)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$ declare target_id uuid; begin if not public.is_admin() then raise exception 'admin_required'; end if; select id into target_id from auth.users where lower(email)=lower(trim(target_email)) limit 1; if target_id is null then return false; end if; if make_moderator then insert into public.moderator_roles(user_id,role,created_by) values(target_id,'moderator',auth.uid()) on conflict(user_id) do update set role='moderator',created_by=auth.uid(); else delete from public.moderator_roles where user_id=target_id; end if; return true; end; $function$;

revoke all on function public.set_moderator_by_email(target_email text, make_moderator boolean) from public;

grant execute on function public.set_moderator_by_email(target_email text, make_moderator boolean) to authenticated;

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
$function$;

revoke all on function public.set_participant_owner() from public;

grant execute on function public.set_participant_owner() to authenticated;

CREATE OR REPLACE FUNCTION public.set_profile_nickname_plain()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  new.nickname := nullif(left(public.sanitize_nickname(new.nickname), 64), '');
  return new;
end;
$function$;

revoke all on function public.set_profile_nickname_plain() from public;

grant execute on function public.set_profile_nickname_plain() to authenticated;

CREATE OR REPLACE FUNCTION public.set_room_message_sender()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  actor uuid := auth.uid();
  display_name text;
begin
  if actor is null then
    if coalesce(new.is_system, false) then
      return new;
    end if;
    raise exception 'Authentication required';
  end if;

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
$function$;

revoke all on function public.set_room_message_sender() from public;

grant execute on function public.set_room_message_sender() to authenticated;

CREATE OR REPLACE FUNCTION public.sync_moderation_action()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$ begin if new.action_type is null and new.action is not null then new.action_type:=new.action; end if; if new.action is null and new.action_type is not null then new.action:=new.action_type; end if; if new.action_type is null then raise exception 'action_type_required'; end if; if new.action_type not in ('mute','kick','unmute','unkick','warn','ban','unban','delete_message','resolve_report') then raise exception 'invalid_moderation_action'; end if; return new; end; $function$;

revoke all on function public.sync_moderation_action() from public;

grant execute on function public.sync_moderation_action() to authenticated;

CREATE OR REPLACE FUNCTION public.touch_my_presence(p_is_online boolean DEFAULT true)
 RETURNS void
 LANGUAGE sql
 SET search_path TO 'public'
AS $function$ update public.profiles set is_online=p_is_online,last_seen=now() where owner_id=auth.uid(); $function$;

revoke all on function public.touch_my_presence(p_is_online boolean) from public;

grant execute on function public.touch_my_presence(p_is_online boolean) to authenticated;


-- ---------------------------------------------------------------------------
-- TRIGGERS (captured from live pg_trigger)
-- ---------------------------------------------------------------------------

drop trigger if exists on_auth_user_created on auth.users;

CREATE TRIGGER on_auth_user_created AFTER INSERT ON auth.users FOR EACH ROW EXECUTE FUNCTION handle_new_user();

drop trigger if exists tr_check_filters on realtime.subscription;

CREATE TRIGGER tr_check_filters BEFORE INSERT OR UPDATE ON realtime.subscription FOR EACH ROW EXECUTE FUNCTION realtime.subscription_check_filters();

drop trigger if exists enforce_bucket_name_length_trigger on storage.buckets;

CREATE TRIGGER enforce_bucket_name_length_trigger BEFORE INSERT OR UPDATE OF name ON storage.buckets FOR EACH ROW EXECUTE FUNCTION storage.enforce_bucket_name_length();

drop trigger if exists protect_buckets_delete on storage.buckets;

CREATE TRIGGER protect_buckets_delete BEFORE DELETE ON storage.buckets FOR EACH STATEMENT EXECUTE FUNCTION storage.protect_delete();

drop trigger if exists protect_objects_delete on storage.objects;

CREATE TRIGGER protect_objects_delete BEFORE DELETE ON storage.objects FOR EACH STATEMENT EXECUTE FUNCTION storage.protect_delete();

drop trigger if exists update_objects_updated_at on storage.objects;

CREATE TRIGGER update_objects_updated_at BEFORE UPDATE ON storage.objects FOR EACH ROW EXECUTE FUNCTION storage.update_updated_at_column();

drop trigger if exists trg_profile_nickname_plain on profiles;

CREATE TRIGGER trg_profile_nickname_plain BEFORE INSERT OR UPDATE OF nickname ON public.profiles FOR EACH ROW EXECUTE FUNCTION set_profile_nickname_plain();

drop trigger if exists trg_room_message_rate_limit on room_messages;

CREATE TRIGGER trg_room_message_rate_limit BEFORE INSERT ON public.room_messages FOR EACH ROW EXECUTE FUNCTION enforce_message_rate_limit();

drop trigger if exists trg_room_message_sender on room_messages;

CREATE TRIGGER trg_room_message_sender BEFORE INSERT ON public.room_messages FOR EACH ROW EXECUTE FUNCTION set_room_message_sender();

drop trigger if exists trg_lock_room_participant_identity on room_participants;

CREATE TRIGGER trg_lock_room_participant_identity BEFORE UPDATE ON public.room_participants FOR EACH ROW EXECUTE FUNCTION lock_room_participant_identity();

drop trigger if exists trg_participant_owner on room_participants;

CREATE TRIGGER trg_participant_owner BEFORE INSERT OR UPDATE ON public.room_participants FOR EACH ROW EXECUTE FUNCTION set_participant_owner();

drop trigger if exists trg_room_participant_leave_bot on room_participants;

CREATE TRIGGER trg_room_participant_leave_bot AFTER DELETE ON public.room_participants FOR EACH ROW EXECUTE FUNCTION room_participant_leave_bot();

drop trigger if exists trg_room_participant_welcome on room_participants;

CREATE TRIGGER trg_room_participant_welcome AFTER INSERT ON public.room_participants FOR EACH ROW EXECUTE FUNCTION room_participant_join_bot();

drop trigger if exists trg_room_participants_capacity on room_participants;

CREATE TRIGGER trg_room_participants_capacity BEFORE INSERT ON public.room_participants FOR EACH ROW EXECUTE FUNCTION enforce_room_capacity();

drop trigger if exists trg_buddy_owner on buddies;

CREATE TRIGGER trg_buddy_owner BEFORE INSERT OR UPDATE ON public.buddies FOR EACH ROW EXECUTE FUNCTION set_buddy_owner();

drop trigger if exists on_direct_message_push on direct_messages;

CREATE TRIGGER on_direct_message_push AFTER INSERT ON public.direct_messages FOR EACH ROW EXECUTE FUNCTION notify_direct_message_push();

drop trigger if exists trg_direct_message_rate_limit on direct_messages;

CREATE TRIGGER trg_direct_message_rate_limit BEFORE INSERT ON public.direct_messages FOR EACH ROW EXECUTE FUNCTION enforce_direct_message_rate_limit();

drop trigger if exists trg_apply_moderation_action on moderation_actions;

CREATE TRIGGER trg_apply_moderation_action AFTER INSERT ON public.moderation_actions FOR EACH ROW EXECUTE FUNCTION apply_moderation_action_trigger();

drop trigger if exists trg_sync_moderation_action on moderation_actions;

CREATE TRIGGER trg_sync_moderation_action BEFORE INSERT OR UPDATE ON public.moderation_actions FOR EACH ROW EXECUTE FUNCTION sync_moderation_action();

drop trigger if exists on_room_invitation_push on room_invitations;

CREATE TRIGGER on_room_invitation_push AFTER INSERT ON public.room_invitations FOR EACH ROW EXECUTE FUNCTION notify_room_invitation_push();

drop trigger if exists on_buddy_request_push on buddy_requests;

CREATE TRIGGER on_buddy_request_push AFTER INSERT ON public.buddy_requests FOR EACH ROW EXECUTE FUNCTION notify_buddy_request_push();


-- ---------------------------------------------------------------------------
-- REALTIME publication (idempotent membership)
-- ---------------------------------------------------------------------------

do $$
begin
  perform 1 from pg_publication where pubname = 'supabase_realtime';
  if found then
    begin
      execute 'alter publication supabase_realtime add table public.room_messages, public.direct_messages, public.profiles, public.room_invitations, public.buddy_requests';
    exception when duplicate_object then null;
    end;
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- FCM token registration (f17): SECURITY DEFINER upsert so a token row can
-- change ownership across accounts/devices without hitting owner-scoped RLS.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.upsert_fcm_token(p_token text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  insert into public.user_fcm_tokens (user_id, username, fcm_token, updated_at)
  values (
    auth.uid(),
    (select p.username from public.profiles p where p.owner_id = auth.uid()),
    p_token,
    now()
  )
  on conflict (fcm_token) do update set
    user_id = excluded.user_id,
    username = excluded.username,
    updated_at = now();
end;
$function$;

revoke all on function public.upsert_fcm_token(text) from public;
grant execute on function public.upsert_fcm_token(text) to authenticated;
