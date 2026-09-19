-- Nickname login uses this RPC from the Android client with the publishable/anon key.
-- Keep the SECURITY DEFINER search_path pinned, but allow the client roles to resolve
-- a nickname to its login email before calling Supabase Auth password sign-in.
create or replace function public.resolve_login_email(p_username text)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_email text;
begin
  select p.email into v_email
  from public.profiles as p
  where lower(p.username) = lower(trim(p_username))
  limit 1;
  return v_email;
end;
$$;

revoke all on function public.resolve_login_email(text) from public;
grant execute on function public.resolve_login_email(text) to anon, authenticated, service_role;
