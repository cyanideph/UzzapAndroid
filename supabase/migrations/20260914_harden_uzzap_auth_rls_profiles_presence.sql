-- Uzzap hardening migration
-- Keeps the Android client on the publishable/anon key while moving identity,
-- ownership, capacity and access control into PostgreSQL/RLS.

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS owner_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS email TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS profiles_owner_id_uidx ON public.profiles(owner_id) WHERE owner_id IS NOT NULL;

ALTER TABLE public.room_messages ADD COLUMN IF NOT EXISTS sender_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_room_messages_sender_id ON public.room_messages(sender_id);
ALTER TABLE public.room_participants ADD COLUMN IF NOT EXISTS owner_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_room_participants_owner ON public.room_participants(owner_id);
ALTER TABLE public.buddies ADD COLUMN IF NOT EXISTS owner_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_buddies_owner ON public.buddies(owner_id);
ALTER TABLE public.direct_messages ADD COLUMN IF NOT EXISTS sender_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;
ALTER TABLE public.direct_messages ADD COLUMN IF NOT EXISTS recipient_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_dm_sender ON public.direct_messages(sender_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dm_recipient ON public.direct_messages(recipient_id, created_at DESC);

UPDATE public.profiles p
SET owner_id = u.id
FROM auth.users u
WHERE p.owner_id IS NULL
  AND p.email IS NOT NULL
  AND lower(p.email) = lower(u.email)
  AND NOT EXISTS (SELECT 1 FROM public.profiles p2 WHERE p2.owner_id = u.id);

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  INSERT INTO public.profiles (id, owner_id, username, nickname, email, status, status_message, avatar, is_online, last_seen)
  VALUES (
    NEW.id, NEW.id,
    COALESCE(NULLIF(lower(regexp_replace(NEW.raw_user_meta_data ->> 'username', '[^a-zA-Z0-9_]+', '_', 'g')), ''), 'user_' || left(NEW.id::text, 8)),
    COALESCE(NULLIF(NEW.raw_user_meta_data ->> 'nickname', ''), split_part(COALESCE(NEW.email, 'user'), '@', 1)),
    NEW.email, 'Available', 'New Uzzap user :)', 'kool', true, now()
  )
  ON CONFLICT (owner_id) DO UPDATE SET email = EXCLUDED.email;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created AFTER INSERT ON auth.users FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

CREATE OR REPLACE FUNCTION public.set_room_message_sender()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
  NEW.sender_id := auth.uid();
  NEW.sender := COALESCE((SELECT p.username FROM public.profiles p WHERE p.owner_id = auth.uid() LIMIT 1), 'user_' || left(auth.uid()::text, 8));
  NEW.is_system := false;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_room_message_sender ON public.room_messages;
CREATE TRIGGER trg_room_message_sender BEFORE INSERT ON public.room_messages FOR EACH ROW EXECUTE FUNCTION public.set_room_message_sender();

CREATE OR REPLACE FUNCTION public.set_participant_owner()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
  NEW.owner_id := auth.uid();
  NEW.username := COALESCE((SELECT p.username FROM public.profiles p WHERE p.owner_id = auth.uid() LIMIT 1), 'user_' || left(auth.uid()::text, 8));
  NEW.last_ping := now();
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_participant_owner ON public.room_participants;
CREATE TRIGGER trg_participant_owner BEFORE INSERT OR UPDATE ON public.room_participants FOR EACH ROW EXECUTE FUNCTION public.set_participant_owner();

CREATE OR REPLACE FUNCTION public.enforce_room_capacity()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path = public AS $$
DECLARE cap integer; active_count integer;
BEGIN
  PERFORM pg_advisory_xact_lock(hashtext(NEW.room_name));
  SELECT r.capacity INTO cap FROM public.chat_rooms r WHERE r.name = NEW.room_name LIMIT 1;
  IF cap IS NULL THEN RAISE EXCEPTION 'Room not found'; END IF;
  SELECT count(*) INTO active_count FROM public.room_participants p
    WHERE p.room_name = NEW.room_name AND p.last_ping > now() - interval '90 seconds' AND p.owner_id <> auth.uid();
  IF active_count >= cap THEN RAISE EXCEPTION 'Room is full'; END IF;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_room_capacity ON public.room_participants;
CREATE TRIGGER trg_room_capacity BEFORE INSERT ON public.room_participants FOR EACH ROW EXECUTE FUNCTION public.enforce_room_capacity();

CREATE OR REPLACE FUNCTION public.set_buddy_owner()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
  NEW.owner_id := auth.uid();
  NEW.user_handle := COALESCE((SELECT p.username FROM public.profiles p WHERE p.owner_id = auth.uid() LIMIT 1), 'user_' || left(auth.uid()::text, 8));
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_buddy_owner ON public.buddies;
CREATE TRIGGER trg_buddy_owner BEFORE INSERT OR UPDATE ON public.buddies FOR EACH ROW EXECUTE FUNCTION public.set_buddy_owner();

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.buddies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.direct_messages ENABLE ROW LEVEL SECURITY;

DO $$ DECLARE t text; p text; BEGIN
  FOREACH t IN ARRAY ARRAY['profiles','chat_categories','chat_rooms','room_messages','room_participants','buddies','direct_messages'] LOOP
    FOR p IN SELECT policyname FROM pg_policies WHERE schemaname='public' AND tablename=t LOOP
      EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', p, t);
    END LOOP;
  END LOOP;
END $$;

CREATE POLICY profiles_read ON public.profiles FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY profiles_insert_own ON public.profiles FOR INSERT TO authenticated WITH CHECK (owner_id = auth.uid());
CREATE POLICY profiles_update_own ON public.profiles FOR UPDATE TO authenticated USING (owner_id = auth.uid()) WITH CHECK (owner_id = auth.uid());
CREATE POLICY profiles_delete_own ON public.profiles FOR DELETE TO authenticated USING (owner_id = auth.uid());
CREATE POLICY categories_read ON public.chat_categories FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY rooms_read ON public.chat_rooms FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY room_messages_read ON public.room_messages FOR SELECT TO authenticated USING (true);
CREATE POLICY room_messages_insert_auth ON public.room_messages FOR INSERT TO authenticated WITH CHECK (sender_id = auth.uid() AND is_system = false);
CREATE POLICY participants_read ON public.room_participants FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY participants_insert_own ON public.room_participants FOR INSERT TO authenticated WITH CHECK (owner_id = auth.uid());
CREATE POLICY participants_update_own ON public.room_participants FOR UPDATE TO authenticated USING (owner_id = auth.uid()) WITH CHECK (owner_id = auth.uid());
CREATE POLICY participants_delete_own ON public.room_participants FOR DELETE TO authenticated USING (owner_id = auth.uid());
CREATE POLICY buddies_read_own ON public.buddies FOR SELECT TO authenticated USING (owner_id = auth.uid());
CREATE POLICY buddies_insert_own ON public.buddies FOR INSERT TO authenticated WITH CHECK (owner_id = auth.uid());
CREATE POLICY buddies_update_own ON public.buddies FOR UPDATE TO authenticated USING (owner_id = auth.uid()) WITH CHECK (owner_id = auth.uid());
CREATE POLICY buddies_delete_own ON public.buddies FOR DELETE TO authenticated USING (owner_id = auth.uid());
CREATE POLICY dm_read_participant ON public.direct_messages FOR SELECT TO authenticated USING (sender_id = auth.uid() OR recipient_id = auth.uid());
CREATE POLICY dm_insert_sender ON public.direct_messages FOR INSERT TO authenticated WITH CHECK (sender_id = auth.uid());
CREATE POLICY dm_update_recipient ON public.direct_messages FOR UPDATE TO authenticated USING (recipient_id = auth.uid()) WITH CHECK (recipient_id = auth.uid());

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='room_messages') THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.room_messages;
  END IF;
END $$;
