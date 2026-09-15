Uzzap (Kotlin/Android recreation)
A from-scratch Android/Kotlin recreation of the classic Uzzap mobile chat client, preserving the retro navigation, menus, icons, emoticons and chat-room flow.
The backend uses Supabase Auth + PostgreSQL/RLS. The Android app is allowed to contain only the Supabase anon key. A service-role key is intentionally not supported by the build.
Security model
Email/password authentication is handled by Supabase Auth.
Profiles are owned by auth.users.id.
Room messages are writable only by authenticated users; the database trigger controls the sender identity.
Presence and buddy rows are writable only by their owner.
Direct messages are readable only by sender/recipient.
Categories, rooms and room history are publicly readable for the classic room-browsing experience.
The SQL removes the prototype's unrestricted public write policies.
