create or replace function public.notify_room_invitation_push()
returns trigger
language plpgsql
security definer
set search_path = public, net
as $$
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
$$;

drop trigger if exists on_room_invitation_push on public.room_invitations;
create trigger on_room_invitation_push
after insert on public.room_invitations
for each row execute function public.notify_room_invitation_push();

revoke execute on function public.notify_room_invitation_push() from public, anon, authenticated;
