# Moderator Room

Uzzap has a server-authorized **Moderator Room** for moderators and admins.

## Roles
- `member`: normal user
- `moderator`: can enter the Moderator Room and use moderation tools
- `admin`: owner/admin; can enter the Moderator Room and assign/remove moderators

## Owner
The Supabase bootstrap recognizes `mharbalaba@gmail.com` as the Uzzap owner and assigns `admin` when that Auth account exists.

## Assign a moderator
Admins can call the Supabase RPC `set_moderator_by_email(email, true)` for a registered Auth user. To remove moderator status, call it with `false`.

The Android client must never rely only on hiding UI: room access and role-management authorization are enforced by Supabase policies/functions.
