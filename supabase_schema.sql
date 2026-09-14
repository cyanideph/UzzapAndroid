-- ==========================================================
-- UZZAP - SECURE SUPABASE SCHEMA
--
-- Chat structure:
--   Region = chat category
--   Province = chat room
--
-- The Philippines currently has 18 regions. NCR is included as
-- a region with Metro Manila as its single special room because
-- NCR has no provinces.
--
-- IMPORTANT:
--   * The Android app uses only the Supabase ANON key.
--   * Authentication is handled by Supabase Auth.
--   * Client writes are restricted by RLS to the authenticated user.
--   * Never put a service-role key in the APK.
-- ==========================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT UNIQUE NOT NULL,
    nickname TEXT NOT NULL,
    email TEXT,
    status TEXT NOT NULL DEFAULT 'Available',
    status_message TEXT NOT NULL DEFAULT '',
    avatar TEXT NOT NULL DEFAULT 'kool',
    is_online BOOLEAN NOT NULL DEFAULT true,
    last_seen TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS owner_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
CREATE UNIQUE INDEX IF NOT EXISTS profiles_owner_id_uidx ON public.profiles(owner_id) WHERE owner_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.chat_categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS public.chat_rooms (
    id TEXT PRIMARY KEY,
    category_id TEXT REFERENCES public.chat_categories(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    topic TEXT NOT NULL DEFAULT '',
    capacity INT NOT NULL DEFAULT 50 CHECK (capacity > 0 AND capacity <= 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.room_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_name TEXT NOT NULL,
    sender TEXT NOT NULL,
    body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    is_system BOOLEAN NOT NULL DEFAULT false,
    sender_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.room_messages ADD COLUMN IF NOT EXISTS sender_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_room_messages_room_id ON public.room_messages (room_name, id ASC);
CREATE INDEX IF NOT EXISTS idx_room_messages_sender_id ON public.room_messages (sender_id);

CREATE OR REPLACE FUNCTION public.set_room_message_sender()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF auth.uid() IS NOT NULL THEN
        NEW.sender_id := auth.uid();
        NEW.sender := COALESCE(
            (SELECT username FROM public.profiles WHERE owner_id = auth.uid() LIMIT 1),
            'user_' || left(auth.uid()::text, 8)
        );
        NEW.is_system := false;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_room_message_sender ON public.room_messages;
CREATE TRIGGER trg_room_message_sender
BEFORE INSERT ON public.room_messages
FOR EACH ROW EXECUTE FUNCTION public.set_room_message_sender();

CREATE TABLE IF NOT EXISTS public.room_participants (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_name TEXT NOT NULL,
    username TEXT NOT NULL,
    owner_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    last_ping TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (room_name, username)
);
ALTER TABLE public.room_participants ADD COLUMN IF NOT EXISTS owner_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_room_participants_owner ON public.room_participants(owner_id);

CREATE OR REPLACE FUNCTION public.set_participant_owner()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF auth.uid() IS NOT NULL THEN
        NEW.owner_id := auth.uid();
        NEW.username := COALESCE(
            (SELECT username FROM public.profiles WHERE owner_id = auth.uid() LIMIT 1),
            'user_' || left(auth.uid()::text, 8)
        );
        NEW.last_ping := now();
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_participant_owner ON public.room_participants;
CREATE TRIGGER trg_participant_owner
BEFORE INSERT OR UPDATE ON public.room_participants
FOR EACH ROW EXECUTE FUNCTION public.set_participant_owner();

CREATE TABLE IF NOT EXISTS public.buddies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_handle TEXT NOT NULL,
    buddy_handle TEXT NOT NULL,
    group_name TEXT NOT NULL DEFAULT 'My Friends',
    owner_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_handle, buddy_handle)
);
ALTER TABLE public.buddies ADD COLUMN IF NOT EXISTS owner_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_buddies_owner ON public.buddies(owner_id);

CREATE OR REPLACE FUNCTION public.set_buddy_owner()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF auth.uid() IS NOT NULL THEN
        NEW.owner_id := auth.uid();
        NEW.user_handle := COALESCE(
            (SELECT username FROM public.profiles WHERE owner_id = auth.uid() LIMIT 1),
            'user_' || left(auth.uid()::text, 8)
        );
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_buddy_owner ON public.buddies;
CREATE TRIGGER trg_buddy_owner
BEFORE INSERT OR UPDATE ON public.buddies
FOR EACH ROW EXECUTE FUNCTION public.set_buddy_owner();

CREATE TABLE IF NOT EXISTS public.direct_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender TEXT NOT NULL,
    recipient TEXT NOT NULL,
    body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    is_read BOOLEAN NOT NULL DEFAULT false,
    sender_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    recipient_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.direct_messages ADD COLUMN IF NOT EXISTS sender_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;
ALTER TABLE public.direct_messages ADD COLUMN IF NOT EXISTS recipient_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_dm_sender ON public.direct_messages(sender_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dm_recipient ON public.direct_messages(recipient_id, created_at DESC);

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.buddies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.direct_messages ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    t TEXT;
    p TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['profiles','chat_categories','chat_rooms','room_messages','room_participants','buddies','direct_messages'] LOOP
        FOR p IN SELECT policyname FROM pg_policies WHERE schemaname='public' AND tablename=t LOOP
            EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', p, t);
        END LOOP;
    END LOOP;
END $$;

CREATE POLICY profiles_read ON public.profiles FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY profiles_insert_own ON public.profiles FOR INSERT TO authenticated
    WITH CHECK (owner_id = auth.uid());
CREATE POLICY profiles_update_own ON public.profiles FOR UPDATE TO authenticated
    USING (owner_id = auth.uid()) WITH CHECK (owner_id = auth.uid());
CREATE POLICY profiles_delete_own ON public.profiles FOR DELETE TO authenticated
    USING (owner_id = auth.uid());

CREATE POLICY categories_read ON public.chat_categories FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY rooms_read ON public.chat_rooms FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY room_messages_read ON public.room_messages FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY room_messages_insert_auth ON public.room_messages FOR INSERT TO authenticated
    WITH CHECK (sender_id = auth.uid() AND is_system = false);

CREATE POLICY participants_read ON public.room_participants FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY participants_insert_own ON public.room_participants FOR INSERT TO authenticated
    WITH CHECK (owner_id = auth.uid());
CREATE POLICY participants_update_own ON public.room_participants FOR UPDATE TO authenticated
    USING (owner_id = auth.uid()) WITH CHECK (owner_id = auth.uid());
CREATE POLICY participants_delete_own ON public.room_participants FOR DELETE TO authenticated
    USING (owner_id = auth.uid());

CREATE POLICY buddies_read_own ON public.buddies FOR SELECT TO authenticated
    USING (owner_id = auth.uid());
CREATE POLICY buddies_insert_own ON public.buddies FOR INSERT TO authenticated
    WITH CHECK (owner_id = auth.uid());
CREATE POLICY buddies_update_own ON public.buddies FOR UPDATE TO authenticated
    USING (owner_id = auth.uid()) WITH CHECK (owner_id = auth.uid());
CREATE POLICY buddies_delete_own ON public.buddies FOR DELETE TO authenticated
    USING (owner_id = auth.uid());

CREATE POLICY dm_read_own ON public.direct_messages FOR SELECT TO authenticated
    USING (sender_id = auth.uid() OR recipient_id = auth.uid());
CREATE POLICY dm_insert_own ON public.direct_messages FOR INSERT TO authenticated
    WITH CHECK (sender_id = auth.uid());
CREATE POLICY dm_update_recipient ON public.direct_messages FOR UPDATE TO authenticated
    USING (recipient_id = auth.uid()) WITH CHECK (recipient_id = auth.uid());
CREATE POLICY dm_delete_sender ON public.direct_messages FOR DELETE TO authenticated
    USING (sender_id = auth.uid());

-- ==========================================================
-- PHILIPPINE REGION -> PROVINCE CHATROOM SEED
--
-- This intentionally removes the old prototype categories,
-- rooms, participants and room messages so the database matches
-- the new Uzzap geography-based design.
-- ==========================================================

DELETE FROM public.room_messages;
DELETE FROM public.room_participants;
DELETE FROM public.chat_rooms;
DELETE FROM public.chat_categories;

INSERT INTO public.chat_categories (id, name, sort_order) VALUES
    ('region_1', 'Region I - Ilocos Region', 1),
    ('car', 'Cordillera Administrative Region', 2),
    ('region_2', 'Region II - Cagayan Valley', 3),
    ('region_3', 'Region III - Central Luzon', 4),
    ('region_4a', 'Region IV-A - CALABARZON', 5),
    ('mimaropa', 'MIMAROPA Region', 6),
    ('region_5', 'Region V - Bicol Region', 7),
    ('region_6', 'Region VI - Western Visayas', 8),
    ('nir', 'Negros Island Region', 9),
    ('region_7', 'Region VII - Central Visayas', 10),
    ('region_8', 'Region VIII - Eastern Visayas', 11),
    ('region_9', 'Region IX - Zamboanga Peninsula', 12),
    ('region_10', 'Region X - Northern Mindanao', 13),
    ('region_11', 'Region XI - Davao Region', 14),
    ('region_12', 'Region XII - SOCCSKSARGEN', 15),
    ('caraga', 'Caraga', 16),
    ('barmm', 'Bangsamoro Autonomous Region in Muslim Mindanao', 17),
    ('ncr', 'National Capital Region', 18)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, sort_order = EXCLUDED.sort_order;

INSERT INTO public.chat_rooms (id, category_id, name, topic, capacity) VALUES
    -- Region I - Ilocos Region
    ('ilocos_norte', 'region_1', 'Ilocos Norte', 'Province chatroom', 100),
    ('ilocos_sur', 'region_1', 'Ilocos Sur', 'Province chatroom', 100),
    ('la_union', 'region_1', 'La Union', 'Province chatroom', 100),
    ('pangasinan', 'region_1', 'Pangasinan', 'Province chatroom', 100),

    -- Cordillera Administrative Region
    ('abra', 'car', 'Abra', 'Province chatroom', 100),
    ('apayao', 'car', 'Apayao', 'Province chatroom', 100),
    ('benguet', 'car', 'Benguet', 'Province chatroom', 100),
    ('ifugao', 'car', 'Ifugao', 'Province chatroom', 100),
    ('kalinga', 'car', 'Kalinga', 'Province chatroom', 100),
    ('mountain_province', 'car', 'Mountain Province', 'Province chatroom', 100),

    -- Region II - Cagayan Valley
    ('batanes', 'region_2', 'Batanes', 'Province chatroom', 100),
    ('cagayan', 'region_2', 'Cagayan', 'Province chatroom', 100),
    ('isabela', 'region_2', 'Isabela', 'Province chatroom', 100),
    ('nueva_vizcaya', 'region_2', 'Nueva Vizcaya', 'Province chatroom', 100),
    ('quirino', 'region_2', 'Quirino', 'Province chatroom', 100),

    -- Region III - Central Luzon
    ('aurora', 'region_3', 'Aurora', 'Province chatroom', 100),
    ('bataan', 'region_3', 'Bataan', 'Province chatroom', 100),
    ('bulacan', 'region_3', 'Bulacan', 'Province chatroom', 100),
    ('nueva_ecija', 'region_3', 'Nueva Ecija', 'Province chatroom', 100),
    ('pampanga', 'region_3', 'Pampanga', 'Province chatroom', 100),
    ('tarlac', 'region_3', 'Tarlac', 'Province chatroom', 100),
    ('zambales', 'region_3', 'Zambales', 'Province chatroom', 100),

    -- Region IV-A - CALABARZON
    ('batangas', 'region_4a', 'Batangas', 'Province chatroom', 100),
    ('cavite', 'region_4a', 'Cavite', 'Province chatroom', 100),
    ('laguna', 'region_4a', 'Laguna', 'Province chatroom', 100),
    ('quezon', 'region_4a', 'Quezon', 'Province chatroom', 100),
    ('rizal', 'region_4a', 'Rizal', 'Province chatroom', 100),

    -- MIMAROPA
    ('marinduque', 'mimaropa', 'Marinduque', 'Province chatroom', 100),
    ('occidental_mindoro', 'mimaropa', 'Occidental Mindoro', 'Province chatroom', 100),
    ('oriental_mindoro', 'mimaropa', 'Oriental Mindoro', 'Province chatroom', 100),
    ('palawan', 'mimaropa', 'Palawan', 'Province chatroom', 100),
    ('romblon', 'mimaropa', 'Romblon', 'Province chatroom', 100),

    -- Region V - Bicol Region
    ('albay', 'region_5', 'Albay', 'Province chatroom', 100),
    ('camarines_norte', 'region_5', 'Camarines Norte', 'Province chatroom', 100),
    ('camarines_sur', 'region_5', 'Camarines Sur', 'Province chatroom', 100),
    ('catanduanes', 'region_5', 'Catanduanes', 'Province chatroom', 100),
    ('masbate', 'region_5', 'Masbate', 'Province chatroom', 100),
    ('sorsogon', 'region_5', 'Sorsogon', 'Province chatroom', 100),

    -- Region VI - Western Visayas
    ('aklan', 'region_6', 'Aklan', 'Province chatroom', 100),
    ('antique', 'region_6', 'Antique', 'Province chatroom', 100),
    ('capiz', 'region_6', 'Capiz', 'Province chatroom', 100),
    ('guimaras', 'region_6', 'Guimaras', 'Province chatroom', 100),
    ('iloilo', 'region_6', 'Iloilo', 'Province chatroom', 100),

    -- Negros Island Region
    ('negros_occidental', 'nir', 'Negros Occidental', 'Province chatroom', 100),
    ('negros_oriental', 'nir', 'Negros Oriental', 'Province chatroom', 100),
    ('siquijor', 'nir', 'Siquijor', 'Province chatroom', 100),

    -- Region VII - Central Visayas
    ('bohol', 'region_7', 'Bohol', 'Province chatroom', 100),
    ('cebu', 'region_7', 'Cebu', 'Province chatroom', 100),

    -- Region VIII - Eastern Visayas
    ('biliran', 'region_8', 'Biliran', 'Province chatroom', 100),
    ('eastern_samar', 'region_8', 'Eastern Samar', 'Province chatroom', 100),
    ('leyte', 'region_8', 'Leyte', 'Province chatroom', 100),
    ('northern_samar', 'region_8', 'Northern Samar', 'Province chatroom', 100),
    ('samar', 'region_8', 'Samar', 'Province chatroom', 100),
    ('southern_leyte', 'region_8', 'Southern Leyte', 'Province chatroom', 100),

    -- Region IX - Zamboanga Peninsula
    ('zamboanga_del_norte', 'region_9', 'Zamboanga del Norte', 'Province chatroom', 100),
    ('zamboanga_del_sur', 'region_9', 'Zamboanga del Sur', 'Province chatroom', 100),
    ('zamboanga_sibugay', 'region_9', 'Zamboanga Sibugay', 'Province chatroom', 100),

    -- Region X - Northern Mindanao
    ('bukidnon', 'region_10', 'Bukidnon', 'Province chatroom', 100),
    ('camiguin', 'region_10', 'Camiguin', 'Province chatroom', 100),
    ('lanao_del_norte', 'region_10', 'Lanao del Norte', 'Province chatroom', 100),
    ('misamis_occidental', 'region_10', 'Misamis Occidental', 'Province chatroom', 100),
    ('misamis_oriental', 'region_10', 'Misamis Oriental', 'Province chatroom', 100),

    -- Region XI - Davao Region
    ('davao_de_oro', 'region_11', 'Davao de Oro', 'Province chatroom', 100),
    ('davao_del_norte', 'region_11', 'Davao del Norte', 'Province chatroom', 100),
    ('davao_del_sur', 'region_11', 'Davao del Sur', 'Province chatroom', 100),
    ('davao_occidental', 'region_11', 'Davao Occidental', 'Province chatroom', 100),
    ('davao_oriental', 'region_11', 'Davao Oriental', 'Province chatroom', 100),

    -- Region XII - SOCCSKSARGEN
    ('cotabato', 'region_12', 'Cotabato', 'Province chatroom', 100),
    ('sarangani', 'region_12', 'Sarangani', 'Province chatroom', 100),
    ('south_cotabato', 'region_12', 'South Cotabato', 'Province chatroom', 100),
    ('sultan_kudarat', 'region_12', 'Sultan Kudarat', 'Province chatroom', 100),

    -- Caraga
    ('agusan_del_norte', 'caraga', 'Agusan del Norte', 'Province chatroom', 100),
    ('agusan_del_sur', 'caraga', 'Agusan del Sur', 'Province chatroom', 100),
    ('dinagat_islands', 'caraga', 'Dinagat Islands', 'Province chatroom', 100),
    ('surigao_del_norte', 'caraga', 'Surigao del Norte', 'Province chatroom', 100),
    ('surigao_del_sur', 'caraga', 'Surigao del Sur', 'Province chatroom', 100),

    -- BARMM
    ('basilan', 'barmm', 'Basilan', 'Province chatroom', 100),
    ('lanao_del_sur', 'barmm', 'Lanao del Sur', 'Province chatroom', 100),
    ('maguindanao_del_norte', 'barmm', 'Maguindanao del Norte', 'Province chatroom', 100),
    ('maguindanao_del_sur', 'barmm', 'Maguindanao del Sur', 'Province chatroom', 100),
    ('sulu', 'barmm', 'Sulu', 'Province chatroom', 100),
    ('tawi_tawi', 'barmm', 'Tawi-Tawi', 'Province chatroom', 100),

    -- NCR has no provinces, so use Metro Manila as its special room.
    ('metro_manila', 'ncr', 'Metro Manila', 'National Capital Region', 200)
ON CONFLICT (id) DO UPDATE SET
    category_id = EXCLUDED.category_id,
    name = EXCLUDED.name,
    topic = EXCLUDED.topic,
    capacity = EXCLUDED.capacity;

-- Optional first system message for the new room structure.
INSERT INTO public.room_messages (room_name, sender, body, is_system) VALUES
    ('Metro Manila', 'System', 'Welcome to Uzzap! Choose your Philippine region and province chatroom.', true);
