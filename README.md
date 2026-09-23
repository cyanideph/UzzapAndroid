# Uzzap for Android

<p align="center">
  <strong>A modern Android revival of the classic Uzzap chat experience.</strong><br>
  Philippine community rooms • Private chat • Buddies • Realtime messaging • Push notifications • Moderation
</p>

<p align="center">
  <img src="https://img.shields.io/badge/release-1.0--beta.8-informational" alt="Release">
  <img src="https://img.shields.io/badge/platform-Android-brightgreen" alt="Android">
  <img src="https://img.shields.io/badge/language-Kotlin-purple" alt="Kotlin">
  <img src="https://img.shields.io/badge/backend-Supabase-3ECF8E" alt="Supabase">
  <img src="https://img.shields.io/badge/push-Firebase-orange" alt="Firebase">
</p>

<p align="center">
  <a href="https://github.com/cyanideph/UzzapAndroid/actions">CI</a> •
  <a href="docs/ARCHITECTURE.md">Architecture</a> •
  <a href="#current-feature-set">Features</a> •
  <a href="#security-model">Security</a> •
  <a href="#build">Build</a>
</p>

---

## 🌐 Overview

**Uzzap** is a from-scratch Android/Kotlin modernization of the classic Uzzap mobile chat experience, created by **CY**.

The project preserves the recognizable Uzzap community-room experience while rebuilding it around a modern Android client and a Supabase-powered backend.

> **Status:** Beta — the current `main` branch contains the Phase 1–4 implementation plus the beta.8 security, messaging, profile, welcome-system and moderation fixes.

### At a glance

| Area | What it provides |
|---|---|
| 💬 Community | Philippine regions and province-based chat rooms |
| ⚡ Realtime | Live room messaging and private conversations |
| 👥 Social | Buddies, requests, invitations and unread state |
| 🔔 Notifications | Firebase Cloud Messaging and tap-to-chat navigation |
| 🛡️ Safety | Reports, blocks, moderation, sanctions and audit history |
| 🎨 Classic feel | Uzzap colors, emoticons, menus and room-flow concepts |
| 🔐 Backend | Supabase Auth, PostgreSQL/RLS and server-side authorization |

## 📚 Documentation

- **[Architecture](docs/ARCHITECTURE.md)** — Android components, backend boundaries, notification flow and source mapping
- **[Supabase schema](supabase_schema.sql)** — base database schema
- **[Database migrations](supabase/migrations/)** — incremental backend changes
- **[Third-party notices](NOTICE.md)** — original and third-party material notices

---

## Current feature set


### Account and profile
- Login and signup
- Logout and password recovery
- Persistent Supabase sessions
- Token refresh and expired-session recovery
- Profile creation/editing and avatar support
- Text-only editable nicknames with nickname color-markup sanitization
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
- Pure centered system-style welcome rendering

### Messaging and emoticons
- 30 Uzzap message color codes (`c01` through `c30`)
- Multiple color segments in a single message
- Color codes are for messages only; nicknames remain text-only
- Uzzap emoticon/emoji picker
- Numbered Uzzap emoticons `11`-`20` and `47`
- Legacy text emoticon rendering such as `:)`, `:D`, `:(`, `;)`, `:P` and `<3`
- Color codes and emoticons can be combined in messages
- Raw message markup remains compatible with Supabase storage

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
- Secure room message deletion
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

Enable Email authentication in Supabase Auth and configure the Android build with `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` (or the legacy anon-key equivalent).

## Build

The repository contains the Gradle wrapper. From the project root:

```bash
chmod +x gradlew
export SUPABASE_URL="https://YOUR_PROJECT_REF.supabase.co"
export SUPABASE_PUBLISHABLE_KEY="YOUR_SUPABASE_PUBLISHABLE_KEY"
./gradlew assembleDebug
```

The checked-in project targets **Gradle 9.3.1**, Android Gradle Plugin **9.1.1**, compile/target SDK **36**, Java **17**, and Android minimum SDK **23**.

### Current app identity

- Application ID: `com.cy.uzzap`
- Version name: `1.0-beta.8`
- Version code: `8`
- Release channel: Beta
- Backend: Supabase Auth + PostgreSQL/RLS
- Push: Firebase Cloud Messaging

## GitHub Actions

The repository includes Android CI workflows for debug and release builds. The debug workflow builds on pushes to `main` and manual dispatch and uploads the APK as an artifact named **`Uzzap-debug-apk`**.

Required Actions configuration:

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY` (or legacy anon-key equivalent)
- Production signing variables only for signed release builds

Workflows must never contain or print a Supabase service-role/secret key.

## Docker

The Docker build accepts client-safe Supabase configuration only. It does not accept or inject a service-role key.

## UI

The classic Uzzap visual language is intentionally retained while the Android implementation provides modern scrolling, dialogs, realtime updates, buddy/private-chat navigation, notification handling, moderation screens and diagnostics.

The **About Uzzap** screen reports the live application version/build and documents the current feature set and beta history.

## Version history

### v1.0-beta.8 — September 2026
- Security audit fixes verified against the live database: anon email enumeration closed via nickname-login RPC; leaked API key rotated, legacy JWT keys disabled
- Canonical live-schema baseline documentation updated to reflect the current production schema; see `docs/DATABASE.md` for the verified inventory and current object counts.
- Fixed room/buddy leave silently failing after nickname changes (delete by owner_id)
- Whisper is now a true private message (previously broadcast to the whole room)
- Fixed moderators being unable to delete/moderate their own messages
- Unified encrypted session storage; removed plaintext JWT; startup blocks navigation on missing/invalid Supabase key
- Async profile loading; centralized auth headers; moderator role re-check on long-press; buddy-list network throttling
- Fixed FCM token ownership conflicts on multi-account devices (upsert_fcm_token RPC)
- Fixed publish-site version/size substitution; release workflow aligned to publishable key
- Version code increased to 8

### v1.0-beta.7 — September 2026
- Added 30 Uzzap message color codes (`c01`-`c30`)
- Added multi-color message rendering while keeping nicknames text-only
- Added Uzzap emoticon/emoji picker
- Added numbered emoticons `11`-`20` and `47`
- Added legacy text emoticon rendering
- Added pure system-style `uzzapbot` welcome messages
- Hardened nickname sanitization and room sender identity
- Added secure moderator/admin room message deletion
- Fixed password recovery HTTP 405 update failure
- Updated About Uzzap What's New and version history
- Version code increased to 7

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

The current `main` branch contains the Phase 1–4 work plus the beta.8 security-audit, messaging, profile, welcome-system and moderation fixes. The app is still a **Beta** release. Every push to `main` starts the debug Android CI workflow, which runs unit tests, assembles the debug APK and publishes the `Uzzap-debug-apk` artifact for device testing.

## Before public release

- Enable and verify Supabase Auth leaked-password protection in the project settings.
- Complete broader instrumented/UI testing on physical Android devices.
- Verify all Realtime, FCM and Edge Function production paths.
- Configure and protect the production signing key.
- Run a final security/RLS audit against the production Supabase project.
