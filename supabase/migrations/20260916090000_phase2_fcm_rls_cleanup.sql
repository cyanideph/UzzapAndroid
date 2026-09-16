-- Phase 2: bind FCM registrations to the authenticated auth.uid().
-- Do not authorize rows from user-controlled JWT metadata such as user_metadata.

ALTER TABLE public.user_fcm_tokens
  ALTER COLUMN user_id SET NOT NULL;

DROP POLICY IF EXISTS "Users can manage their own fcm tokens" ON public.user_fcm_tokens;

CREATE POLICY "Users can manage their own fcm tokens"
ON public.user_fcm_tokens
FOR ALL
TO authenticated
USING (user_id = (select auth.uid()))
WITH CHECK (user_id = (select auth.uid()));

CREATE INDEX IF NOT EXISTS idx_user_fcm_tokens_updated_at
  ON public.user_fcm_tokens (updated_at DESC);

CREATE OR REPLACE FUNCTION public.touch_user_fcm_token_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_user_fcm_tokens_updated_at ON public.user_fcm_tokens;
CREATE TRIGGER trg_user_fcm_tokens_updated_at
BEFORE UPDATE ON public.user_fcm_tokens
FOR EACH ROW
EXECUTE FUNCTION public.touch_user_fcm_token_updated_at();

REVOKE EXECUTE ON FUNCTION public.touch_user_fcm_token_updated_at() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.touch_user_fcm_token_updated_at() FROM anon;
REVOKE EXECUTE ON FUNCTION public.touch_user_fcm_token_updated_at() FROM authenticated;
