package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.model.ChatRoomCategory

/** Room facade backed by the populated Supabase schema. */
object UnifiedRoomClient {
    private const val TAG = "UnifiedRoomClient"
    // The room UI polls every 2.5s. Keep a small REST throttle so a lifecycle/realtime
    // reconnect cannot leave the client displaying a stale message snapshot.
    private const val FALLBACK_POLL_INTERVAL_MS = 2_000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fallbackFetchAt = mutableMapOf<String, Long>()

    fun fetchCategoriesAndRooms(onResult: (List<ChatRoomCategory>?) -> Unit) {
        SupabaseClient.fetchCategoriesAndRooms { result ->
            Log.i(TAG, "Loaded ${result?.size ?: 0} Uzzap region categories")
            onResult(result)
        }
    }

    fun liveCounts(onResult: (Map<String, Int>?) -> Unit) {
        SupabaseClient.fetchLiveRoomCounts { remoteCounts ->
            val counts = (remoteCounts ?: emptyMap()).toMutableMap()
            for (room in AppState.joinedRooms) {
                val cur = counts[room] ?: 0
                if (cur < 1) counts[room] = 1
            }
            onResult(counts)
        }
    }

    fun join(roomName: String, onResult: (Boolean, String?) -> Unit) {
        SupabaseClient.pingPresence(roomName, AppState.nickname)
        onResult(true, null)
    }

    fun leave(roomName: String, onResult: ((Boolean) -> Unit)? = null) {
        synchronized(fallbackFetchAt) { fallbackFetchAt.remove(roomName) }
        SupabaseClient.leaveRoom(roomName, AppState.nickname)
        onResult?.invoke(true)
    }

    fun fetchParticipants(roomName: String, onResult: (List<String>?) -> Unit) =
        SupabaseClient.fetchParticipants(roomName, onResult)

    fun fetchLatestMessages(roomName: String, limit: Int = 60, onResult: (List<ChatMessage>?) -> Unit) =
        SupabaseClient.fetchLatestMessages(roomName, limit, onResult)

    /**
     * Incremental room synchronization.
     *
     * REST is intentionally used even while Realtime is connected. Realtime delivers
     * low-latency events, but it cannot be treated as the authoritative history after
     * Android background/foreground transitions or a channel reconnect. The server
     * history query closes that gap and the fragment de-duplicates by message id.
     * forceRest bypasses the small throttle for an explicit reconnect sync.
     */
    fun fetchMessages(
        roomName: String,
        afterId: Long = 0,
        forceRest: Boolean = false,
        onResult: (List<ChatMessage>?) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val shouldFetch = if (forceRest) {
            synchronized(fallbackFetchAt) { fallbackFetchAt[roomName] = now }
            true
        } else {
            synchronized(fallbackFetchAt) {
                val last = fallbackFetchAt[roomName] ?: 0L
                if (now - last >= FALLBACK_POLL_INTERVAL_MS) {
                    fallbackFetchAt[roomName] = now
                    true
                } else {
                    false
                }
            }
        }

        if (!shouldFetch) {
            mainHandler.post { onResult(emptyList()) }
            return
        }

        SupabaseClient.fetchMessages(roomName, afterId, onResult)
    }

    fun fetchMessagesBefore(roomName: String, beforeId: Long, limit: Int = 50, onResult: (List<ChatMessage>?) -> Unit) =
        SupabaseClient.fetchMessagesBefore(roomName, beforeId, limit, onResult)

    fun sendMessage(roomName: String, bodyText: String, onResult: (ChatMessage?) -> Unit) =
        SupabaseClient.sendMessage(roomName, AppState.nickname, bodyText, false, onResult)
}
