package com.kolipri.uzzap.data

import android.util.Log
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.model.ChatRoomCategory

/**
 * Room facade for the populated KOTLINJARZAP Supabase project.
 * The production database uses the existing chat_categories/chat_rooms/
 * room_messages/room_participants schema, so room operations are routed
 * through the established REST client instead of the empty UzzapX RPC schema.
 */
object UnifiedRoomClient {
    private const val TAG = "UnifiedRoomClient"

    fun fetchCategoriesAndRooms(onResult: (List<ChatRoomCategory>?) -> Unit) {
        SupabaseClient.fetchCategoriesAndRooms { result ->
            Log.i(TAG, "Loaded ${result?.size ?: 0} Uzzap region categories from KOTLINJARZAP")
            onResult(result)
        }
    }

    fun liveCounts(onResult: (Map<String, Int>?) -> Unit) {
        // The current source-of-truth schema stores participant rows but does
        // not require the newer UzzapX live-count RPC. Return an empty map;
        // the room UI can still display the directory and participant list.
        onResult(emptyMap())
    }

    fun join(roomName: String, onResult: (Boolean, String?) -> Unit) {
        if (!SupabaseClient.authenticatedForFeature()) {
            onResult(false, "Please sign in first.")
            return
        }
        SupabaseClient.pingPresence(roomName, AppState.nickname)
        onResult(true, null)
    }

    fun leave(roomName: String, onResult: ((Boolean) -> Unit)? = null) {
        SupabaseClient.leaveRoom(roomName, AppState.nickname)
        onResult?.invoke(true)
    }

    fun fetchParticipants(roomName: String, onResult: (List<String>?) -> Unit) {
        SupabaseClient.fetchParticipants(roomName, onResult)
    }

    fun fetchLatestMessages(roomName: String, limit: Int = 60, onResult: (List<ChatMessage>?) -> Unit) {
        SupabaseClient.fetchLatestMessages(roomName, limit, onResult)
    }

    fun fetchMessages(roomName: String, afterId: Long = 0, onResult: (List<ChatMessage>?) -> Unit) {
        SupabaseClient.fetchMessages(roomName, afterId, onResult)
    }

    fun fetchMessagesBefore(roomName: String, beforeId: Long, limit: Int = 50, onResult: (List<ChatMessage>?) -> Unit) {
        SupabaseClient.fetchMessagesBefore(roomName, beforeId, limit, onResult)
    }

    fun sendMessage(roomName: String, bodyText: String, onResult: (ChatMessage?) -> Unit) {
        SupabaseClient.sendMessage(roomName, AppState.nickname, bodyText, false, onResult)
    }
}
