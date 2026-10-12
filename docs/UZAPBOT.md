# UzzapBot Production Architecture

Scope: production UzzapBot integration as of September 2026. This document describes the runtime boundary between Android, Supabase, the UzzapBot Edge Function, and the configured AI service. It contains no API keys or private credentials.

## Runtime flow

Android Uzzap client -> Supabase room_messages -> UzzapBot event receipt/dispatch -> Supabase Edge Function uzzapbot -> conversational AI through the configured Cloudflare Worker -> Gemma 4 -> bot response -> room_messages -> Android Realtime.

Deterministic game commands remain in backend game logic and are not routed through AI.

## Responsibilities

### Android
The Android client displays bot messages and sends room messages. It must not contain privileged backend credentials. Relevant sources include ui/ChatRoomFragment.kt, data/UnifiedRoomClient.kt, data/SupabaseRealtimeClient.kt, and ui/RoomMessageAdapter.kt.

### Supabase
Supabase is the authoritative application backend and database. UzzapBot-related state includes room_messages, uzzapbot_event_receipts, uzzapbot_processed_messages, uzzapbot_cursor, uzzapbot_memory, uzzapbot_ai_rate_state, uzzapbot_room_activity, uzzapbot_room_settings, uzzapbot_questions and uzzapbot_question_usage.

### UzzapBot Edge Function
The uzzapbot Edge Function handles event dispatch, duplicate protection, participation decisions, deterministic game commands, state loading, conversational AI calls, language/style cleanup, bot-message insertion, and processing/error records.
### Conversational AI

The production conversational path uses server-side AI configuration. Historically this was a Cloudflare Worker AI endpoint using the configured Gemma 4 model, with a backup endpoint on primary failure.

Live production also contains the `tencent-uzzap-ai-chatbot` Edge Function (per-user JWT, memory recall/write) and the deployed privacy policy names Groq as the AI provider for UzzapBot responses. The exact live-primary routing across these providers should be confirmed against deployment configuration before changing AI-related code or docs.

The Worker provides /health and /ai endpoints, validates request/message sizes, runs the configured model, disables thinking output, and returns a normalized response.

## AI routing

AI is intended for /ai, supported /bot conversational requests, and ordinary messages selected by UzzapBot participation logic.

Deterministic commands such as /TRIVIA ON, /GTA FOREIGN, /GTA OPM, /EWORDHUNT and /TWORDHUNT must remain outside the AI path.

## Reliability

The event-receipt model supports duplicate detection, processing status, attempts, retries, errors, cursors and performance measurement. A processed event does not necessarily mean a visible bot message was produced; participation logic can intentionally choose not to respond.

## Memory

Persistent conversational memory is stored in uzzapbot_memory. Records support user/room scope, key/value data, memory type, source, confidence and timestamps. User-specific memory belongs in backend state, not the Android APK or public source.

## Security

Never commit Supabase service-role keys, AI provider secrets, private API tokens, production credentials, or secret-bearing environment files. Privileged AI calls remain server-side.

## Operational checks

1. Verify the uzzapbot Edge Function deployment is active.
2. Verify the AI Worker health endpoint.
3. Send a controlled test message in a test/moderator room.
4. Confirm the room_messages row.
5. Confirm the event receipt and processing state.
6. Confirm only the intended bot response is produced.
7. Confirm the response returns through Realtime.
8. Review error and performance metrics after repeated tests.

## Related documentation

- docs/ARCHITECTURE.md — Android architecture
- docs/DATABASE.md — live database and UzzapBot schema
- docs/BACKEND.md — backend architecture
- docs/PRIVACY_POLICY.md — data and AI disclosure
- docs/MODERATOR_ROOM.md — moderator authorization
