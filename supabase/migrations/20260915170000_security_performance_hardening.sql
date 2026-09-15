-- Security/performance hardening applied to the production project.
-- Keep this migration in source control so repository state matches production.

-- Trigger-only rate limit helpers must not be directly callable through the Data API.
revoke execute on function public.enforce_message_rate_limit() from public, anon, authenticated;
revoke execute on function public.enforce_direct_message_rate_limit() from public, anon, authenticated;

-- FK indexes for moderation/report query paths.
create index if not exists moderation_actions_message_id_idx on public.moderation_actions(message_id);
create index if not exists moderation_actions_moderator_id_idx on public.moderation_actions(moderator_id);
create index if not exists moderation_actions_target_user_id_idx on public.moderation_actions(target_user_id);
create index if not exists moderator_roles_created_by_idx on public.moderator_roles(created_by);
create index if not exists room_sanctions_created_by_idx on public.room_sanctions(created_by);
create index if not exists user_reports_message_id_idx on public.user_reports(message_id);
create index if not exists user_reports_reviewed_by_idx on public.user_reports(reviewed_by);

-- Remove the duplicate unique index; the remaining unique constraint already
-- enforces (room_name, username).
drop index if exists public.room_participants_room_username_uidx;

-- Consolidate overlapping SELECT policies.
drop policy if exists room_sanctions_mod_read on public.room_sanctions;

drop policy if exists user_reports_moderator_read on public.user_reports;
drop policy if exists user_reports_read_own on public.user_reports;
create policy user_reports_read_own_or_moderator
  on public.user_reports
  for select to public
  using (
    reporter_id = (select auth.uid())
    or (select is_moderator())
  );
