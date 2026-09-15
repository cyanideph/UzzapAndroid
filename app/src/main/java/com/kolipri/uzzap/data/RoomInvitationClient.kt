package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import com.kolipri.uzzap.BuildConfig
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RoomInvitation(val id: Long, val roomName: String, val inviterUsername: String, val inviteeUsername: String, val status: String, val createdAt: String)

object RoomInvitationClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val json = "application/json; charset=utf-8".toMediaType()
    private fun request(path: String): Request.Builder = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type", "application/json")
    private fun rpc(name: String, body: JSONObject, callback: Callback) { http.newCall(request("/rest/v1/rpc/$name").post(body.toString().toRequestBody(json)).build()).enqueue(callback) }

    fun send(room: String, inviter: String, invitee: String, onResult: (Boolean) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank() || room.isBlank() || invitee.isBlank()) { main.post { onResult(false) }; return }
        rpc("create_room_invitation", JSONObject().put("p_room_name", room.trim()).put("p_invitee_username", invitee.trim()), object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(false) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                val value = it.body?.string()?.trim()?.removeSurrounding("\"")
                main.post { onResult(it.isSuccessful && value.equals("true", true)) }
            } }
        })
    }

    fun fetchPending(username: String, onResult: (List<RoomInvitation>?) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) { main.post { onResult(null) }; return }
        val user = URLEncoder.encode(username.trim().lowercase().replace(" ", "_"), "UTF-8")
        val path = "/rest/v1/room_invitations?invitee_username=eq.$user&status=eq.pending&select=id,room_name,inviter_username,invitee_username,status,created_at&order=created_at.desc&limit=50"
        http.newCall(request(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { main.post { onResult(null) }; return }
                runCatching { val arr = JSONArray(it.body?.string().orEmpty()); List(arr.length()) { i -> val o = arr.getJSONObject(i); RoomInvitation(o.optLong("id"), o.optString("room_name"), o.optString("inviter_username"), o.optString("invitee_username"), o.optString("status"), o.optString("created_at")) } }
                    .onSuccess { main.post { onResult(it) } }.onFailure { main.post { onResult(null) } }
            } }
        })
    }

    fun respond(id: Long, accepted: Boolean, onResult: (Boolean) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank() || id <= 0) { main.post { onResult(false) }; return }
        rpc("respond_room_invitation", JSONObject().put("p_invitation_id", id).put("p_accepted", accepted), object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(false) } }
            override fun onResponse(call: Call, response: Response) { response.use { val value = it.body?.string()?.trim()?.removeSurrounding("\""); main.post { onResult(it.isSuccessful && value.equals("true", true)) } } }
        })
    }
}
