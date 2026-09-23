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

    private fun req(path: String) = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", SupabaseClient.authHeader()).header("Content-Type", "application/json")

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

    /** Fetches buddy presence in one PostgREST request instead of one HTTP request per buddy. */
    fun fetchMultiple(usernames: List<String>, onResult: (Map<String, BuddyPresence>) -> Unit) {
        val normalized = usernames
            .map { it.trim().lowercase().replace(" ", "_") }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalized.isEmpty()) {
            main.post { onResult(emptyMap()) }
            return
        }

        val filter = URLEncoder.encode("(${normalized.joinToString(",")})", "UTF-8")
        val path = "/rest/v1/profiles?username=in.$filter&select=username,is_online,last_seen,status"

        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post { onResult(emptyMap()) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        main.post { onResult(emptyMap()) }
                        return
                    }

                    val result = runCatching {
                        val array = JSONArray(it.body?.string().orEmpty())
                        buildMap<String, BuddyPresence> {
                            for (i in 0 until array.length()) {
                                val obj = array.optJSONObject(i) ?: continue
                                val username = obj.optString("username").trim()
                                if (username.isBlank()) continue
                                val last = obj.optString("last_seen")
                                val recent = TimeUtils.isWithinWindow(last, ONLINE_WINDOW_SECONDS)
                                val presence = BuddyPresence(
                                    username = username,
                                    isOnline = obj.optBoolean("is_online") && recent,
                                    lastSeen = last,
                                    status = obj.optString("status", "offline")
                                )
                                put(username.lowercase(), presence)
                            }
                        }
                    }.getOrDefault(emptyMap())

                    main.post { onResult(result) }
                }
            }
        })
    }
}
