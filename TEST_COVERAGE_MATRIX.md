# UzzapAndroid Test Coverage Matrix

This document is the release test contract for the Android application. A passing JVM unit-test task is **not** considered whole-app validation.

## Test layers

| Layer | Purpose | Release gate |
|---|---|---|
| JVM unit | Deterministic models, state, parsing, client state machines | Required |
| Android instrumentation/UI | Activity, lifecycle, rendering, navigation, Android APIs | Required |
| Supabase integration | Auth, REST/RPC, RLS, database contracts | Required |
| Realtime integration | WebSocket filters, reconnect, session refresh, duplicate protection | Required |
| Two-user E2E | Real user-to-user room/DM behavior | Required |
| Failure/recovery | Offline, timeout, malformed payload, stale callback, retry | Required |
| Security | RLS, authorization boundaries, token/secret handling | Required |
| Performance | Startup, polling, rendering, memory/lifecycle | Required for release |

## Feature coverage

| Feature | JVM | UI | Integration | E2E | Status |
|---|---:|---:|---:|---:|---|
| App startup / splash | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Login / session restore | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Signup / profile provisioning | ✅ | ⬜ | ⬜ | ⬜ | Partial |
| Password recovery | ✅ | ⬜ | ⬜ | ⬜ | Partial |
| Profile / presence | ✅ | ⬜ | ⬜ | ⬜ | Partial |
| Region/province room discovery | ✅ | ⬜ | ⬜ | ⬜ | Partial |
| Room join / leave | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Room hide | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Room messaging | ✅ | ⬜ | ⬜ | ⬜ | Partial |
| Room message deduplication | ✅ | ⬜ | ⬜ | ⬜ | Partial |
| Realtime room filtering | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Realtime reconnect | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Session refresh / Realtime rejoin | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Buddy list / groups | ✅ | ⬜ | ⬜ | ⬜ | Partial |
| Buddy requests | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Batch buddy presence | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Private chat | ✅ | ⬜ | ⬜ | ⬜ | Partial |
| DM send/receive | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Delivery/read receipts | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Unread cache/coalescing | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Stale unread race protection | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Notification suppression | ✅ | ⬜ | ⬜ | ⬜ | Partial |
| FCM registration/receive | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Block/report | ✅ | ⬜ | ⬜ | ⬜ | Partial |
| Moderator console | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Room invitations | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Diagnostics | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Error handling / retries | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |
| Offline/online recovery | ✅ | ⬜ | ⬜ | ⬜ | Partial |
| RLS/security boundaries | ⚠️ | — | ⬜ | ⬜ | Gap |
| Performance/lifecycle | ⚠️ | ⬜ | ⬜ | ⬜ | Gap |

Legend: ✅ meaningful automated coverage exists; ⚠️ partial/code-level coverage; ⬜ missing release-grade coverage.

## Mandatory E2E scenarios

1. Login → session restore → app home.
2. Open region → province → room.
3. Join room → send message → receive message from second account.
4. Hide room → remain subscribed while hidden.
5. Leave room → stop receiving room events.
6. Reconnect WebSocket → resubscribe with current room filters.
7. Refresh auth token → Realtime reconnects using the new token.
8. Send DM A → B receives it through Realtime.
9. B reads DM → A/B read state is correct.
10. Unread burst → one logical unread state, no duplicate notifications.
11. Buddy request → accept → buddy appears with presence.
12. Block/report → blocked sender cannot produce an accepted DM.
13. FCM notification → notification opens the correct destination.
14. Moderator action → unauthorized user is rejected by server/RLS.
15. Offline → cached state remains visible → reconnect reconciles state.
16. Malformed/failed backend response → user-visible error and no crash.
17. Destroy/recreate room/private screens → no duplicate listeners/events.
18. Rapid navigation/background/foreground → no leaked polling or callbacks.

## Release gate

The app is not marked **fully tested** until the required JVM, instrumentation, integration, Realtime, two-user E2E, failure/recovery, and security scenarios have recorded PASS results. Unit-test success alone never satisfies this gate.
