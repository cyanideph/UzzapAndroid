-- Direct-message parity layer for the modern Kotlin client.
-- The base schema already defines public.direct_messages; this migration
-- hardens the identity columns and enables realtime delivery.

ALTER TABLE public.direct_messages
  ADD COLUMN IF NOT EXISTS sender_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS recipient_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_direct_messages_sender_created
  ON public.direct_messages(sender_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_direct_messages_recipient_created
  ON public.direct_messages(recipient_id, created_at DESC);

ALTER TABLE public.direct_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS dm_read_participant ON public.direct_messages;
DROP POLICY IF EXISTS dm_insert_sender ON public.direct_messages;
DROP POLICY IF EXISTS dm_update_recipient ON public.direct_messages;

CREATE POLICY dm_read_participant
  ON public.direct_messages FOR SELECT TO authenticated
  USING (sender_id = auth.uid() OR recipient_id = auth.uid());

CREATE POLICY dm_insert_sender
  ON public.direct_messages FOR INSERT TO authenticated
  WITH CHECK (sender_id = auth.uid());

CREATE POLICY dm_update_recipient
  ON public.direct_messages FOR UPDATE TO authenticated
  USING (recipient_id = auth.uid())
  WITH CHECK (recipient_id = auth.uid());

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime'
      AND schemaname = 'public'
      AND tablename = 'direct_messages'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.direct_messages;
  END IF;
END $$;
