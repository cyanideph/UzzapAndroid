package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kolipri.uzzap.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Current-schema room membership API with a foreground heartbeat. */
object RoomPresenceClient {
    private const val TAG = "RoomPresenceClient"
    private const val HEARTBEAT_INTERVAL_MS = 15_000L
    private val main = Handler(Looper.getMainLooper())
    private val activeRooms = linkedSetOf<String>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val heartbeat = object : Runnable {
        override fun run() {
            synchronized(activeRooms) {
                activeRooms.toList().forEach { room ->
                    pingPresence(room, AppState.nickname, null)
                }
            }
            if (hasActiveRooms()) main.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    fun liveCounts(onResult: (Map<String, Int>?) -> Unit) = UnifiedRoomClient.liveCounts(onResult)

    /** Join is confirmed only after Supabase accepts the participant upsert. */
    fun join(roomName: String, username: String, onResult: (Boolean, String?) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) {
            main.post { onResult(false, "Your session has expired. Please sign in again.") }
            return
        }
        pingPresence(roomName, username, onResult)
    }

    fun resumeAll() {
        val rooms = synchronized(activeRooms) { activeRooms.toList() }
        rooms.forEach { room -> pingPresence(room, AppState.nickname, null) }
        if (rooms.isNotEmpty()) startHeartbeat()
    }

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

    private fun pingPresence(
        roomName: String,
        username: String,
        onResult: ((Boolean, String?) -> Unit)?
    ) {
        val token = SupabaseClient.userAccessToken
        if (token.isNullOrBlank()) {
            onResult?.let { main.post { it(false, "Your session has expired. Please sign in again.") } }
            return
        }

        val json = JSONObject()
            .put("room_name", roomName)
            .put("username", username)
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/room_participants?on_conflict=room_name,username")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()

        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Presence request failed for $roomName", e)
                onResult?.let { main.post { it(false, "Unable to join room. Check your connection and try again.") } }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        val message = try {
                            val obj = JSONObject(body)
                            obj.optString("message")
                                .ifBlank { obj.optString("details") }
                                .ifBlank { obj.optString("hint") }
                                .ifBlank { "Supabase HTTP ${it.code}" }
                        } catch (_: Exception) {
                            "Supabase HTTP ${it.code}"
                        }
                        Log.w(TAG, "Presence rejected for $roomName: $message")
                        onResult?.let { callback -> main.post { callback(false, message) } }
                        return
                    }

                    if (onResult != null) {
                        synchronized(activeRooms) { activeRooms.add(roomName) }
                        AppState.joinedRooms.add(roomName)
                        startHeartbeat()
                        main.post { onResult(true, null) }
                    }
                }
            }
        })
    }
}
