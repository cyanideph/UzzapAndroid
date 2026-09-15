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
        var id = UUID.fromString(uuid).mostSignificantBits xor UUID.fromString(uuid).leastSignificantBits
        if (id == 0L) id = 1L
        while (true) {
            val previous = longToUuid.putIfAbsent(id, uuid)
            if (previous == null || previous == uuid) {
                uuidToLong[uuid] = id
                return id
            }
            id++
        }
    }

    fun uuidFor(legacyId: Long): String? = longToUuid[legacyId]
}
