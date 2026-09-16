package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper

/** Current-schema room membership API with a foreground heartbeat. */
object RoomPresenceClient {
    private const val HEARTBEAT_INTERVAL_MS = 15_000L
    private val main = Handler(Looper.getMainLooper())
    private val activeRooms = linkedSetOf<String>()
    private val heartbeat = object : Runnable {
        override fun run() {
            synchronized(activeRooms) {
                activeRooms.toList().forEach { room ->
                    SupabaseClient.pingPresence(room, AppState.nickname)
                }
            }
            if (hasActiveRooms()) main.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    fun liveCounts(onResult: (Map<String, Int>?) -> Unit) = UnifiedRoomClient.liveCounts(onResult)

    fun join(roomName: String, username: String, onResult: (Boolean, String?) -> Unit) {
        AppState.joinedRooms.add(roomName)
        synchronized(activeRooms) { activeRooms.add(roomName) }
        SupabaseClient.pingPresence(roomName, AppState.nickname)
        startHeartbeat()
        onResult(true, null)
    }

    /** Start/restore room presence after returning to the foreground. */
    fun resumeAll() {
        val rooms = synchronized(activeRooms) { activeRooms.toList() }
        rooms.forEach { SupabaseClient.pingPresence(it, AppState.nickname) }
        if (rooms.isNotEmpty()) startHeartbeat()
    }

    /** Stop network heartbeat only; do not leave rooms. */
    fun pauseHeartbeat() {
        main.removeCallbacks(heartbeat)
    }

    fun leave(roomName: String, username: String, onResult: ((Boolean) -> Unit)? = null) {
        synchronized(activeRooms) { activeRooms.remove(roomName) }
        AppState.joinedRooms.remove(roomName)
        SupabaseClient.leaveRoom(roomName, AppState.nickname)
        if (!hasActiveRooms()) main.removeCallbacks(heartbeat)
        onResult?.invoke(true)
    }

    fun leaveAll() {
        val rooms = synchronized(activeRooms) { activeRooms.toList() }
        rooms.forEach { room ->
            SupabaseClient.leaveRoom(room, AppState.nickname)
            AppState.joinedRooms.remove(room)
        }
        synchronized(activeRooms) { activeRooms.clear() }
        main.removeCallbacks(heartbeat)
    }

    private fun hasActiveRooms(): Boolean = synchronized(activeRooms) { activeRooms.isNotEmpty() }

    private fun startHeartbeat() {
        main.removeCallbacks(heartbeat)
        main.postDelayed(heartbeat, HEARTBEAT_INTERVAL_MS)
    }
}
