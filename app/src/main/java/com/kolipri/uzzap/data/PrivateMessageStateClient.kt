package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import com.kolipri.uzzap.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class UnreadPrivateMessage(val count: Int, val senders: List<String>, val countsBySender: Map<String, Int> = emptyMap())

object PrivateMessageStateClient {
    private const val UNREAD_PAGE_SIZE = 1000
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun req(path: String) = Request.Builder().url("$url$path")
        .header("apikey", key)
        .header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}")
        .header("Content-Type", "application/json")

    fun fetchUnread(username: String, onResult: (UnreadPrivateMessage?) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) { main.post { onResult(null) }; return }
        val u = URLEncoder.encode(username.lowercase().replace(" ", "_"), "UTF-8")
        val path = "/rest/v1/direct_messages?recipient=eq.$u&is_read=eq.false&select=id,sender&order=created_at.asc&limit=$UNREAD_PAGE_SIZE"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { main.post { onResult(null) }; return }
                    val senders = mutableListOf<String>(); val counts = mutableMapOf<String, Int>()
                    runCatching {
                        val arr = JSONArray(it.body?.string().orEmpty())
                        for (i in 0 until arr.length()) {
                            val sender = arr.getJSONObject(i).optString("sender").trim()
                            if (sender.isNotBlank()) { senders.add(sender); val keyName = sender.lowercase(); counts[keyName] = (counts[keyName] ?: 0) + 1 }
                        }
                    }
                    main.post { onResult(UnreadPrivateMessage(senders.size, senders.distinct(), counts)) }
                }
            }
        })
    }

    fun markConversationRead(username: String, buddy: String, onResult: ((Boolean) -> Unit)? = null) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) { main.post { onResult?.invoke(false) }; return }
        val me = URLEncoder.encode(username.lowercase().replace(" ", "_"), "UTF-8")
        val sender = URLEncoder.encode(buddy.lowercase().replace(" ", "_"), "UTF-8")
        val path = "/rest/v1/direct_messages?recipient=eq.$me&sender=eq.$sender&is_read=eq.false"
        val body = JSONObject().put("is_read", true)
        http.newCall(req(path).patch(body.toString().toRequestBody(jsonMediaType)).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult?.invoke(false) } }
            override fun onResponse(call: Call, response: Response) { response.use { main.post { onResult?.invoke(it.isSuccessful) } } }
        })
    }
}
