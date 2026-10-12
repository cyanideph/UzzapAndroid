package com.kolipri.uzzap.data

import java.net.URLEncoder

/**
 * The two username-casing rules. Violating either one silently breaks
 * features for mixed-case usernames, so read this before touching
 * any username filter:
 *
 * 1. IDENTITY columns keep exact case. `direct_messages.sender/recipient`,
 *    `room_participants.username` and realtime filters must use the exact
 *    canonical case from the profile (`AppState.username.trim()`).
 *    Never lowercase these — see SupabaseRealtimeClient.
 *
 * 2. LOOKUP filters must be case-insensitive. `profiles.username`,
 *    `buddies.user_handle` and `room_invitations.invitee_username` are
 *    stored in registration case, so `eq.<lowercased>` misses rows.
 *    Use the [ilikeFilter]/[ilikeOrFilter] builders plus a client-side
 *    [matchesHandle] check (ilike treats `%`/`_` as wildcards, and two
 *    profiles may differ only by case).
 */
fun canonicalizeUsername(username: String): String =
    username.trim().lowercase().replace(" ", "_")

/** Case-insensitive client-side handle equality (mirrors the DB lower() checks). */
fun matchesHandle(stored: String?, wanted: String): Boolean =
    canonicalizeUsername(stored.orEmpty()) == canonicalizeUsername(wanted)

private fun quotePostgrestValue(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

/** `column=ilike."value"` — case-insensitive equality for a single lookup. */
fun ilikeFilter(column: String, value: String): String =
    "$column=ilike.${quotePostgrestValue(value.trim())}"

/**
 * `or=(column.ilike."a",column.ilike."b")` — case-insensitive equality
 * for batch lookups. Filters the requested list to distinct non-blank
 * values first; returns "" when there is nothing to look up.
 */
fun ilikeOrFilter(column: String, values: Collection<String>): String {
    val distinct = values.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (distinct.isEmpty()) return ""
    return "or=(${distinct.joinToString(",") { v -> "$column.ilike.${quotePostgrestValue(v)}" }})"
}

fun encodeFilter(filter: String): String = URLEncoder.encode(filter, "UTF-8")
