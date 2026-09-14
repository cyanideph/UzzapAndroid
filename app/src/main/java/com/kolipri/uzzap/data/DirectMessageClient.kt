package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.model.DirectMessage
import okhttp3.Call
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
import java.util.concurrent.TimeUnit

object DirectMessageClient {
    private const val TAG = "DirectMessageClient"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    private fun request(path: String): Request.Builder = Request.Builder().url("$url$path").header("apikey", anonKey).header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type", "application/json")
    private fun encoded(value: String) = URLEncoder.encode(value, "UTF-8")

    fun fetchConversation(me: String, buddy: String, afterId: Long = 0, onResult: (List<DirectMessage>?) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) { mainHandler.post { onResult(null) }; return }
        val a = encoded(me); val b = encoded(buddy); val after = if (afterId > 0) "&id=gt.$afterId" else ""
        val filter = "or=(and(sender.eq.$a,recipient.eq.$b),and(sender.eq.$b,recipient.eq.$a))"
        val path = "/rest/v1/direct_messages?select=id,sender,recipient,body,created_at&$filter&order=id.asc&limit=100$after"
        http.newCall(request(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { mainHandler.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { Log.w(TAG, "fetchConversation failed: HTTP ${it.code}"); mainHandler.post { onResult(null) }; return }
                    try {
                        val arr = JSONArray(it.body?.string().orEmpty()); val result = ArrayList<DirectMessage>(arr.length())
                        for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); result += DirectMessage(o.optLong("id"), o.optString("sender"), o.optString("recipient"), o.optString("body"), o.optString("created_at")) }
                        mainHandler.post { onResult(result) }
                    } catch (e: Exception) { Log.w(TAG, "Invalid direct-message response", e); mainHandler.post { onResult(null) } }
                }
            }
        })
    }

    fun sendMessage(me: String, buddy: String, body: String, onResult: (DirectMessage?) -> Unit) {
        val clean = body.trim()
        if (SupabaseClient.userAccessToken.isNullOrBlank() || clean.isEmpty() || clean.length > 2000) { mainHandler.post { onResult(null) }; return }
        resolveOwnerId(me) { senderId ->
            if (senderId == null) { mainHandler.post { onResult(null) }; return@resolveOwnerId }
            resolveOwnerId(buddy) { recipientId ->
                if (recipientId == null) { mainHandler.post { onResult(null) }; return@resolveOwnerId }
                val json = JSONObject().put("sender_id", senderId).put("recipient_id", recipientId).put("sender", me).put("recipient", buddy).put("body", clean)
                http.newCall(request("/rest/v1/direct_messages").header("Prefer", "return=representation").post(json.toString().toRequestBody(jsonMediaType)).build()).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { mainHandler.post { onResult(null) } }
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!it.isSuccessful) { Log.w(TAG, "sendMessage failed: HTTP ${it.code}"); mainHandler.post { onResult(null) }; return }
                            try {
                                val o = JSONArray(it.body?.string().orEmpty()).optJSONObject(0)
                                mainHandler.post { onResult(o?.let { item -> DirectMessage(item.optLong("id"), item.optString("sender", me), item.optString("recipient", buddy), item.optString("body", clean), item.optString("created_at")) }) }
                            } catch (_: Exception) { mainHandler.post { onResult(null) } }
                        }
                    }
                })
            }
        }
    }

    private fun resolveOwnerId(username: String, onResult: (String?) -> Unit) {
        val u = encoded(username.lowercase().replace(" ", "_")); val path = "/rest/v1/profiles?username=eq.$u&select=owner_id&limit=1"
        http.newCall(request(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { mainHandler.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) {
                response.use { if (!it.isSuccessful) { mainHandler.post { onResult(null) }; return }; val id = runCatching { JSONArray(it.body?.string().orEmpty()).optJSONObject(0)?.optString("owner_id")?.ifBlank { null } }.getOrNull(); mainHandler.post { onResult(id) } }
            }
        })
    }
}
