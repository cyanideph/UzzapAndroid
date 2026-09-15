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

`supabase_schema.sql` is the base schema only. The app also depends on tables and
functions that were added afterwards in `supabase/migrations/` (push tokens, room
invitations, moderator rooms, rate limiting, warn/ban). **Skipping the migrations
will leave those features returning 404s from the client.**

1. Open your Supabase project.
2. Run `supabase_schema.sql` in the SQL Editor.
3. Apply every file in `supabase/migrations/` **in filename order**:
   ```bash
   for f in supabase/migrations/*.sql; do
     echo "-- applying $f --"
     psql "$SUPABASE_DB_URL" -f "$f"
   done
   ```
   (Or, if you use the Supabase CLI with this repo linked: `supabase db push`.)
   If you're pasting into the SQL Editor by hand instead, run the files in this
   order — it's the order `ls supabase/migrations/` already sorts to, but is
   listed here so it survives copy/paste:
   1. `20260914010000_room_invitations.sql`
   2. `20260914020000_presence_unread_blocks_reports.sql`
   3. `20260914070000_add_presence_unread_safety_controls.sql`
   4. `20260914_direct_messages_realtime.sql`
   5. `20260914_harden_uzzap_auth_rls_profiles_presence.sql`
   6. `20260915090000_add_message_rate_limits.sql`
   7. `20260915150000_complete_uzzap_enforcement.sql`
   8. `20260915153000_moderation_warn_ban.sql`
   9. `20260915170000_security_performance_hardening.sql`
   10. `20260915180000_user_fcm_tokens.sql`
   11. `20260915_moderator_room_and_management.sql`
4. Enable Email authentication under Authentication → Providers → Email.
5. Configure the Android build with `SUPABASE_URL` and `SUPABASE_ANON_KEY`.
6. Never put `SUPABASE_SERVICE_ROLE_KEY` in this repository, Gradle, Docker, GitHub Actions, or an APK.

The anon key is a client key, not a privilege boundary. RLS and authenticated ownership provide the security boundary.

New migrations should keep going in `supabase/migrations/` with an
`YYYYMMDDHHMMSS_description.sql` name so they always sort into the correct
apply order.

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

Server-side message rate limiting, warn/ban moderation, and block/report controls
are already implemented in `supabase/migrations/` (see the Supabase setup steps
above — none of it is active until those migrations are applied). Still needed
before a public release: message pagination for realtime subscriptions,
token-refresh hardening, broader instrumented/UI test coverage (current tests are
unit-level only), and a properly managed production signing key. The secure SQL
is a solid foundation; it is not a complete moderation system on its own.
