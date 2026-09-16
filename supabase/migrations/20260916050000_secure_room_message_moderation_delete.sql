-- Secure moderator/admin deletion of room messages.
-- The Android client calls public.moderate_room_message through PostgREST RPC.
-- Authorization is enforced here; UI visibility is not a security boundary.

create or replace function public.moderate_room_message(
  p_message_id bigint,
  p_reason text default ''
)
returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  m public.room_messages%rowtype;
  actor uuid := auth.uid();
begin
  if actor is null or not public.is_moderator() then
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

  delete from public.room_messages
  where id = p_message_id;

  insert into public.moderation_actions (
    moderator_id,
    target_user_id,
    target_username,
    action,
    action_type,
    room_name,
    message_id,
    reason
  ) values (
    actor,
    m.sender_id,
    m.sender,
    'delete_message',
    'delete_message',
    m.room_name,
    p_message_id,
    left(coalesce(p_reason, ''), 500)
  );

  return true;
end;
$$;

revoke all on function public.moderate_room_message(bigint, text) from public;
revoke all on function public.moderate_room_message(bigint, text) from anon;
grant execute on function public.moderate_room_message(bigint, text) to authenticated;
