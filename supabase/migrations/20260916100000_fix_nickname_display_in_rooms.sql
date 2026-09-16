-- Keep account username and display nickname separate.
-- Room messages must persist the user's nickname markup, not the login username.
create or replace function public.set_room_message_sender()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  new.sender_id := auth.uid();
  new.sender := coalesce(
    (select p.nickname from public.profiles p where p.owner_id = auth.uid() limit 1),
    (select p.username from public.profiles p where p.owner_id = auth.uid() limit 1),
    'user_' || left(auth.uid()::text, 8)
  );
  new.is_system := false;
  return new;
end;
$$;

revoke all on function public.set_room_message_sender() from public;
grant execute on function public.set_room_message_sender() to authenticated;
