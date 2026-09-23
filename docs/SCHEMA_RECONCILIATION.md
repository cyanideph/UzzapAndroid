# Production Schema Reconciliation

> **Audit date:** September 2026
>
> This document records the reconciliation status between the checked-in `supabase_schema.sql`, repository migrations, and the verified live Supabase public schema.

## Executive result

The checked-in `supabase_schema.sql` is **not a complete standalone production-schema backup**. The repository also does not contain the complete migration history that is recorded by the live Supabase project.

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

## Migration-history reconciliation

The live Supabase project reports **131 applied migrations** through `20260923060732` (`harden_delete_my_account`). The repository currently contains **25 migration files** under `supabase/migrations/`.

This is not a one-to-one filename comparison: some repository migrations are consolidated/renamed versions of earlier live migrations. Therefore the difference of 106 files must **not** be interpreted as exactly 106 missing migrations. However, the audit confirms a material source-control gap.

Most importantly, the live project reports **57 migrations whose names contain `uzzapbot`**, including the event-receipt/dispatch, question-bank, memory, monitoring, configuration, AI-gating and game-persistence work. There are no corresponding UzzapBot migration files in the repository's current `supabase/migrations/` directory.

The repository does contain a `20260918_baseline_live_schema.sql`, but its 1,479 lines define only 15 core tables; it does not reproduce the complete live UzzapBot schema. It therefore cannot serve as the missing UzzapBot migration history.

### Result

| Layer | Verified state |
|---|---|
| Live migration history | 131 applied migrations |
| Repository migration files | 25 files |
| Live UzzapBot-named migrations | 57 |
| Repository UzzapBot migration files | 0 |
| `supabase_schema.sql` | Foundational/base schema, 483 lines |
| `20260918_baseline_live_schema.sql` | Core 15-table baseline, 1,479 lines |
| Live production schema | 49 tables + 1 view |

This means the repository is **not currently a reproducible source-of-truth for the full production database migration path**.

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


## Concrete table-level gap

The verified live public schema contains 49 base tables. The checked-in `20260918_baseline_live_schema.sql` defines 15 core tables. Therefore **34 live tables are outside that baseline file**.

The 34 additional live tables are:

- `game_players`
- `game_sessions`
- `uzzapbot_achievements`
- `uzzapbot_ai_rate_state`
- `uzzapbot_audit_log`
- `uzzapbot_battles`
- `uzzapbot_control_settings`
- `uzzapbot_cursor`
- `uzzapbot_daily_challenges`
- `uzzapbot_event_receipts`
- `uzzapbot_game_config`
- `uzzapbot_game_modes`
- `uzzapbot_game_pools`
- `uzzapbot_internal_config`
- `uzzapbot_memory`
- `uzzapbot_player_achievements`
- `uzzapbot_player_activity`
- `uzzapbot_player_stats`
- `uzzapbot_player_titles`
- `uzzapbot_powerup_uses`
- `uzzapbot_powerups`
- `uzzapbot_processed_messages`
- `uzzapbot_question_usage`
- `uzzapbot_questions`
- `uzzapbot_room_activity`
- `uzzapbot_room_challenges`
- `uzzapbot_room_settings`
- `uzzapbot_titles`
- `uzzapbot_tournaments`
- `uzzapbot_welcomes`
- `uzzapbot_word_dataset_entries`
- `uzzapbot_word_datasets`
- `uzzapbot_xp_history`
- `account_deletion_requests`
- `buddy_requests`

This is a schema-source gap, not a request to recreate tables blindly. Existing production objects should be preserved while their definitions are reconstructed from authoritative migration/source history.

## Safe reconstruction decision

No destructive migration was applied.

The correct reconstruction target is a **new, versioned production schema snapshot/migration artifact**, generated from the verified live schema and reconciled against the 131 live migration records. It should be reviewed before becoming the repository's canonical restore path.


## Live schema snapshot manifest

A fresh read-only inspection of the production public schema returned:

| Object class | Count |
|---|---:|
| Base tables | 49 |
| Views | 1 |
| Public routines | 81 |
| RLS policies | 54 |
| Non-internal triggers | 18 |
| Indexes | 122 |

The single public view is `public.uzzapbot_performance_24h`. It derives its metrics from `uzzapbot_event_receipts` and is therefore part of the UzzapBot operational schema.

### Why this is a manifest, not a dump

The available database connector can inspect and safely query the live schema, but it does not expose a direct production `pg_dump --schema-only` artifact through this workflow. Consequently, this audit does **not** fabricate DDL from partial metadata.

