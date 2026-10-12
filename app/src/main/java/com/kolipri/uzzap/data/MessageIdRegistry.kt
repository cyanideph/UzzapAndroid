package com.kolipri.uzzap.data

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Bridges the legacy Long UI ids to the UUID ids used by the current Supabase schema. */
object MessageIdRegistry {
    private val longToUuid = ConcurrentHashMap<Long, String>()
    private val uuidToLong = ConcurrentHashMap<String, Long>()

    fun register(uuid: String): Long {
        val existing = uuidToLong[uuid]
        if (existing != null) return existing
        // xor is negative ~50% of the time and fromString throws on
        // non-UUID ids; both cases produced unusable ids (UI requires id > 0)
        // and silently dropped realtime messages/deletes.
        var id = try {
            val parsed = UUID.fromString(uuid)
            parsed.mostSignificantBits xor parsed.leastSignificantBits
        } catch (_: Exception) {
            uuid.hashCode().toLong()
        }
        if (id == Long.MIN_VALUE) id = Long.MAX_VALUE
        id = kotlin.math.abs(id)
        if (id == 0L) id = 1L
        while (true) {
            if (id <= 0L) id = 1L
            val previous = longToUuid.putIfAbsent(id, uuid)
            if (previous == null || previous == uuid) {
                uuidToLong[uuid] = id
                return id
            }
            if (id == Long.MAX_VALUE) id = 1L else id++
        }
    }

    fun uuidFor(legacyId: Long): String? = longToUuid[legacyId]
}
