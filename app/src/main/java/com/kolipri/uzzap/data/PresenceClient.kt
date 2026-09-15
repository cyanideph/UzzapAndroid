package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import com.kolipri.uzzap.BuildConfig
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

data class BuddyPresence(val username: String, val isOnline: Boolean, val lastSeen: String, val status: String)

object PresenceClient {
    private const val ONLINE_WINDOW_SECONDS = 35L
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun req(path: String) = Request.Builder()
        .url("$url$path")
        .header("apikey", key)
        .header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}")
        .header("Content-Type", "application/json")

    fun heartbeat(isOnline: Boolean = true) {
        http.newCall(req("/rest/v1/rpc/set_presence")
            .post(JSONObject().put("p_status", if (isOnline) "online" else "offline").toString().toRequestBody(json))
            .build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { }
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    fun fetch(username: String, onResult: (BuddyPresence?) -> Unit) {
        http.newCall(req("/rest/v1/rpc/get_user_presence")
            .post(JSONObject().put("p_username", username).toString().toRequestBody(json))
            .build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post { onResult(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        main.post { onResult(null) }
                        return
                    }
                    runCatching {
                        val array = JSONArray(it.body?.string().orEmpty())
                        val obj = array.optJSONObject(0) ?: return@runCatching null
                        val last = obj.optString("last_seen")
                        val recent = runCatching {
                            Instant.parse(last).isAfter(Instant.now().minusSeconds(ONLINE_WINDOW_SECONDS))
                        }.getOrDefault(false)
                        BuddyPresence(
                            obj.optString("username"),
                            obj.optBoolean("is_online") && recent,
                            last,
                            obj.optString("status", "offline")
                        )
                    }.onSuccess { presence ->
                        main.post { onResult(presence) }
                    }.onFailure {
                        main.post { onResult(null) }
                    }
                }
            }
        })
    }

    fun fetchMultiple(usernames: List<String>, onResult: (Map<String, BuddyPresence>) -> Unit) {
        val distinct = usernames.distinct()
        if (distinct.isEmpty()) {
            main.post { onResult(emptyMap()) }
            return
        }
        val results = java.util.concurrent.ConcurrentHashMap<String, BuddyPresence>()
        var remaining = distinct.size
        for (username in distinct) {
            fetch(username) { presence ->
                if (presence != null) results[presence.username.lowercase()] = presence
                remaining--
                if (remaining == 0) main.post { onResult(results) }
            }
        }
    }
}
