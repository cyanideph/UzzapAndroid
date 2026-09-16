-- Repair legacy welcome rows created before the system-message trigger fix.
update public.room_messages
set sender = 'UzzapBot',
    sender_id = null,
    is_system = true
where body ~* '^Welcome to .+, .+!$'
  and coalesce(is_system, false) = false;

-- Nicknames are no longer colorized. Normalize legacy sender values that contain color markup.
update public.room_messages
set sender = public.sanitize_nickname(sender)
where coalesce(is_system, false) = false
  and sender ~* '\[c[0-9]{2}';
