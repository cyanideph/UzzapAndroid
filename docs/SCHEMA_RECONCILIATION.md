# Production Schema Reconciliation

> **Audit date:** September 2026
>
> This document records the reconciliation status between the checked-in `supabase_schema.sql`, repository migrations, and the verified live Supabase public schema.

## Executive result

The checked-in `supabase_schema.sql` is **not a complete standalone production-schema backup**.

The verified live database contains **49 public base tables + 1 public view**, while `supabase_schema.sql` is a much smaller base-schema snapshot. Production additions are represented by incremental migrations and/or backend changes rather than being fully reproduced in the single snapshot.

Therefore:

- Do not treat `supabase_schema.sql` alone as a production restore artifact.
- Do not delete or rewrite migrations merely to make the snapshot appear complete.
- Use the ordered migration history plus a verified live-schema export for disaster recovery.
- Treat `docs/DATABASE.md` as the current human-readable live-schema reference.

## Verified live baseline

At the September 2026 inspection:

| Object | Live public count |
|---|---:|
| Base tables | 49 |
| Views | 1 |
| Public functions | 81 |
| RLS policies | 54 |
| Non-internal triggers | 18 |
| Indexes | 122 |

The live schema also contained 3,333 UzzapBot questions and 37,479 WordHunt entries.

## Base SQL status

The checked-in `supabase_schema.sql` currently contains the foundational Uzzap schema and security definitions, including profiles, categories, rooms, room messages and related core objects.

It does not contain the complete current UzzapBot production model as a standalone snapshot. Examples of live objects that are not represented there as complete standalone definitions include:

- `uzzapbot_event_receipts`
- `uzzapbot_memory`
- `uzzapbot_questions`
- `uzzapbot_game_config`
- `uzzapbot_ai_rate_state`
- `uzzapbot_room_activity`
- `uzzapbot_room_settings`
- `uzzapbot_processed_messages`
- `uzzapbot_performance_24h`
- newer game/progression tables
- WordHunt dataset tables

The live database is the evidence for this status; this list is not intended to claim that every item is absent from every migration.

## Reconciliation model

The repository should be understood as three layers:

```text
supabase_schema.sql
        |
        | foundational/base schema
        v
supabase/migrations/
        |
        | incremental production changes
        v
LIVE SUPABASE DATABASE
        |
        | verified current state
        v
docs/DATABASE.md
        |
        | human-readable architecture
        v
operators/developers
```

## Restore strategy

For a reliable production restore, preserve all of the following:

1. `supabase_schema.sql`
2. every migration under `supabase/migrations/` in filename order
3. the live production schema definition/export
4. required Edge Function source/configuration
5. server-side secrets through the deployment platform, never Git
6. seed/reference data where required, including game and WordHunt datasets

A repository snapshot without its migrations and production data should not be considered a complete backup.

## Next maintenance step

The repository should eventually contain a generated, versioned production-schema artifact created from the live Supabase database and reviewed before commit. That artifact should include table definitions, constraints, indexes, RLS policies, functions, triggers, grants and views.

Until that artifact is generated and verified, **do not label `supabase_schema.sql` as the complete production schema**.

## Security

This reconciliation intentionally contains no:

- Supabase service-role keys
- JWT secrets
- AI provider secrets
- FCM server credentials
- access tokens
- environment files

Credentials belong in the appropriate secret-management system, not in Git or documentation.

## Related documentation

- `docs/DATABASE.md` — verified live database architecture
- `docs/BACKEND.md` — backend architecture
- `docs/UZAPBOT.md` — UzzapBot production architecture
- `supabase_schema.sql` — foundational repository schema
- `supabase/migrations/` — incremental database changes
