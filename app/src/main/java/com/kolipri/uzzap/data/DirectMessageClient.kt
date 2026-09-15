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

    private fun resolveUser(username: String, cb: (String?) -> Unit) {
        val u = URLEncoder.encode(username.trim().lowercase().replace(" ", "_"), "UTF-8")
        val path = "/rest/v1/profiles?username=eq.$u&select=owner_id,username&limit=1"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { cb(null) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { main.post { cb(null) }; return }
                val value = runCatching { JSONArray(it.body?.string().orEmpty()).optJSONObject(0)?.optString("owner_id")?.ifBlank { null } }.getOrNull()
                main.post { cb(value) }
            } }
        })
    }

    private fun parse(o: JSONObject, me: String, buddy: String): DirectMessage {
        val sender = o.optString("sender").ifBlank { me }
        val recipient = o.optString("recipient").ifBlank { buddy }
        return DirectMessage(o.optLong("id"), sender, recipient, o.optString("body"), o.optString("created_at"), o.optBoolean("is_read"))
    }

    fun fetchConversation(me: String, buddy: String, afterId: Long = 0, onResult: (List<DirectMessage>?) -> Unit) {
        val myId = currentUserId()
        if (myId.isNullOrBlank() || buddy.isBlank()) { main.post { onResult(null) }; return }
        resolveUser(buddy) { otherId ->
            if (otherId.isNullOrBlank()) { main.post { onResult(null) }; return@resolveUser }
            val after = if (afterId > 0) "&id=gt.$afterId" else ""
            val or = "(and(sender_id.eq.$myId,recipient_id.eq.$otherId),and(sender_id.eq.$otherId,recipient_id.eq.$myId))"
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
        val clean = body.trim()
        val myId = currentUserId()
        if (myId.isNullOrBlank() || clean.isEmpty() || clean.length > 2000 || buddy.isBlank()) { main.post { onResult(null) }; return }
        resolveUser(buddy) { otherId ->
            if (otherId.isNullOrBlank() || otherId == myId) { main.post { onResult(null) }; return@resolveUser }
            val sender = me.trim().lowercase().replace(" ", "_")
            val recipient = buddy.trim().lowercase().replace(" ", "_")
            val jsonBody = JSONObject().put("sender_id", myId).put("recipient_id", otherId).put("sender", sender).put("recipient", recipient).put("body", clean).put("is_read", false)
            http.newCall(req("/rest/v1/direct_messages").header("Prefer", "return=representation").post(jsonBody.toString().toRequestBody(json)).build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { main.post { onResult(null) } }
                override fun onResponse(call: Call, response: Response) { response.use {
                    if (!it.isSuccessful) { main.post { onResult(null) }; return }
                    runCatching { JSONArray(it.body?.string().orEmpty()).optJSONObject(0) ?: error("Empty response") }
                        .onSuccess { o -> main.post { onResult(parse(o, me, buddy)) } }
                        .onFailure { main.post { onResult(null) } }
                } }
            })
        }
    }
}
