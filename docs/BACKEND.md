# Uzzap Backend Architecture

Scope: current production backend model documented in September 2026. This is an architectural guide, not a replacement for executable migrations or a database dump.

## Backend layers

Android -> Supabase Auth; Android -> PostgreSQL/RLS; Android -> Supabase Realtime; backend events -> push sender -> Firebase Cloud Messaging -> Android notification handling.

## Authentication

Supabase Auth owns authentication sessions and the auth.users identity boundary. Public-schema user records reference Auth identities through UUID relationships where applicable. The Android client must never receive or store a service-role key.

## PostgreSQL and RLS

PostgreSQL is the authoritative source for application state. Row Level Security is part of the security boundary; client-side UI hiding is not authorization.

The verified live public schema contains 49 base tables, 1 view, 81 public functions, 54 RLS policies, 18 non-internal triggers, and 122 indexes. See docs/DATABASE.md for the detailed inventory.

## Realtime

Supabase Realtime delivers room and private-message changes to connected clients. The Android app owns presentation and reconnection behavior; backend authorization remains authoritative.

## Edge Functions

Edge Functions provide trusted server-side operations that should not expose privileged credentials to Android. Sources are vendored under `supabase/functions/` from production for repo completeness.

| Function | Purpose | Auth |
|---|---|---|
| `uzzapbot` (v183) | AI/game orchestration, dispatch, memory | Internal token (`x-uzzapbot-internal-token`) |
| `tencent-uzzap-ai-chatbot` | Per-user AI chat with memory recall/write | User JWT |
| `send-push` | FCM delivery workflows | Webhook secret (see `supabase/config.toml`) |
| `uzzap-privacy` | Public privacy page + deletion-request form | Public (form-encoded POST) |
| `uzzap-delete-account` | Authenticated self-deletion via `delete_my_account` | User JWT |

The uzzapbot Edge Function provides AI/game orchestration. The push sender Edge Function handles notification delivery workflows.

## Notifications

Database/event -> push sender -> Firebase Cloud Messaging -> UzzapFirebaseMessagingService -> UzzapNotificationManager -> relevant Uzzap screen.

## Data domains

Accounts: profiles and account_deletion_requests.
Rooms: chat_categories, chat_rooms, room_participants and room_messages.
Social: buddies, buddy_requests and blocked_users.
Private chat: direct_messages and receipt state.
Invitations: room_invitations.
Moderation: user_reports, moderation_actions, room_sanctions and moderator_roles.
Notifications: user_fcm_tokens.
UzzapBot: uzzapbot operational, AI, memory and activity tables.
Games: game_sessions, game_players and UzzapBot progression tables.
Question data: uzzapbot_questions and uzzapbot_question_usage.
WordHunt: uzzapbot_word_datasets and uzzapbot_word_dataset_entries.

## Security boundary

Untrusted client -> authenticated request -> PostgreSQL RLS / authorized RPC or server function -> authoritative state.

Service-role credentials belong only in trusted server environments. Never place them in Android source, APK resources, committed Gradle properties, Docker images, GitHub Actions logs/source, or public documentation.

## Production documentation rule

supabase/migrations/ is the executable migration history. supabase_schema.sql is a schema snapshot/base schema. docs/DATABASE.md is the human-readable live architecture. When they disagree, verify the live database and migration history before treating any document as authoritative.

## Related documentation

- docs/ARCHITECTURE.md
- docs/DATABASE.md
- docs/UZAPBOT.md
- docs/PRIVACY_POLICY.md
- docs/MODERATOR_ROOM.md
