-- Uzzap Phase 2 fix: RLS policies may call this helper as authenticated.
-- Keep it SECURITY DEFINER with an empty search_path; grant only EXECUTE to authenticated.

grant execute on function public.is_active_room_member(text) to authenticated;
revoke execute on function public.is_active_room_member(text) from anon;
