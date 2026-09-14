package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.kolipri.uzzap.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object SafetyClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun req(path: String) = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type", "application/json")
    private fun encoded(value: String) = URLEncoder.encode(value.lowercase().replace(" ", "_"), "UTF-8")

    fun isBlocked(username: String, onResult: (Boolean) -> Unit) {
        val owner = currentUserId() ?: run { main.post { onResult(false) }; return }
        val u = encoded(username)
        val o = encoded(owner)
        http.newCall(req("/rest/v1/blocked_users?owner_id=eq.$o&blocked_username=eq.$u&select=id&limit=1").get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(false) } }
            override fun onResponse(call: Call, response: Response) { response.use { val body = it.body?.string().orEmpty(); main.post { onResult(it.isSuccessful && JSONArray(body).length() > 0) } } }
        })
    }

    fun block(username: String, onResult: (Boolean) -> Unit) {
        val owner = currentUserId() ?: run { main.post { onResult(false) }; return }
        val body = JSONObject().put("owner_id", owner).put("blocked_username", username.lowercase().replace(" ", "_"))
        http.newCall(req("/rest/v1/blocked_users?on_conflict=owner_id,blocked_username").header("Prefer", "resolution=merge-duplicates,return=minimal").post(body.toString().toRequestBody(jsonMediaType)).build()).enqueue(callback(onResult))
    }

    fun unblock(username: String, onResult: (Boolean) -> Unit) {
        val owner = currentUserId() ?: run { main.post { onResult(false) }; return }
        val u = encoded(username)
        val o = encoded(owner)
        http.newCall(req("/rest/v1/blocked_users?owner_id=eq.$o&blocked_username=eq.$u").delete().build()).enqueue(callback(onResult))
    }

    fun report(username: String, reason: String, details: String, onResult: (Boolean) -> Unit) {
        val reporter = currentUserId() ?: run { main.post { onResult(false) }; return }
        val body = JSONObject().put("reporter_id", reporter).put("reported_username", username.lowercase().replace(" ", "_")).put("reason", reason.take(100)).put("details", details.take(1000))
        http.newCall(req("/rest/v1/user_reports").post(body.toString().toRequestBody(jsonMediaType)).build()).enqueue(callback(onResult))
    }

    private fun currentUserId(): String? {
        val token = SupabaseClient.userAccessToken ?: return null
        return runCatching {
            val payload = token.split('.').getOrNull(1) ?: return@runCatching null
            val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            JSONObject(String(bytes, Charsets.UTF_8)).optString("sub").ifBlank { null }
        }.getOrNull()
    }

    private fun callback(onResult: (Boolean) -> Unit) = object : Callback {
        override fun onFailure(call: Call, e: IOException) { main.post { onResult(false) } }
        override fun onResponse(call: Call, response: Response) { response.use { main.post { onResult(it.isSuccessful) } } }
    }
}
