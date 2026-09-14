# Uzzap (Kotlin/Android recreation)

A from-scratch **Android/Kotlin recreation of the classic Uzzap mobile chat client**, preserving the retro navigation, menus, icons, emoticons and chat-room flow.

The backend uses **Supabase Auth + PostgreSQL/RLS**. The Android app is allowed to contain only the Supabase **anon** key. A service-role key is intentionally not supported by the build.

## Security model

- Email/password authentication is handled by Supabase Auth.
- Profiles are owned by `auth.users.id`.
- Room messages are writable only by authenticated users; the database trigger controls the sender identity.
- Presence and buddy rows are writable only by their owner.
- Direct messages are readable only by sender/recipient.
- Categories, rooms and room history are publicly readable for the classic room-browsing experience.
- The SQL removes the prototype's unrestricted public write policies.

## Supabase setup

1. Open your Supabase project.
2. Run `supabase_schema.sql` in the SQL Editor.
3. Enable Email authentication under Authentication → Providers → Email.
4. Configure the Android build with `SUPABASE_URL` and `SUPABASE_ANON_KEY`.
5. Never put `SUPABASE_SERVICE_ROLE_KEY` in this repository, Gradle, Docker, GitHub Actions, or an APK.

The anon key is a client key, not a privilege boundary. RLS and authenticated ownership provide the security boundary.

## Local build

The repository contains the Gradle wrapper. From the project root:

```bash
chmod +x gradlew
export SUPABASE_URL="https://YOUR_PROJECT_REF.supabase.co"
export SUPABASE_ANON_KEY="YOUR_SUPABASE_ANON_KEY"
./gradlew assembleDebug
```

The wrapper targets Gradle 9.3.1 and the project uses Android Gradle Plugin 9.1.1. Use the checked-in wrapper rather than a random system Gradle installation.

## GitHub Actions

The workflow builds a debug APK on pushes to `main` and on manual dispatch, then uploads the APK as an artifact. Repository permissions are read-only and the workflow no longer force-updates tags/releases.

Required Actions secrets:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`

## Docker

The Docker build accepts only `SUPABASE_URL` and `SUPABASE_ANON_KEY`. It does not accept or inject a service-role key.

## UI

The existing Uzzap recreation UI is intentionally retained: splash/menu screens, connecting screen, app grid, buddy list, chat-room categories, chat rooms, emoticon picker, dialogs and the original visual assets.

## Before public release

Add automated tests, realtime subscriptions/message pagination, token-refresh hardening, server-side rate limiting and moderation, block/report controls, abuse prevention, and a properly managed production signing key. The secure SQL is a foundation; it is not a complete moderation system.
