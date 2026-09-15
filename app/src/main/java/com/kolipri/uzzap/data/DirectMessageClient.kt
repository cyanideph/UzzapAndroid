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
    private fun req(path: String): Request.Builder = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type", "application/json")

    private fun currentUserId(): String? = SupabaseClient.userAccessToken?.let { token -> runCatching { val p = token.split('.').getOrNull(1) ?: return@runCatching null; val bytes = Base64.decode(p, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING); JSONObject(String(bytes, StandardCharsets.UTF_8)).optString("sub").ifBlank { null } }.getOrNull() }

    private data class UserRef(val id: String, val username: String)

    private fun normalizeUsername(username: String): String = username.trim().lowercase().replace(" ", "_")

    private fun resolveUser(username: String, cb: (UserRef?) -> Unit) {
        val u = URLEncoder.encode(normalizeUsername(username), "UTF-8")
        val path = "/rest/v1/profiles?username=eq.$u&select=owner_id,username&limit=1"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { cb(null) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { main.post { cb(null) }; return }
                val value = runCatching {
                    val o = JSONArray(it.body?.string().orEmpty()).optJSONObject(0) ?: return@runCatching null
                    val id = o.optString("owner_id").ifBlank { return@runCatching null }
                    val canonical = o.optString("username").ifBlank { return@runCatching null }
                    UserRef(id, canonical)
                }.getOrNull()
                main.post { cb(value) }
            } }
        })
    }

    private fun resolveCurrentUser(myId: String, cb: (UserRef?) -> Unit) {
        val id = URLEncoder.encode(myId, "UTF-8")
        val path = "/rest/v1/profiles?owner_id=eq.$id&select=owner_id,username&limit=1"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { cb(null) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { main.post { cb(null) }; return }
                val value = runCatching {
                    val o = JSONArray(it.body?.string().orEmpty()).optJSONObject(0) ?: return@runCatching null
                    val canonical = o.optString("username").ifBlank { return@runCatching null }
                    UserRef(o.optString("owner_id").ifBlank { myId }, canonical)
                }.getOrNull()
                main.post { cb(value) }
            } }
        })
    }

    private fun responseError(response: Response): String {
        val body = response.body?.string().orEmpty()
        val detail = runCatching {
            val o = JSONObject(body)
            listOf(o.optString("message"), o.optString("details"), o.optString("hint"))
                .filter { it.isNotBlank() }.joinToString(" — ")
        }.getOrDefault(body)
        return "Private message rejected (${response.code}): ${detail.ifBlank { response.message.ifBlank { "Unknown server error" } }}"
    }

    private fun parse(o: JSONObject, me: String, buddy: String): DirectMessage {
        val sender = o.optString("sender").ifBlank { me }
        val recipient = o.optString("recipient").ifBlank { buddy }
        return DirectMessage(o.optLong("id"), sender, recipient, o.optString("body"), o.optString("created_at"), o.optBoolean("is_read"))
    }

    fun fetchConversation(me: String, buddy: String, afterId: Long = 0, onResult: (List<DirectMessage>?) -> Unit) {
        val myId = currentUserId()
        if (myId.isNullOrBlank() || buddy.isBlank()) { main.post { onResult(null) }; return }
        resolveUser(buddy) { other ->
            if (other == null || other.id == myId) { main.post { onResult(null) }; return@resolveUser }
            val after = if (afterId > 0) "&id=gt.$afterId" else ""
            val or = "(and(sender_id.eq.$myId,recipient_id.eq.${other.id}),and(sender_id.eq.${other.id},recipient_id.eq.$myId))"
            val path = "/rest/v1/direct_messages?or=${URLEncoder.encode(or, "UTF-8")}&select=id,sender,recipient,body,is_read,created_at&order=id.asc&limit=100$after"
            http.newCall(req(path).get().build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { main.post { onResult(null) } }
                override fun onResponse(call: Call, response: Response) { response.use {
                    if (!it.isSuccessful) { main.post { onResult(null) }; return }
                    runCatching { val a = JSONArray(it.body?.string().orEmpty()); List(a.length()) { i -> parse(a.getJSONObject(i), me, buddy) } }
                        .onSuccess { messages -> main.post { onResult(messages) } }
                        .onFailure { main.post { onResult(null) } }
                } }
            })
        }
    }

    fun sendMessage(me: String, buddy: String, body: String, onResult: (DirectMessage?) -> Unit) {
        sendMessage(me, buddy, body, onResult) { main.post { onResult(null) } }
    }

    fun sendMessage(me: String, buddy: String, body: String, onResult: (DirectMessage?) -> Unit, onError: (String) -> Unit) {
        val clean = body.trim()
        val myId = currentUserId()
        if (myId.isNullOrBlank()) { main.post { onError("Your session has expired. Please sign in again.") }; return }
        if (clean.isEmpty()) { main.post { onError("Message is empty.") }; return }
        if (clean.length > 2000) { main.post { onError("Message is too long (maximum 2000 characters).") }; return }
        if (buddy.isBlank()) { main.post { onError("Recipient is missing.") }; return }

        resolveCurrentUser(myId) { senderProfile ->
            if (senderProfile == null) {
                main.post { onError("Your account profile could not be resolved. Please sign out and sign in again.") }
                return@resolveCurrentUser
            }
            resolveUser(buddy) { recipientProfile ->
                if (recipientProfile == null) {
                    main.post { onError("The recipient account could not be found.") }
                    return@resolveUser
                }
                if (recipientProfile.id == myId) {
                    main.post { onError("You cannot send a private message to yourself.") }
                    return@resolveUser
                }

                val jsonBody = JSONObject()
                    .put("sender_id", senderProfile.id)
                    .put("recipient_id", recipientProfile.id)
                    .put("sender", senderProfile.username)
                    .put("recipient", recipientProfile.username)
                    .put("body", clean)
                    .put("is_read", false)

                http.newCall(req("/rest/v1/direct_messages")
                    .header("Prefer", "return=representation")
                    .post(jsonBody.toString().toRequestBody(json))
                    .build()).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        main.post { onError("Could not reach the message server: ${e.message ?: "network error"}") }
                    }
                    override fun onResponse(call: Call, response: Response) { response.use {
                        if (!it.isSuccessful) {
                            val error = responseError(it)
                            main.post { onError(error) }
                            return
                        }
                        runCatching { JSONArray(it.body?.string().orEmpty()).optJSONObject(0) ?: error("Empty response") }
                            .onSuccess { o -> main.post { onResult(parse(o, senderProfile.username, recipientProfile.username)) } }
                            .onFailure { e -> main.post { onError("Message was accepted but the server returned an invalid response: ${e.message ?: "unknown response"}") } }
                    } }
                })
            }
        }
    }
}
