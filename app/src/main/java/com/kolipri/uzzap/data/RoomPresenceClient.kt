package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper

/** Current-schema room membership API. */
object RoomPresenceClient {
    private val main = Handler(Looper.getMainLooper())

    fun liveCounts(onResult: (Map<String, Int>?) -> Unit) = UnifiedRoomClient.liveCounts(onResult)

    fun join(roomName: String, username: String, onResult: (Boolean, String?) -> Unit) {
        AppState.joinedRooms.add(roomName)
        UnifiedRoomClient.join(roomName, onResult)
    }

    fun leave(roomName: String, username: String, onResult: ((Boolean) -> Unit)? = null) {
        AppState.joinedRooms.remove(roomName)
        UnifiedRoomClient.leave(roomName, onResult)
    }
}
