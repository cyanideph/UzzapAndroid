package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.kolipri.uzzap.BuildConfig
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class UnreadPrivateMessage(
    val count: Int,
    val senders: List<String>,
    val countsBySender: Map<String, Int> = emptyMap(),
    val messageIds: List<Long> = emptyList()
)

object PrivateMessageStateClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).writeTimeout(12, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val json = "application/json; charset=utf-8".toMediaType()
    private fun req(path: String) = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type", "application/json")
    private fun currentUserId(): String? = SupabaseClient.userAccessToken?.let { token -> runCatching { val p = token.split('.').getOrNull(1) ?: return@runCatching null; val bytes = Base64.decode(p, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING); JSONObject(String(bytes, StandardCharsets.UTF_8)).optString("sub").ifBlank { null } }.getOrNull() }

    fun fetchUnread(username: String, onResult: (UnreadPrivateMessage?) -> Unit) {
        val me = currentUserId()
        if (me.isNullOrBlank()) { main.post { onResult(null) }; return }
        val path = "/rest/v1/direct_messages?recipient_id=eq.$me&is_read=is.false&select=id,sender&order=id.asc&limit=500"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { main.post { onResult(null) }; return }
                runCatching {
                    val array = JSONArray(it.body?.string().orEmpty())
                    val senders = mutableListOf<String>()
                    val counts = mutableMapOf<String, Int>()
                    val ids = mutableListOf<Long>()
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val sender = item.optString("sender").trim()
                        val id = item.optLong("id", -1L)
                        if (sender.isNotBlank()) {
                            senders += sender
                            val key = sender.lowercase()
                            counts[key] = (counts[key] ?: 0) + 1
                        }
                        if (id > 0) ids += id
                    }
                    UnreadPrivateMessage(senders.size, senders.distinct(), counts, ids)
                }.onSuccess { result -> main.post { onResult(result) } }.onFailure { main.post { onResult(null) } }
            } }
        })
    }

    fun markConversationRead(username: String, buddy: String, onResult: ((Boolean) -> Unit)? = null) {
        val me = currentUserId()
        if (me.isNullOrBlank() || buddy.isBlank()) { main.post { onResult?.invoke(false) }; return }
        val encodedBuddy = URLEncoder.encode(buddy.trim().lowercase().replace(" ", "_"), "UTF-8")
        val lookup = "/rest/v1/profiles?username=eq.$encodedBuddy&select=owner_id&limit=1"
        http.newCall(req(lookup).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult?.invoke(false) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { main.post { onResult?.invoke(false) }; return }
                val other = runCatching { JSONArray(it.body?.string().orEmpty()).optJSONObject(0)?.optString("owner_id")?.ifBlank { null } }.getOrNull()
                if (other.isNullOrBlank()) { main.post { onResult?.invoke(false) }; return }
                val path = "/rest/v1/direct_messages?recipient_id=eq.$me&sender_id=eq.$other&is_read=is.false"
                val body = JSONObject().put("is_read", true)
                http.newCall(req(path).header("Prefer", "return=minimal").patch(body.toString().toRequestBody(json)).build()).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { main.post { onResult?.invoke(false) } }
                    override fun onResponse(call: Call, response: Response) { response.use { main.post { onResult?.invoke(it.isSuccessful) } } }
                })
            } }
        })
    }
}
