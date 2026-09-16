package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.model.ChatRoomCategory

/** Room facade backed by the populated KOTLINJARZAP Supabase schema. */
object UnifiedRoomClient {
    private const val TAG = "UnifiedRoomClient"
    private const val FALLBACK_POLL_INTERVAL_MS = 15_000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fallbackFetchAt = mutableMapOf<String, Long>()
    private fun displayNickname(): String = AppState.nickname.ifBlank { AppState.username }

    fun fetchCategoriesAndRooms(onResult: (List<ChatRoomCategory>?) -> Unit) { SupabaseClient.fetchCategoriesAndRooms { result -> Log.i(TAG, "Loaded ${result?.size ?: 0} Uzzap region categories"); onResult(result) } }
    fun liveCounts(onResult: (Map<String, Int>?) -> Unit) { SupabaseClient.fetchLiveRoomCounts { remoteCounts -> val counts = (remoteCounts ?: emptyMap()).toMutableMap(); for (room in AppState.joinedRooms) { val cur = counts[room] ?: 0; if (cur < 1) counts[room] = 1 }; onResult(counts) } }
    fun join(roomName: String, onResult: (Boolean, String?) -> Unit) { SupabaseClient.pingPresence(roomName, displayNickname()); onResult(true, null) }
    fun leave(roomName: String, onResult: ((Boolean) -> Unit)? = null) { synchronized(fallbackFetchAt) { fallbackFetchAt.remove(roomName) }; SupabaseClient.leaveRoom(roomName, displayNickname()); onResult?.invoke(true) }
    fun fetchParticipants(roomName: String, onResult: (List<String>?) -> Unit) = SupabaseClient.fetchParticipants(roomName, onResult)
    fun fetchLatestMessages(roomName: String, limit: Int = 60, onResult: (List<ChatMessage>?) -> Unit) = SupabaseClient.fetchLatestMessages(roomName, limit, onResult)
    fun fetchMessages(roomName: String, afterId: Long = 0, forceRest: Boolean = false, onResult: (List<ChatMessage>?) -> Unit) {
        if (SupabaseRealtimeClient.isConnected() && !forceRest) { mainHandler.post { onResult(emptyList()) }; return }
        val now = System.currentTimeMillis()
        val shouldFetch = if (forceRest) true else synchronized(fallbackFetchAt) { val last = fallbackFetchAt[roomName] ?: 0L; if (now - last >= FALLBACK_POLL_INTERVAL_MS) { fallbackFetchAt[roomName] = now; true } else false }
        if (!shouldFetch) { mainHandler.post { onResult(emptyList()) }; return }
        SupabaseClient.fetchMessages(roomName, afterId, onResult)
    }
    fun fetchMessagesBefore(roomName: String, beforeId: Long, limit: Int = 50, onResult: (List<ChatMessage>?) -> Unit) = SupabaseClient.fetchMessagesBefore(roomName, beforeId, limit, onResult)
    fun sendMessage(roomName: String, bodyText: String, onResult: (ChatMessage?) -> Unit) = SupabaseClient.sendMessage(roomName, displayNickname(), bodyText, false, onResult)
}
