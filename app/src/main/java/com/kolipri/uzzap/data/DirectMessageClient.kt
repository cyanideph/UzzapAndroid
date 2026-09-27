package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.model.DirectMessage
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object DirectMessageClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val json = "application/json; charset=utf-8".toMediaType()
    private fun req(path: String): Request.Builder = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", SupabaseClient.authHeader()).header("Content-Type", "application/json")

    private fun currentUserId(): String? = SupabaseClient.userAccessToken?.let { token -> runCatching { val p = token.split('.').getOrNull(1) ?: return@runCatching null; val bytes = Base64.decode(p, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING); JSONObject(String(bytes, StandardCharsets.UTF_8)).optString("sub").ifBlank { null } }.getOrNull() }
    private data class UserRef(val id: String, val username: String)
    private fun normalizeUsername(username: String): String = username.trim().lowercase().replace(" ", "_")

    private fun resolveUser(username: String, cb: (UserRef?) -> Unit) {
        val u = URLEncoder.encode(normalizeUsername(username), "UTF-8")
        val path = "/rest/v1/profiles?username=eq.$u&select=owner_id,username&limit=1"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { cb(null) } }
            override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { main.post { cb(null) }; return }; val value = runCatching { val o = JSONArray(it.body?.string().orEmpty()).optJSONObject(0) ?: return@runCatching null; val id = o.optString("owner_id").ifBlank { return@runCatching null }; val canonical = o.optString("username").ifBlank { return@runCatching null }; UserRef(id, canonical) }.getOrNull(); main.post { cb(value) } } }
        })
    }

    private fun resolveCurrentUser(myId: String, cb: (UserRef?) -> Unit) {
        val id = URLEncoder.encode(myId, "UTF-8")
        val path = "/rest/v1/profiles?owner_id=eq.$id&select=owner_id,username&limit=1"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { cb(null) } }
            override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { main.post { cb(null) }; return }; val value = runCatching { val o = JSONArray(it.body?.string().orEmpty()).optJSONObject(0) ?: return@runCatching null; val canonical = o.optString("username").ifBlank { return@runCatching null }; UserRef(o.optString("owner_id").ifBlank { myId }, canonical) }.getOrNull(); main.post { cb(value) } } }
        })
    }

    private fun responseError(response: Response): String {
        val body = response.body?.string().orEmpty()
        val detail = runCatching { val o = JSONObject(body); listOf(o.optString("message"), o.optString("details"), o.optString("hint")).filter { it.isNotBlank() }.joinToString(" — ") }.getOrDefault(body)
        return "Private message rejected (${response.code}): ${detail.ifBlank { response.message.ifBlank { "Unknown server error" } }}"
    }

    private fun parse(o: JSONObject, me: String, buddy: String): DirectMessage = DirectMessage(o.optLong("id"), o.optString("sender").ifBlank { me }, o.optString("recipient").ifBlank { buddy }, o.optString("body"), o.optString("created_at"), o.optBoolean("is_read"))

    fun fetchConversation(me: String, buddy: String, afterId: Long = 0, onResult: (List<DirectMessage>?) -> Unit) {
        fetchConversationInternal(me, buddy, afterId, false, onResult)
    }

    /** Loads the page immediately preceding beforeId, oldest first for UI insertion. */
    fun fetchConversationBefore(me: String, buddy: String, beforeId: Long, limit: Int = 50, onResult: (List<DirectMessage>?) -> Unit) {
        fetchConversationInternal(me, buddy, beforeId, true, onResult, limit)
    }

    private fun fetchConversationInternal(me: String, buddy: String, cursorId: Long, before: Boolean, onResult: (List<DirectMessage>?) -> Unit, limit: Int = 100) {
        AppDiagnostics.started("PrivateMessaging.fetchConversation", "buddy=${buddy.take(40)} cursor=$cursorId before=$before")
        val myId = currentUserId()
        if (myId.isNullOrBlank() || buddy.isBlank()) { main.post { onResult(null) }; return }
        resolveUser(buddy) { other ->
            if (other == null || other.id == myId) { main.post { onResult(null) }; return@resolveUser }
            val cursor = if (cursorId > 0) if (before) "&id=lt.$cursorId" else "&id=gt.$cursorId" else ""
            val or = "(and(sender_id.eq.$myId,recipient_id.eq.${other.id}),and(sender_id.eq.${other.id},recipient_id.eq.$myId))"
            val order = if (before) "id.desc" else "id.asc"
            val path = "/rest/v1/direct_messages?or=${URLEncoder.encode(or, "UTF-8")}&select=id,sender,recipient,body,is_read,created_at&order=$order&limit=${limit.coerceIn(1,100)}$cursor"
            http.newCall(req(path).get().build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { main.post { onResult(null) } }
                override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { responseError(it); main.post { onResult(null) }; return }; runCatching { val a = JSONArray(it.body?.string().orEmpty()); val result = List(a.length()) { i -> parse(a.getJSONObject(i), me, buddy) }; if (before) result.reversed() else result }.onSuccess { main.post { onResult(it) } }.onFailure { main.post { onResult(null) } } } }
            })
        }
    }

    fun sendMessage(me: String, buddy: String, body: String, onResult: (DirectMessage?) -> Unit) {
        sendMessage(me, buddy, body, onResult) { main.post { onResult(null) } }
    }

    /** Retries transient/network failures with exponential backoff. Server validation/auth failures are not retried. */
    fun sendMessageWithRetry(me: String, buddy: String, body: String, maxAttempts: Int = 4, onResult: (DirectMessage?) -> Unit, onError: (String) -> Unit) {
        var attempt = 0
        fun attemptSend() {
            attempt++
            sendMessage(me, buddy, body, { result ->
                if (result != null) onResult(result) else if (attempt < maxAttempts) main.postDelayed({ attemptSend() }, (1L shl attempt).coerceAtMost(8L) * 1000L) else onError("Message could not be sent after $attempt attempts.")
            }, { error ->
                val transient = error.contains("Could not reach", true) || error.contains("timeout", true) || error.contains("network", true) || error.contains("connection", true)
                if (transient && attempt < maxAttempts) main.postDelayed({ attemptSend() }, (1L shl attempt).coerceAtMost(8L) * 1000L) else onError(error)
            })
        }
        attemptSend()
    }

    fun sendMessage(me: String, buddy: String, body: String, onResult: (DirectMessage?) -> Unit, onError: (String) -> Unit) {
        val function = "PrivateMessaging.sendMessage"
        val clean = body.trim(); val myId = currentUserId()
        fun fail(message: String) { AppDiagnostics.failed(function, message); main.post { onError(message) } }
        if (myId.isNullOrBlank()) { fail("Your session has expired. Please sign in again."); return }
        if (clean.isEmpty()) { fail("Message is empty."); return }
        if (clean.length > 2000) { fail("Message is too long (maximum 2000 characters)."); return }
        if (buddy.isBlank()) { fail("Recipient is missing."); return }
        resolveCurrentUser(myId) { senderProfile ->
            if (senderProfile == null) { fail("Your account profile could not be resolved."); return@resolveCurrentUser }
            resolveUser(buddy) { recipientProfile ->
                if (recipientProfile == null) { fail("The recipient account could not be found."); return@resolveUser }
                if (recipientProfile.id == myId) { fail("You cannot send a private message to yourself."); return@resolveUser }
                val jsonBody = JSONObject().put("sender_id", senderProfile.id).put("recipient_id", recipientProfile.id).put("sender", senderProfile.username).put("recipient", recipientProfile.username).put("body", clean).put("is_read", false)
                http.newCall(req("/rest/v1/direct_messages").header("Prefer", "return=representation").post(jsonBody.toString().toRequestBody(json)).build()).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { fail("Could not reach the message server: ${e.message ?: "network error"}") }
                    override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { fail(responseError(it)); return }; runCatching { JSONArray(it.body?.string().orEmpty()).optJSONObject(0) ?: error("Empty response") }.onSuccess { o -> main.post { onResult(parse(o, senderProfile.username, recipientProfile.username)) } }.onFailure { e -> fail("Server returned invalid response: ${e.message ?: "unknown response"}") } } }
                })
            }
        }
    }

    /** Returns recent conversation partners from the authenticated user's own messages. */
    fun fetchRecentConversationPartners(onResult: (List<String>) -> Unit) {
        val myId = currentUserId()
        if (myId.isNullOrBlank()) { main.post { onResult(emptyList()) }; return }
        val encoded = URLEncoder.encode(myId, "UTF-8")
        val or = URLEncoder.encode("(sender_id.eq.$myId,recipient_id.eq.$myId)", "UTF-8")
        val path = "/rest/v1/direct_messages?or=$or&select=sender,recipient,created_at&order=created_at.desc&limit=100"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(emptyList()) } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { main.post { onResult(emptyList()) }; return }
                    val partners = runCatching {
                        val array = JSONArray(it.body?.string().orEmpty())
                        buildList {
                            for (i in 0 until array.length()) {
                                val o = array.optJSONObject(i) ?: continue
                                val sender = o.optString("sender").trim()
                                val recipient = o.optString("recipient").trim()
                                val partner = if (sender.equals(AppState.username, true)) recipient else sender
                                if (partner.isNotBlank() && !partner.equals(AppState.username, true) && none { it.equals(partner, true) }) add(partner)
                            }
                        }
                    }.getOrDefault(emptyList())
                    main.post { onResult(partners) }
                }
            }
        })
    }

}
