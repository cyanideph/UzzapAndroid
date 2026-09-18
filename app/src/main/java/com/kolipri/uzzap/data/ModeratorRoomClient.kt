package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Supabase access for the protected moderator-only room and role management. */
object ModeratorRoomClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    private val url = SupabaseClient.supabaseUrl.trimEnd('/')
    private val key = SupabaseClient.anonKey
    private val json = "application/json; charset=utf-8".toMediaType()
    private fun request(path: String): Request.Builder = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", SupabaseClient.authHeader()).header("Content-Type", "application/json")
    private fun rpc(name: String, body: JSONObject, callback: Callback) { http.newCall(request("/rest/v1/rpc/$name").post(body.toString().toRequestBody(json)).build()).enqueue(callback) }

    fun isModeratorOrAdmin(onResult: (Boolean) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) { main.post { onResult(false) }; return }
        rpc("is_moderator_or_admin", JSONObject(), object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(false) } }
            override fun onResponse(call: Call, response: Response) { response.use { val value = it.body?.string()?.trim()?.removeSurrounding("\""); main.post { onResult(it.isSuccessful && value.equals("true", true)) } } }
        })
    }

    fun fetchModeratorRoom(onResult: (String?) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) { main.post { onResult(null) }; return }
        val path = "/rest/v1/chat_rooms?is_moderator_only=is.true&select=id,name,topic&limit=1"
        http.newCall(request(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { main.post { onResult(null) }; return }
                val name = runCatching { JSONArray(it.body?.string().orEmpty()).optJSONObject(0)?.optString("name")?.ifBlank { null } }.getOrNull()
                main.post { onResult(name) }
            } }
        })
    }

    fun setModeratorByEmail(email: String, makeModerator: Boolean, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || SupabaseClient.userAccessToken.isNullOrBlank()) { main.post { onResult(false, "Sign in as an admin first") }; return }
        rpc("set_moderator_by_email", JSONObject().put("target_email", email.trim()).put("make_moderator", makeModerator), object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(false, e.message ?: "Network error") } }
            override fun onResponse(call: Call, response: Response) { response.use {
                val raw = it.body?.string().orEmpty(); val success = it.isSuccessful && raw.trim().removeSurrounding("\"").equals("true", true)
                main.post { onResult(success, if (success) null else "User not found or admin authorization was denied") }
            } }
        })
    }

    fun enterRoom(onResult: (Boolean, String?) -> Unit) {
        isModeratorOrAdmin { allowed -> if (!allowed) { onResult(false, "Moderator or Admin privileges required"); return@isModeratorOrAdmin }; fetchModeratorRoom { room -> onResult(room != null, room ?: "Moderator Room is not configured") } }
    }
}
