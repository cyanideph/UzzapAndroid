# UzzapBot Hardcoded-Code Migration Plan

**Current checkpoint:** v86 / BOT_VERSION 4.5.1

## Goal
Keep core engine logic, parsing, security, and validation in TypeScript while moving changeable game rules, datasets, questions, pools, aliases, and content into Supabase.

## Status
- ✅ DONE — implemented and verified
- ⚠️ PARTIAL — implemented but still has hardcoded portions
- ⬜ TODO — remaining work

## 1. Data Migration
- ✅ English WordHunt dictionary moved to Supabase
- ✅ Tagalog WordHunt dictionary moved to Supabase
- ✅ Original entries and duplicates preserved
- ✅ Embedded WordHunt dataset removed from index.ts
- ✅ CROSS-BUN removed
- ✅ uzzapbot_word_datasets created
- ✅ uzzapbot_word_dataset_entries created
- ✅ Random-word RPC created
- ✅ Ambiguous anagram answers prevented
- ✅ Random game pools moved to uzzapbot_game_pools
- ✅ Question content uses uzzapbot_questions

## 2. Configuration System
- ✅ uzzapbot_game_config created
- ✅ XP, level, daily, battle, room challenge, tournament, timer, clue, powerup, TIME, and activity settings seeded
- ⬜ Make Edge Function actually read and use these values

## 3. Remove Hardcoded Game Rules
### XP and progression
- ⬜ Correct-answer XP
- ⬜ Wrong-answer XP
- ⬜ Level divisor
- ⬜ Achievement thresholds and requirements

### Game targets
- ⬜ Battle target
- ⬜ Room Challenge target
- ⬜ Tournament target
- ⬜ Tournament default rounds
- ⬜ Generic game limits

### Timers
- ⬜ Default timer
- ⬜ Speed timer
- ⬜ TIME powerup duration

### Clues
- ⬜ Maximum clues
- ⬜ First/second/third clue reveal ratios

### Powerups
- ⬜ Hint cooldown
- ⬜ Skip cooldown
- ⬜ Default cooldown
- ⬜ TIME duration

### Daily/activity
- ⬜ Daily attempt limit
- ⬜ Activity cooldown

## 4. Command System
Core command parsing, routing, and permission enforcement remain code-driven.
- ✅ Parser/routing/permissions remain in code
- ⬜ Consider DB-driven help descriptions
- ⬜ Consider configurable game-mode aliases
- ⬜ Add command enable/disable configuration

## 5. Answer Engine
- ✅ Normalization remains in code
- ✅ Case-insensitive matching verified
- ⚠️ Levenshtein matching remains code-driven
- ⬜ Move similarity threshold 0.88 to config
- ⬜ Move minimum guess/answer lengths to config
- ⬜ Add database-backed accepted aliases
- ⬜ Add modern/alternate answer support

## 6. Fallback Cleanup
- ✅ Large embedded WordHunt dataset removed
- ✅ Game pools are DB-first
- ⚠️ Tiny WordHunt fallback remains
- ⚠️ Tiny question fallback remains
- ⬜ Define controlled emergency fallback policy

## 7. Welcome Bot
- ✅ Welcome bot toggle
- ✅ /wcbot
- ✅ /wmsg
- ⚠️ Default welcome template remains hardcoded
- ⬜ Move default template to config
- ⬜ Support {room} and {nickname} placeholders

## 8. Security
- ✅ Dispatch token removed from plaintext table
- ✅ Dispatch token stored in Vault
- ✅ Service-role protection
- ✅ Internal token validation RPC
- ✅ Sensitive RPC authorization
- ✅ search_path='' hardening
- ✅ Bot recursion protection
- ✅ Duplicate dispatch trigger removed
- ✅ Receipt idempotency
- ✅ Room-message RLS
- ✅ Bot message isolation
- ✅ Hardcoded secrets audit
- ✅ Hardcoded URLs audit
- ✅ Region/province data removed from Edge Function

## 9. Testing
### Completed
- ✅ /version, /about, /status
- ✅ /lock, /unlock, /activate
- ✅ /timed, /score, /clue, /reveal, /stop
- ✅ Trivia, GTA Foreign, GTA OPM
- ✅ English and Tagalog WordHunt
- ✅ Correct/wrong answers
- ✅ Clue progression and score/attempt tracking
- ✅ Duplicate receipt protection
- ✅ Bot recursion protection
- ✅ Receipt consistency
- ✅ Dispatch trigger and duplicate-trigger cleanup
- ✅ v86 startup/E2E

### Remaining
- ⬜ Correct-answer → XP → level regression test
- ⬜ Battle
- ⬜ Room Challenge
- ⬜ Tournament
- ⬜ Daily Challenge
- ⬜ Powerups
- ⬜ Welcome Bot
- ⬜ Moderator controls
- ⬜ Config changes without redeployment
- ⬜ Failure/recovery behavior
- ⬜ Full regression test

## Target Architecture
```text
UzzapBot Edge Function
        |
        +-- GAME CONFIG ------> uzzapbot_game_config
        +-- QUESTIONS --------> uzzapbot_questions
        +-- DATASETS ---------> uzzapbot_word_datasets
        |                         +-> word_dataset_entries
        +-- POOLS ------------> uzzapbot_game_pools
        +-- GAME ENGINE ------> sessions / players / stats
```

## Next Implementation Step
Make `uzzapbot_game_config` the source of truth for existing hardcoded numeric rules, starting with XP, timers, clues, powerups, game targets, daily limits, and activity cooldown.

Changing a rule such as correct-answer XP from 10 to 25 should eventually require a database configuration change rather than editing and redeploying the Edge Function.
