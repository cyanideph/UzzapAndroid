package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Log
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

data class BuddyRequest(val id: Long, val requesterHandle: String, val recipientHandle: String, val createdAt: String)

object BuddyRequestClient {
    private const val TAG = "BuddyRequestClient"
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun request(path: String): Request.Builder {
        val token = SupabaseClient.userAccessToken
        return Request.Builder()
            .url("${SupabaseClient.supabaseUrl}$path")
            .header("apikey", SupabaseClient.anonKey)
            .header("Authorization", "Bearer ${token ?: SupabaseClient.anonKey}")
            .header("Content-Type", "application/json")
    }

    fun send(requester: String, recipient: String, onResult: (Boolean, String?) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) { handler.post { onResult(false, "Not signed in") }; return }
        val body = JSONObject().put("requester_handle", requester.trim()).put("recipient_handle", recipient.trim())
        client.newCall(request("/rest/v1/buddy_requests").post(body.toString().toRequestBody(jsonType)).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { handler.post { onResult(false, e.message ?: "Network error") } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (it.isSuccessful) handler.post { onResult(true, null) }
                    else {
                        val msg = runCatching { JSONObject(raw).optString("message") }.getOrDefault("").ifBlank { "Unable to send buddy request" }
                        handler.post { onResult(false, msg) }
                    }
                }
            }
        })
    }

    fun fetchPending(recipient: String, onResult: (List<BuddyRequest>?) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) { handler.post { onResult(emptyList()) }; return }
        val encoded = URLEncoder.encode(recipient.trim(), "UTF-8")
        val path = "/rest/v1/buddy_requests?recipient_handle=ilike.${encoded}&status=eq.pending&select=id,requester_handle,recipient_handle,created_at&order=created_at.desc"
        client.newCall(request(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { Log.w(TAG, "fetchPending failed", e); handler.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { handler.post { onResult(null) }; return }
                    try {
                        val arr = JSONArray(it.body?.string().orEmpty())
                        val result = buildList {
                            for (i in 0 until arr.length()) {
                                val o = arr.getJSONObject(i)
                                add(BuddyRequest(o.optLong("id"), o.optString("requester_handle"), o.optString("recipient_handle"), o.optString("created_at")))
                            }
                        }
                        handler.post { onResult(result) }
                    } catch (e: Exception) { Log.w(TAG, "fetchPending parse failed", e); handler.post { onResult(null) } }
                }
            }
        })
    }

    fun respond(requestId: Long, accept: Boolean, onResult: (Boolean) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) { handler.post { onResult(false) }; return }
        val body = JSONObject().put("p_request_id", requestId).put("p_accept", accept)
        client.newCall(request("/rest/v1/rpc/respond_to_buddy_request").post(body.toString().toRequestBody(jsonType)).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { handler.post { onResult(false) } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val ok = it.isSuccessful && (it.body?.string()?.contains("true") == true)
                    handler.post { onResult(ok) }
                }
            }
        })
    }
}