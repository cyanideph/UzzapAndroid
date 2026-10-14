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
    private const val UNREAD_CACHE_MAX_AGE_MS = 30_000L

    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).writeTimeout(12, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val json = "application/json; charset=utf-8".toMediaType()

    @Volatile private var cachedUserId: String? = null
    @Volatile private var cachedUnread: UnreadPrivateMessage? = null
    @Volatile private var cachedAtMs = 0L
    private val unreadLock = Any()
    private val pendingUnreadCallbacks = mutableListOf<(UnreadPrivateMessage?) -> Unit>()
    private var unreadRequestInFlight = false
    private var unreadGeneration = 0L

    private fun req(path: String) = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", SupabaseClient.authHeader()).header("Content-Type", "application/json")
    private fun currentUserId(): String? = SupabaseClient.userAccessToken?.let { token -> runCatching { val p = token.split('.').getOrNull(1) ?: return@runCatching null; val bytes = Base64.decode(p, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING); JSONObject(String(bytes, StandardCharsets.UTF_8)).optString("sub").ifBlank { null } }.getOrNull() }

    fun fetchUnread(username: String, onResult: (UnreadPrivateMessage?) -> Unit) {
        val me = currentUserId()
        if (me.isNullOrBlank()) {
            main.post { onResult(null) }
            return
        }

        var startRequest = false
        synchronized(unreadLock) {
            if (cachedUserId == me && cachedUnread != null && System.currentTimeMillis() - cachedAtMs < UNREAD_CACHE_MAX_AGE_MS) {
                val result = cachedUnread
                main.post { onResult(result) }
                return
            }

            pendingUnreadCallbacks += onResult
            if (!unreadRequestInFlight) {
                unreadRequestInFlight = true
                startRequest = true
            }
        }
        if (startRequest) startUnreadRequest(me)
    }

    private fun startUnreadRequest(userId: String) {
        val requestGeneration = synchronized(unreadLock) { unreadGeneration }
        val path = "/rest/v1/direct_messages?recipient_id=eq.$userId&is_read=is.false&select=id,sender&order=id.asc&limit=500"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                completeUnreadRequest(userId, requestGeneration, null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        completeUnreadRequest(userId, requestGeneration, null)
                        return
                    }
                    val result = runCatching {
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
                                val senderKey = sender.lowercase()
                                counts[senderKey] = (counts[senderKey] ?: 0) + 1
                            }
                            if (id > 0) ids += id
                        }
                        UnreadPrivateMessage(senders.size, senders.distinct(), counts, ids)
                    }.getOrNull()
                    completeUnreadRequest(userId, requestGeneration, result)
                }
            }
        })
    }

    private fun completeUnreadRequest(userId: String, requestGeneration: Long, result: UnreadPrivateMessage?) {
        var restart = false
        val callbacks: List<(UnreadPrivateMessage?) -> Unit>
        synchronized(unreadLock) {
            if (requestGeneration != unreadGeneration) {
                // A read operation invalidated this request while it was in flight.
                // Do not publish or cache stale unread state; immediately re-query.
                restart = pendingUnreadCallbacks.isNotEmpty()
                unreadRequestInFlight = restart
                callbacks = emptyList()
            } else {
                if (result != null) {
                    cachedUserId = userId
                    cachedUnread = result
                    cachedAtMs = System.currentTimeMillis()
                }
                callbacks = pendingUnreadCallbacks.toList()
                pendingUnreadCallbacks.clear()
                unreadRequestInFlight = false
            }
        }
        if (restart) {
            startUnreadRequest(userId)
            return
        }
        callbacks.forEach { callback -> main.post { callback(result) } }
    }

    private fun invalidateUnreadCache() {
        synchronized(unreadLock) {
            unreadGeneration++
            cachedUnread = null
            cachedAtMs = 0L
        }
    }

    fun markConversationRead(username: String, buddy: String, onResult: ((Boolean) -> Unit)? = null) {
        val me = currentUserId()
        if (me.isNullOrBlank() || buddy.isBlank()) { main.post { onResult?.invoke(false) }; return }
        val wanted = buddy.trim()
        val encodedBuddy = URLEncoder.encode(wanted, "UTF-8")
        val lookup = "/rest/v1/profiles?username=ilike.$encodedBuddy&select=owner_id,username&limit=10"
        http.newCall(req(lookup).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult?.invoke(false) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { main.post { onResult?.invoke(false) }; return }
                val other = runCatching {
                    val arr = JSONArray(it.body?.string().orEmpty())
                    (0 until arr.length())
                        .mapNotNull { i -> arr.optJSONObject(i) }
                        .firstOrNull { o -> matchesHandle(o.optString("username"), wanted) }
                        ?.optString("owner_id")?.ifBlank { null }
                }.getOrNull()
                if (other.isNullOrBlank()) { main.post { onResult?.invoke(false) }; return }
                val path = "/rest/v1/direct_messages?recipient_id=eq.$me&sender_id=eq.$other&is_read=is.false"
                val body = JSONObject().put("is_read", true)
                http.newCall(req(path).header("Prefer", "return=minimal").patch(body.toString().toRequestBody(json)).build()).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { main.post { onResult?.invoke(false) } }
                    override fun onResponse(call: Call, response: Response) { response.use {
                        if (it.isSuccessful) invalidateUnreadCache()
                        main.post { onResult?.invoke(it.isSuccessful) }
                    } }
                })
            } }
        })
    }
}
