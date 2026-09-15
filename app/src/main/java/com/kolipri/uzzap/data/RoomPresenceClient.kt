package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper

/** Current-schema room membership API. */
object RoomPresenceClient {
    private val main = Handler(Looper.getMainLooper())

    fun liveCounts(onResult: (Map<String, Int>?) -> Unit) = UnifiedRoomClient.liveCounts(onResult)

    fun join(roomName: String, username: String, onResult: (Boolean, String?) -> Unit) =
        UnifiedRoomClient.join(roomName, onResult)

    fun leave(roomName: String, username: String, onResult: ((Boolean) -> Unit)? = null) =
        UnifiedRoomClient.leave(roomName, onResult)
}
