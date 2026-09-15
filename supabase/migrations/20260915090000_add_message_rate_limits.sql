-- Server-side anti-spam limits for authenticated chat senders.
-- Keep limits intentionally conservative for the beta client.

create or replace function public.enforce_message_rate_limit()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
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
$$;

revoke all on function public.enforce_message_rate_limit() from public;
grant execute on function public.enforce_message_rate_limit() to authenticated;

drop trigger if exists trg_room_message_rate_limit on public.room_messages;
create trigger trg_room_message_rate_limit
before insert on public.room_messages
for each row execute function public.enforce_message_rate_limit();

create or replace function public.enforce_direct_message_rate_limit()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
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
$$;

revoke all on function public.enforce_direct_message_rate_limit() from public;
grant execute on function public.enforce_direct_message_rate_limit() to authenticated;

drop trigger if exists trg_direct_message_rate_limit on public.direct_messages;
create trigger trg_direct_message_rate_limit
before insert on public.direct_messages
for each row execute function public.enforce_direct_message_rate_limit();
