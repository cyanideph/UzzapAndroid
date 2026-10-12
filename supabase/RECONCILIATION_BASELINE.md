# Supabase Production Reconciliation Baseline

**Project:** UZZAPANDROID_BACKEND  
**Project ref:** `hrjgblvxhtbopuyzyasu`  
**Verified:** 2026-09-27

## Authority

The production Supabase project is the current-state authority until the historical migration gap is reconstructed. The repository's live snapshots capture the production `public` schema and programmable objects without copying production rows or secrets.

## Verified production state

- Live migration records: **137**
- Latest live migration: `20260927050940` — `move_uzzapbot_ai_lab_to_public_isolated`
- Live migrations containing `uzzapbot` in the name: **63**
- Legacy `uzzapbot-ai-room-test` trigger: **removed**
- Current UzzapBot dispatch trigger: `trg_uzzapbot_dispatch`

## Repository artifacts

| Artifact | Role |
|---|---|
| `supabase/live_schema_snapshot.sql` | Current production public schema snapshot |
| `supabase/live_functions_triggers.sql` | Current production public functions/triggers snapshot |
| `supabase/LIVE_MIGRATION_INVENTORY.md` | Exact live migration version/name inventory |
| `docs/SCHEMA_RECONCILIATION.md` | Reconciliation notes and known history gap |
| `supabase/migrations/20260918_baseline_live_schema.sql` | Historical 2026-09-18 baseline; **not current authority** |

## Important distinction

The 137 live migration records cannot be reconstructed as 137 exact SQL files from migration names alone. The repository currently contains fewer historical migration files. We must not invent missing historical SQL or pretend the old baseline reproduces every historical change.

The safe reproducibility target is a **current-state baseline**: a fresh environment should be able to recreate the verified production schema and programmable objects from version-controlled artifacts, after which future changes are recorded as normal migrations.

## Cleanup rule

Do not delete the historical baseline or live snapshots until a tested current-state baseline has been validated against a clean database. Do not remove production objects merely to make the migration count look equal.

## Next migration policy

1. Treat the reconciled current-state baseline as the starting point for future environments.
2. Record every new production schema change in `supabase/migrations/`.
3. Never edit an already-applied migration to hide drift.
4. Keep production data, auth data, FCM tokens, Vault secrets, and API keys out of Git.
5. Before deleting a schema artifact, verify that no deployment, CI, documentation, or restore workflow depends on it.
