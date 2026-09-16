# Uzzap (Kotlin/Android recreation)

**Uzzap v1.0-beta.6 (Build 6)** — a from-scratch Android/Kotlin modernization of the classic Uzzap mobile chat experience, **created by CY**.

The project preserves the classic Uzzap navigation, menus, icons, emoticons and chat-room flow while adding a modern Supabase backend, realtime messaging, private chat, push notifications, receipts, community controls and server-side moderation.

> This repository documents the Android modernization project. Original and third-party materials are identified separately in `NOTICE.md` and the repository licensing files.

## Current feature set

### Account and profile
- Login and signup
- Logout and password recovery
- Persistent Supabase sessions
- Token refresh and expired-session recovery
- Profile creation/editing and avatar support
- Online/offline presence

### Philippine community rooms
- Philippine regions used as room categories
- Provinces used as chat rooms
- Room browsing, joining and leaving
- Realtime room messages
- Message history pagination
- Retry/reconnect recovery
- Duplicate-message protection
- Room message deletion
- `uzzapbot` system/welcome messages

### Buddies and private chat
- Buddy requests
- Add/remove buddies
- Buddy list
- Private one-to-one chat
- Delivery receipts
- Read receipts
- Unread synchronization
- Room invitations
- Block and report controls

### Notifications
- Firebase Cloud Messaging data-only delivery
- Private-message notifications
- Buddy-request notifications
- Room-invitation notifications
- Tap-to-chat navigation
- Duplicate notification suppression
- Invalid/unregistered FCM token cleanup

### Moderation
- Moderator report queue
- Moderator assignment/removal
- Moderator-only room
- Warn, mute, kick and ban
- Sanction expiry and revoke
- Moderator audit history
- Role hierarchy safeguards
- Self-sanction and moderator-to-admin protections
- Role-aware message deletion
- Server-side sanction materialization
- Message rate limiting

## Security model

- Email/password authentication is handled by Supabase Auth.
- Profiles are owned by `auth.users.id`.
- Room messages are writable only by authenticated users; database-side controls enforce sender identity.
- Presence and buddy rows are writable only by their owner.
- Direct messages are readable only by sender/recipient.
- Categories, rooms and room history are publicly readable for the classic room-browsing experience.
- Moderation actions are protected by authenticated authorization and role hierarchy checks.
- RLS policies use authenticated ownership checks and optimized `select auth.uid()` patterns.
- Trigger-only push functions are not exposed as public APIs.
- The Android client must contain only the Supabase publishable/anon key.
- **Never put a Supabase service-role/secret key in this repository, Gradle, Docker, GitHub Actions, or an APK.**

The publishable/anon key is not a privilege boundary; PostgreSQL RLS and authenticated ownership are the security boundary.

## Supabase setup

`supabase_schema.sql` is the base schema. The app also depends on the migrations in `supabase/migrations/` for push tokens, invitations, presence/unread state, realtime direct messages, authentication/RLS hardening, rate limiting and moderation.

Apply every migration in filename order. If the repository is linked to the project, `supabase db push` can be used; otherwise apply the SQL files through the Supabase SQL Editor.

Current migration sequence includes:

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
12. Phase 2 security/RLS cleanup
13. Phase 3 room-invitation push trigger
14. Phase 4 moderation hardening

Enable Email authentication in Supabase Auth and configure the Android build with `SUPABASE_URL` and `SUPABASE_ANON_KEY` (or the publishable-key equivalent).

## Build

The repository contains the Gradle wrapper. From the project root:

```bash
chmod +x gradlew
export SUPABASE_URL="https://YOUR_PROJECT_REF.supabase.co"
export SUPABASE_ANON_KEY="YOUR_SUPABASE_ANON_KEY"
./gradlew assembleDebug
```

The checked-in project targets **Gradle 9.3.1**, Android Gradle Plugin **9.1.1**, compile/target SDK **36**, Java **17**, and Android minimum SDK **23**.

### Current app identity

- Application ID: `com.cy.uzzap`
- Version name: `1.0-beta.6`
- Version code: `6`
- Release channel: Beta
- Backend: Supabase Auth + PostgreSQL/RLS
- Push: Firebase Cloud Messaging

## GitHub Actions

The repository includes Android CI workflows for debug and release builds. The debug workflow builds on pushes to `main` and manual dispatch and uploads the APK as an artifact.

Required Actions configuration:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY` or publishable-key equivalent
- Production signing variables only for signed release builds

Workflows must never contain or print a Supabase service-role/secret key.

## Docker

The Docker build accepts client-safe Supabase configuration only. It does not accept or inject a service-role key.

## UI

The classic Uzzap visual language is intentionally retained while the Android implementation provides modern scrolling, dialogs, realtime updates, buddy/private-chat navigation, notification handling, moderation screens and diagnostics.

The **About Uzzap** screen now reports the live application version/build and documents the current feature set and beta history.

## Version history

### v1.0-beta.6 — September 2026
- Consolidated Phase 1 core chat reliability
- Consolidated Phase 2 authentication, sync and security hardening
- Consolidated Phase 3 notification delivery and push-token cleanup
- Consolidated Phase 4 moderation hardening
- Updated About Uzzap with the current feature inventory
- Version code increased to 6

### v1.0-beta.5 — September 2026
- Expanded moderation, invitations and message receipt features
- Added moderator audit history
- Added security and rate-limit hardening
- Added Philippine region/province room structure

### v1.0-beta.4 — September 2026
- Added buddy/private chat workflows
- Added reporting and moderation UI
- Improved Supabase integration

### v1.0-beta.3 — September 2026
- Android/Kotlin Uzzap modernization
- Initial profile and chatroom flows

## Project status

The current `main` branch contains the Phase 1–4 work: core chat reliability, authentication/sync hardening, notifications and moderation hardening. The app is still a **Beta** release and should receive additional integration/UI testing and production release-signing validation before public distribution.

## Before public release

- Enable and verify Supabase Auth leaked-password protection in the project settings.
- Complete broader instrumented/UI testing on physical Android devices.
- Verify all Realtime, FCM and Edge Function production paths.
- Configure and protect the production signing key.
- Run a final security/RLS audit against the production Supabase project.
