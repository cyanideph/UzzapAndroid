# Phase 3 — Notifications

## Implemented
- FCM data-only delivery so Android builds the notification consistently in foreground and background.
- Private-message notifications with tap-to-open the correct private chat.
- Duplicate private-message suppression using message/event IDs.
- Separate Android notification channels for messages and Uzzap activity.
- Buddy-request notifications.
- Room-invitation notifications.
- Automatic removal of permanently invalid FCM registrations remains enabled.
- Room invitation database push trigger added.
- Android CI runs on the Phase 3 branch.

## Verification
- Supabase `send-push` deployed as version 3.
- Room invitation push trigger applied to the live database.
- Android debug build and unit tests are required before merging to `main`.
