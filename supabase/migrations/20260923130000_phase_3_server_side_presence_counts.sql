-- Phase 3 query optimization: server-side aggregate counts.
-- SECURITY INVOKER keeps existing RLS visibility semantics.
create index if not exists idx_room_participants_room_ping
    on public.room_participants (room_name, last_ping desc);

create or replace function public.get_live_room_counts()
returns table(room_name text, user_count bigint)
language sql
stable
security invoker
set search_path = public
as $$
    select rp.room_name, count(*)::bigint
    from public.room_participants rp
    where rp.last_ping >= now() - interval '45 seconds'
    group by rp.room_name
    order by rp.room_name;
$$;

grant execute on function public.get_live_room_counts() to authenticated;

create or replace function public.get_online_user_count()
returns table(user_count bigint)
language sql
stable
security invoker
set search_path = public
as $$
    select count(*)::bigint
    from public.profiles p
    where p.is_online = true;
$$;

grant execute on function public.get_online_user_count() to authenticated;
