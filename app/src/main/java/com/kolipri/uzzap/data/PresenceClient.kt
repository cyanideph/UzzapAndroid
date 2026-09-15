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

data class BuddyPresence(val username: String, val isOnline: Boolean, val lastSeen: String, val status: String)

object PresenceClient {
    private const val ONLINE_WINDOW_SECONDS = 45L
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val json = "application/json; charset=utf-8".toMediaType()
    private var heartbeatRunnable: Runnable? = null

    private fun req(path: String) = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type", "application/json")

    fun heartbeat(isOnline: Boolean = true) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) return
        http.newCall(req("/rest/v1/rpc/touch_my_presence").post(JSONObject().put("p_is_online", isOnline).toString().toRequestBody(json)).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    fun startHeartbeat() {
        stopHeartbeat()
        heartbeat(true)
        val runnable = object : Runnable {
            override fun run() {
                if (SupabaseClient.userAccessToken.isNullOrBlank()) { heartbeatRunnable = null; return }
                heartbeat(true)
                main.postDelayed(this, 30_000L)
            }
        }
        heartbeatRunnable = runnable
        main.postDelayed(runnable, 30_000L)
    }

    fun stopHeartbeat() {
        heartbeatRunnable?.let(main::removeCallbacks)
        heartbeatRunnable = null
        if (!SupabaseClient.userAccessToken.isNullOrBlank()) heartbeat(false)
    }

    fun fetch(username: String, onResult: (BuddyPresence?) -> Unit) {
        val u = URLEncoder.encode(username.trim().lowercase().replace(" ", "_"), "UTF-8")
        val path = "/rest/v1/profiles?username=eq.$u&select=username,is_online,last_seen,status&limit=1"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { main.post { onResult(null) }; return }
                runCatching {
                    val obj = JSONArray(it.body?.string().orEmpty()).optJSONObject(0) ?: return@runCatching null
                    val last = obj.optString("last_seen")
                    val recent = TimeUtils.isWithinWindow(last, ONLINE_WINDOW_SECONDS)
                    BuddyPresence(obj.optString("username"), obj.optBoolean("is_online") && recent, last, obj.optString("status", "offline"))
                }.onSuccess { p -> main.post { onResult(p) } }.onFailure { main.post { onResult(null) } }
            } }
        })
    }

    fun fetchMultiple(usernames: List<String>, onResult: (Map<String, BuddyPresence>) -> Unit) {
        val distinct = usernames.distinct()
        if (distinct.isEmpty()) { main.post { onResult(emptyMap()) }; return }
        val results = java.util.concurrent.ConcurrentHashMap<String, BuddyPresence>()
        var remaining = distinct.size
        for (username in distinct) fetch(username) { presence -> if (presence != null) results[presence.username.lowercase()] = presence; remaining--; if (remaining == 0) main.post { onResult(results) } }
    }
}