For a true restore artifact, generate a schema-only dump using an authorized Supabase/Postgres database connection, review it, and commit the resulting SQL separately. The generated dump should be treated as sensitive infrastructure documentation and checked for secrets before publication.

### Current decision

The live schema has now been independently re-verified after the migration-history audit. No production DDL was changed during this reconciliation.



## Production object-level inventory

The live catalog was rechecked read-only. All 49 public base tables have RLS enabled; none currently has forced RLS. The inventory below records per-table index and trigger counts so future schema audits can detect structural drift without exposing application data.

| Table | Indexes | Triggers |
|---|---:|---:|
| account_deletion_requests | 1 | 0 |
| blocked_users | 2 | 0 |
| buddies | 3 | 1 |
| buddy_requests | 6 | 1 |
| chat_categories | 1 | 0 |
| chat_rooms | 3 | 0 |
| direct_messages | 4 | 2 |
| game_players | 3 | 0 |
| game_sessions | 3 | 1 |
| moderation_actions | 6 | 2 |
| moderator_roles | 2 | 0 |
| profiles | 6 | 1 |
| room_invitations | 4 | 1 |
| room_messages | 3 | 4 |
| room_participants | 4 | 5 |
| room_sanctions | 3 | 0 |
| user_fcm_tokens | 4 | 0 |
| user_reports | 5 | 0 |
| uzzapbot_achievements | 1 | 0 |
| uzzapbot_ai_rate_state | 1 | 0 |
| uzzapbot_audit_log | 3 | 0 |
| uzzapbot_battles | 2 | 0 |
| uzzapbot_control_settings | 2 | 0 |
| uzzapbot_cursor | 1 | 0 |
| uzzapbot_daily_challenges | 3 | 0 |
| uzzapbot_event_receipts | 2 | 0 |
| uzzapbot_game_config | 1 | 0 |
| uzzapbot_game_modes | 1 | 0 |
| uzzapbot_game_pools | 2 | 0 |
| uzzapbot_internal_config | 1 | 0 |
| uzzapbot_memory | 4 | 0 |
| uzzapbot_player_achievements | 2 | 0 |
| uzzapbot_player_activity | 3 | 0 |
| uzzapbot_player_stats | 1 | 0 |
| uzzapbot_player_titles | 1 | 0 |
| uzzapbot_powerup_uses | 3 | 0 |
| uzzapbot_powerups | 1 | 0 |
| uzzapbot_processed_messages | 2 | 0 |
| uzzapbot_question_usage | 3 | 0 |
| uzzapbot_questions | 6 | 0 |
| uzzapbot_room_activity | 1 | 0 |
| uzzapbot_room_challenges | 2 | 0 |
| uzzapbot_room_settings | 1 | 0 |
| uzzapbot_titles | 1 | 0 |
| uzzapbot_tournaments | 2 | 0 |
| uzzapbot_welcomes | 1 | 0 |
| uzzapbot_word_dataset_entries | 2 | 0 |
| uzzapbot_word_datasets | 1 | 0 |
| uzzapbot_xp_history | 2 | 0 |

### RLS policy coverage

The production public schema currently has 54 RLS policies. Policy coverage is concentrated in the core messaging/moderation tables and selected UzzapBot administrative, memory, player-statistics, question-bank, and audit surfaces. This inventory is intentionally descriptive; policy expressions remain in the live database and are not rewritten here as a substitute for migrations.

### Restore status

This audit has reached the point where the remaining gap is **source recovery**, not production repair. Do not apply reconstructed DDL to the production project merely to make Git match the live database. The next canonical recovery artifact should come from an authorized schema-only PostgreSQL dump, followed by a diff against the repository's migrations and baseline.


## Column-level production manifest

A fresh read-only catalog query confirmed the production column counts below. This is metadata only; no row contents were exported.

