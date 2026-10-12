# Uzzap Java -> Kotlin Feature Matrix

This matrix compares the legacy Java client in `jarzzap` with the current Kotlin/Supabase Android client. The legacy source is heavily obfuscated, so items are marked **Verified** only where the Java behavior can be traced directly to the inspected classes (`Kalypte.java`, `ab.java`, `bl.java`) or to an equivalent Kotlin screen already present in the new client.

## 1. Screen-by-screen

| Legacy Java area | Kotlin screen | Status | Notes |
|---|---|---|---|
| Startup / login | `SplashMenuActivity` | Verified | Login, register, forgot password, saved session, backend info, help, about, exit. |
| Connection / session | `ConnectingActivity` | Verified | Supabase session restoration and connection flow. |
| Main navigation | `AppGridActivity` / `ChatHostActivity` | Verified | Modern Android navigation replacing the old single-screen J2ME-style host. |
| Buddy list | `BuddyListFragment` | Verified + enhanced | Groups, add buddy, rename group, status, PM launch, block/report, unread badges, live presence. |
| Room categories | `ChatRoomsFragment` | Verified + enhanced | Categories/rooms are backend-driven. Intended hierarchy is PH regions -> provinces. |
| Public room | `ChatRoomFragment` | Verified + enhanced | Polling, send, emoticons, whisper, members, room info, invite, report, leave. |
| Private chat | `PrivateChatActivity` | Verified + enhanced | Conversation polling, send, unread/read state, options, emoticons, profile, block/report. |
| Settings | `LegacySettingsActivity` | Verified | Retained as the compatibility settings surface. |
| Profile | `UzzapParityActivity` profile mode / status dialogs | Verified | Status message, presence and Supabase profile synchronization. |
| Help / About | `SimpleTextActivity` | Verified | Help assets and about flow retained. |
| Themes | `UzzapParityActivity` theme mode | Verified | Classic/dark/black/blue/green choices. |
| Notifications | `UzzapNotificationManager` | Verified + enhanced | Android notification channel, unread PM aggregation and deep-link to private chat. |

## 2. Menu-by-menu / button-by-button parity

### Buddy list

Legacy Java `ab.java` exposes a large `buddyoptions` menu. Verified legacy actions include authorization, delete/remove, open IM, SMS, profile/contact actions, invitation actions, group membership changes, and contact/frequency management.

Kotlin currently exposes:

- Chat with buddy -> `PrivateChatActivity`
- View Profile -> live Supabase profile/presence
- Block User -> `SafetyClient.block`
- Report User -> `SafetyClient.report`
- Delete Buddy -> local removal UI (backend delete parity is a remaining follow-up)
- Add Buddy
- New Group
- Rename Group
- Return Groups to Defaults
- Sync Buddies
- Clear local message history
- Help
- Status Message
- Available
- Not Available
- Invisible
- Settings

**Recovered:** the important Uzzap-native buddy actions are now represented in Kotlin. Yahoo/MSN/SMS transport actions from the original Java client are intentionally not recreated as external transports because the new backend is Supabase-native; the equivalent in-app communication path is private messaging.

### Public room menu

The Kotlin room menu now covers the legacy Uzzap-native room actions:

- Send Message
- Send Whisper / Private Message
- Room Members
- Room Info
- Invite Buddy
- Report
- Leave Room
- Join/select/refresh room and category actions through the parity screen

### Private message menu

Legacy Java `bl.java` explicitly contains:

- Send Message
- Edit Message
- Set/Add Recipient
- Add Cc Recipient
- Show/Hide Recipients / CC
- Add Emoticon

Kotlin now restores the relevant Uzzap-native PM actions:

- Send Message
- Edit Message (draft editing)
- Add Emoticon
- View Profile
- Block User
- Report User

Recipient/CC controls remain unnecessary for the one-to-one Supabase PM model.

## 3. Chat behavior

| Behavior | Java | Kotlin |
|---|---|---|
| Send public room message | Yes | Yes |
| Message polling / live refresh | Yes | Yes, 2.5 s polling |
| Room presence | Yes | Yes, Supabase participants |
| System join messages | Yes | Yes |
| Emoticons | Yes | Yes |
| Whisper / private chat | Yes | Yes |
| Room members | Yes | Yes |
| Invite buddy | Yes | Yes, Supabase room invitation |
| Leave room | Yes | Yes |
| Report | Legacy menu | Kotlin safety flow + RLS-backed PM enforcement |

## 4. Buddy behavior

| Behavior | Java | Kotlin |
|---|---|---|
| Buddy groups | Yes | Yes |
| Add buddy | Yes | Yes |
| Delete/remove buddy | Yes | UI removal; backend delete should be treated as the next persistence enhancement |
| Rename group | Yes | Yes |
| Frequent/favorites grouping | Yes | Partially represented by current groups |
| Online/offline | Yes | Yes, authenticated Supabase heartbeat + 35 s freshness window |
| Status message | Yes | Yes |
| Profile | Yes | Yes |
| Block | Legacy-style contact control | Yes, enforced at RLS for new PMs |
| Report | Legacy reporting path | Yes |

## 5. PM behavior

- Two-way conversation history: **Yes**
- Incremental polling: **Yes**
- Optimistic local send: **Yes**
- Read state: **Yes**
- Per-sender unread counters: **Yes**
- Total unread counter: **Yes**
- Notification: **Yes**
- Notification deep-link to sender conversation: **Yes**
- Block enforcement: **Yes, database policy prevents new blocked conversations**

## 6. Notifications

- Android notification channel: **Yes**
- POST_NOTIFICATIONS runtime permission on Android 13+: **Yes**
- Unread PM polling: **Yes**
- Per-sender aggregation: **Yes**
- Tap notification -> exact PM buddy: **Yes**
- Preserve notification if other unread conversations remain: **Yes**

## 7. Settings

Current compatibility surface includes:

- General/settings entry
- Notification/tones entry
- Theme selection
- Profile/status entry
- Help/about
- Session/sign-out controls

## 8. Profile

- Nickname/username: Yes
- Email: Yes
- Status: Yes
- Status message: Yes
- Avatar field: Yes
- Online/offline state: Yes
- Last seen: Yes
- Supabase persistence: Yes

## 9. Room behavior

The Kotlin room hierarchy is authoritative from Supabase instead of fake/local room definitions. The target structure is **Philippine regions as categories and provinces as rooms**. The database currently contains 18 categories and 83 rooms; these should be verified against the final canonical Philippine region/province seed before release.

## 10. Remaining parity gaps

1. Buddy deletion is implemented client-side via owner-scoped backend delete (`SupabaseClient.deleteBuddy`); keep UI confirmation.
2. Legacy external Yahoo/MSN/SMS/email transports are not part of the Supabase-native architecture.
3. Legacy authorization/contact metadata is not represented as a first-class table yet.
4. Push delivery runs through the `send-push` Edge Function + FCM (token registered via `upsert_fcm_token` RPC); REST polling remains as the realtime fallback channel.
5. Enable Supabase leaked-password protection in Auth settings before production.
