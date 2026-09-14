package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import com.kolipri.uzzap.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

data class BuddyPresence(val username: String, val isOnline: Boolean, val lastSeen: String, val status: String)

object PresenceClient {
    private const val ONLINE_WINDOW_SECONDS = 35L
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun req(path: String) = Request.Builder().url("$url$path")
        .header("apikey", key)
        .header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}")
        .header("Content-Type", "application/json")

    /** Updates only the signed-in user's row; no exposed SECURITY DEFINER RPC is required. */
    fun heartbeat(isOnline: Boolean = true) {
        val token = SupabaseClient.userAccessToken
        val username = currentUsername()
        if (token.isNullOrBlank() || username.isNullOrBlank()) return
        val encoded = URLEncoder.encode(username.lowercase().replace(" ", "_"), "UTF-8")
        val body = JSONObject().put("is_online", isOnline).put("last_seen", Instant.now().toString())
        http.newCall(req("/rest/v1/profiles?username=eq.$encoded").patch(body.toString().toRequestBody(jsonMediaType)).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    fun fetch(username: String, onResult: (BuddyPresence?) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) { main.post { onResult(null) }; return }
        val u = URLEncoder.encode(username.lowercase().replace(" ", "_"), "UTF-8")
        val path = "/rest/v1/profiles?username=eq.$u&select=username,is_online,last_seen,status&limit=1"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { main.post { onResult(null) }; return }
                    val p = runCatching {
                        val o = JSONArray(it.body?.string().orEmpty()).optJSONObject(0) ?: return@runCatching null
                        val lastSeen = o.optString("last_seen")
                        val recent = runCatching { Instant.parse(lastSeen).isAfter(Instant.now().minusSeconds(ONLINE_WINDOW_SECONDS)) }.getOrDefault(false)
                        BuddyPresence(o.optString("username"), o.optBoolean("is_online") && recent, lastSeen, o.optString("status", "Available"))
                    }.getOrNull()
                    main.post { onResult(p) }
                }
            }
        })
    }

    private fun currentUsername(): String? = AppState.username.takeIf { it.isNotBlank() && it != "unknown-user" }
}
