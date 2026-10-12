# Phase 4 — Moderation

Phase 4 hardens the existing moderation system on the server and keeps the Android moderator console backed by authoritative Supabase RPCs.

## Included

- Authenticated-only report creation and report visibility.
- Centralized `can_moderate_target(uuid)` authorization check.
- Moderators cannot moderate administrators.
- Moderators cannot sanction their own account.
- Duration validation for mute/kick/ban actions.
- Moderation actions remain recorded in `moderation_actions`.
- Direct client writes to `room_sanctions` are removed; sanctions are materialized by the protected moderation trigger/RPC path.
- Revoke operations are server-authorized and target-role aware.
- Room-message deletion verifies the target user's role before deleting and records the action.
- Moderation RPCs are restricted to authenticated callers.

## Existing Android moderation features

The current Android code already contains a moderator console, report queue, active sanctions, sanction actions, room-message deletion, mute/kick controls, moderator-only room access, and admin-controlled moderator assignment.

## Production verification

The Phase 4 SQL was applied to the connected Uzzap Supabase project and verified by querying the resulting function security types and moderation schema. No client-side secret key is required.