| Table | Columns |
|---|---:|
| account_deletion_requests | 6 |
| blocked_users | 4 |
| buddies | 6 |
| buddy_requests | 6 |
| chat_categories | 3 |
| chat_rooms | 7 |
| direct_messages | 8 |
| game_players | 11 |
| game_sessions | 24 |
| moderation_actions | 12 |
| moderator_roles | 4 |
| profiles | 11 |
| room_invitations | 9 |
| room_messages | 7 |
| room_participants | 5 |
| room_sanctions | 10 |
| user_fcm_tokens | 5 |
| user_reports | 11 |
| uzzapbot_achievements | 5 |
| uzzapbot_ai_rate_state | 5 |
| uzzapbot_audit_log | 7 |
| uzzapbot_battles | 10 |
| uzzapbot_control_settings | 4 |
| uzzapbot_cursor | 3 |
| uzzapbot_daily_challenges | 10 |
| uzzapbot_event_receipts | 9 |
| uzzapbot_game_config | 6 |
| uzzapbot_game_modes | 6 |
| uzzapbot_game_pools | 8 |
| uzzapbot_internal_config | 3 |
| uzzapbot_memory | 10 |
| uzzapbot_performance_24h (view) | 5 |
| uzzapbot_player_achievements | 3 |
| uzzapbot_player_activity | 6 |
| uzzapbot_player_stats | 10 |
| uzzapbot_player_titles | 3 |
| uzzapbot_powerup_uses | 8 |
| uzzapbot_powerups | 4 |
| uzzapbot_processed_messages | 3 |
| uzzapbot_question_usage | 7 |
| uzzapbot_questions | 19 |
| uzzapbot_room_activity | 13 |
| uzzapbot_room_challenges | 7 |
| uzzapbot_room_settings | 7 |
| uzzapbot_titles | 4 |
| uzzapbot_tournaments | 11 |
| uzzapbot_welcomes | 3 |
| uzzapbot_word_dataset_entries | 6 |
| uzzapbot_word_datasets | 9 |
| uzzapbot_xp_history | 8 |

This establishes a second machine-checkable drift signal beyond table/index/trigger counts. A future recovery script can compare these counts and, preferably, exact column signatures against a schema-only dump.


## Constraint inventory

The live public schema was rechecked read-only and currently contains **49 primary keys, 55 foreign keys, 10 unique constraints, and 310 CHECK constraints**. The CHECK count includes PostgreSQL-generated NOT NULL constraints, so it should not be interpreted as 310 application-defined validation rules.

This confirms that table/column counts alone are insufficient for restoration. Primary keys, foreign keys, uniqueness rules, and CHECK constraints must also be preserved by any future baseline snapshot.

### Recovery rule

Do not convert this inventory into guessed `ALTER TABLE` statements. Exact constraint definitions, including CHECK expressions and FK actions, should be recovered from an authorized schema-only dump or exact catalog definitions before a migration is created.


## Exact constraint-definition sample from production

A read-only PostgreSQL catalog query using `pg_get_constraintdef()` confirmed that the production constraints contain concrete validation expressions and referential actions, not just names/counts. Examples include:

- `game_sessions.limit_count`: 1–5000
- `game_sessions.points`: 1–1000
- `game_sessions.question_number`: >= 0
- `uzzapbot_questions.points`: 1–1000
- `uzzapbot_questions.verification_score`: 0–100
- `uzzapbot_questions.difficulty`: easy/normal/hard
- `uzzapbot_questions.status`: draft/pending_review/approved/rejected/disabled/archived
- `uzzapbot_memory.confidence`: 0–1
- `uzzapbot_memory.memory_type`: fact/preference/context
- `uzzapbot_memory.source`: explicit/user_confirmed/bot_inferred
- `buddy_requests_not_self`: requester and recipient handles must differ case-insensitively

The production FK definitions also preserve explicit delete behavior, including CASCADE, SET NULL, and default NO ACTION depending on the relationship. Therefore a future restore artifact must capture the **definition**, not merely the existence of each FK.

The catalog currently reports **55 foreign keys** and **10 unique constraints**. The complete catalog result was used for verification; this document intentionally records the key recovery characteristics rather than copying a large raw catalog dump.


## Exact index-definition audit

Production was rechecked through `pg_indexes`. The public schema contains **122 indexes**. Their catalog definitions were captured for reconciliation rather than reconstructed manually.

Notable characteristics:

- **4 partial indexes** with `WHERE` predicates.
- **8 expression indexes**, including case-normalized `lower(...)` indexes used by username/handle/request lookup paths.
- Primary/unique constraint indexes are included in the 122 total.
- UzzapBot has indexes covering event-receipt status, question status/category, memory by user/room, player activity, WordHunt normalization, XP history, tournaments/challenges, and related game state.

A significant production detail was detected on `profiles`: both `profiles_owner_id_uidx` and `profiles_owner_id_unique` are present and are equivalent-looking partial unique indexes on `owner_id WHERE owner_id IS NOT NULL`. They are retained in the audit as distinct production objects; they should not be silently deduplicated during schema recovery.

No index DDL was applied to production.
