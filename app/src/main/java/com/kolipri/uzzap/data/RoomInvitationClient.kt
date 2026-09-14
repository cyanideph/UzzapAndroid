package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kolipri.uzzap.BuildConfig
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

data class RoomInvitation(val id: Long, val roomName: String, val inviterUsername: String, val inviteeUsername: String, val status: String, val createdAt: String)

object RoomInvitationClient {
    private const val TAG = "RoomInvitationClient"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    private fun request(path: String): Request.Builder = Request.Builder().url("$url$path").header("apikey", anonKey).header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type", "application/json")
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    fun send(room: String, inviter: String, invitee: String, onResult: (Boolean) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank() || room.isBlank() || inviter.isBlank() || invitee.isBlank()) { main.post { onResult(false) }; return }
        val body = JSONObject().put("room_name", room.trim()).put("inviter_username", inviter.trim().lowercase().replace(" ", "_")).put("invitee_username", invitee.trim().lowercase().replace(" ", "_")).put("status", "pending")
        http.newCall(request("/rest/v1/room_invitations").header("Prefer", "return=minimal").post(body.toString().toRequestBody(jsonMediaType)).build()).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) { main.post { onResult(false) } }
            override fun onResponse(call: okhttp3.Call, response: Response) { response.use { main.post { onResult(it.isSuccessful) } } }
        })
    }

    fun fetchPending(username: String, onResult: (List<RoomInvitation>?) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) { main.post { onResult(null) }; return }
        val user = enc(username.lowercase().replace(" ", "_"))
        val path = "/rest/v1/room_invitations?invitee_username=eq.$user&status=eq.pending&select=id,room_name,inviter_username,invitee_username,status,created_at&order=created_at.desc&limit=50"
        http.newCall(request(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) { main.post { onResult(null) } }
            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { main.post { onResult(null) }; return }
                    try { val arr = JSONArray(it.body?.string().orEmpty()); val result = ArrayList<RoomInvitation>(arr.length()); for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); result += RoomInvitation(o.optLong("id"), o.optString("room_name"), o.optString("inviter_username"), o.optString("invitee_username"), o.optString("status"), o.optString("created_at")) }; main.post { onResult(result) } }
                    catch (e: Exception) { Log.w(TAG, "Invalid invitation response", e); main.post { onResult(null) } }
                }
            }
        })
    }

    fun respond(id: Long, accepted: Boolean, onResult: (Boolean) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank() || id <= 0) { main.post { onResult(false) }; return }
        val status = if (accepted) "accepted" else "declined"
        val body = JSONObject().put("status", status).put("responded_at", Instant.now().toString())
        http.newCall(request("/rest/v1/room_invitations?id=eq.$id").header("Prefer", "return=minimal").patch(body.toString().toRequestBody(jsonMediaType)).build()).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) { main.post { onResult(false) } }
            override fun onResponse(call: okhttp3.Call, response: Response) { response.use { main.post { onResult(it.isSuccessful) } } }
        })
    }
}
