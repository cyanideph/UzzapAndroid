-- Uzzap Phase 1 P0 Security Foundation
-- Applied to Supabase project hrjgblvxhtbopuyzyasu.

-- Pin SECURITY DEFINER function search paths to prevent search_path hijacking.
ALTER FUNCTION public.create_room_invitation(text, text) SET search_path = '';
ALTER FUNCTION public.moderate_room_message(bigint, text) SET search_path = '';
ALTER FUNCTION public.respond_room_invitation(bigint, boolean) SET search_path = '';
ALTER FUNCTION public.respond_to_buddy_request(bigint, boolean) SET search_path = '';
ALTER FUNCTION public.revoke_moderation_action(bigint) SET search_path = '';
ALTER FUNCTION public.set_moderator_by_email(text, boolean) SET search_path = '';
ALTER FUNCTION public.set_room_message_sender() SET search_path = '';
ALTER FUNCTION public.sanitize_nickname(text) SET search_path = '';

-- Enforce one profile row per authenticated owner.
CREATE UNIQUE INDEX IF NOT EXISTS profiles_owner_id_unique
  ON public.profiles(owner_id)
  WHERE owner_id IS NOT NULL;

-- Anonymous clients should not have direct application-table DML privileges.
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA public FROM anon;

-- Authenticated clients should never have direct TRUNCATE capability.
REVOKE TRUNCATE ON ALL TABLES IN SCHEMA public FROM authenticated;

-- Prevent future tables from inheriting these unnecessary privileges.
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON TABLES FROM anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE TRUNCATE ON TABLES FROM authenticated;
