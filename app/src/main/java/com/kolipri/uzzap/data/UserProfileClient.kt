package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import com.kolipri.uzzap.BuildConfig
import org.json.JSONArray
import java.net.URLEncoder
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class PublicProfile(
    val username: String,
    val nickname: String,
    val avatar: String,
    val status: String,
    val statusMessage: String,
    val isOnline: Boolean,
    val lastSeen: String
)

object UserProfileClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY

    private fun normalize(value: String) = value.trim().lowercase().replace(" ", "_")

    fun fetch(username: String, onResult: (PublicProfile?) -> Unit) {
        val normalized = normalize(username)
        if (normalized.isBlank()) { main.post { onResult(null) }; return }
        val encoded = URLEncoder.encode(normalized, "UTF-8")
        val path = "/rest/v1/profiles?username=eq.$encoded&select=username,nickname,avatar,status,status_message,is_online,last_seen&limit=1"
        val request = Request.Builder()
            .url("$url$path")
            .header("apikey", key)
            .header("Authorization", SupabaseClient.authHeader())
            .get()
            .build()
        http.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { main.post { onResult(null) } }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) { main.post { onResult(null) }; return }
                    val profile = runCatching {
                        val o = JSONArray(it.body?.string().orEmpty()).optJSONObject(0) ?: return@runCatching null
                        PublicProfile(
                            username = o.optString("username", normalized),
                            nickname = o.optString("nickname", o.optString("username", normalized)),
                            avatar = o.optString("avatar"),
                            status = o.optString("status", "offline"),
                            statusMessage = o.optString("status_message"),
                            isOnline = o.optBoolean("is_online") && TimeUtils.isWithinWindow(o.optString("last_seen"), 45L),
                            lastSeen = o.optString("last_seen")
                        )
                    }.getOrNull()
                    main.post { onResult(profile) }
                }
            }
        })
    }
}
