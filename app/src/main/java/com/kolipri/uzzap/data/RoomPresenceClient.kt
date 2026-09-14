package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Live room membership/count API. Room capacity is enforced server-side at 20. */
object RoomPresenceClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient()
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun request(path: String, auth: Boolean = true): Request.Builder {
        val token = SupabaseClient.userAccessToken
        val bearer = if (auth && !token.isNullOrBlank()) "Bearer $token" else "Bearer ${SupabaseClient.anonKey}"
        return Request.Builder()
            .url("${SupabaseClient.supabaseUrl}$path")
            .header("apikey", SupabaseClient.anonKey)
            .header("Authorization", bearer)
            .header("Content-Type", "application/json")
    }

    fun liveCounts(onResult: (Map<String, Int>?) -> Unit) {
        val req = request("/rest/v1/rpc/get_live_room_counts").post("{}".toRequestBody(json)).build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(null) } }
            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) { main.post { onResult(null) }; return }
                    runCatching {
                        val arr = JSONArray(it.body?.string().orEmpty())
                        buildMap<String, Int> {
                            for (i in 0 until arr.length()) {
                                val row = arr.getJSONObject(i)
                                put(row.optString("room_name"), row.optInt("user_count", 0))
                            }
                        }
                    }.onSuccess { counts -> main.post { onResult(counts) } }
                        .onFailure { main.post { onResult(null) } }
                }
            }
        })
    }

    fun join(roomName: String, username: String, onResult: (Boolean, String?) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) { main.post { onResult(false, "Not signed in") }; return }
        val body = JSONObject().put("room_name", roomName).put("username", username).toString().toRequestBody(json)
        val encoded = URLEncoder.encode(roomName, "UTF-8")
        val req = request("/rest/v1/room_participants?on_conflict=room_name%2Cusername")
            .header("Prefer", "resolution=merge-duplicates")
            .post(body).build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(false, "Network error") } }
            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    if (it.isSuccessful) main.post { onResult(true, null) }
                    else {
                        val text = it.body?.string().orEmpty()
                        val message = if (text.contains("ROOM_FULL", true)) "Room is full (20/20)." else "Could not join room."
                        main.post { onResult(false, message) }
                    }
                }
            }
        })
    }
}
