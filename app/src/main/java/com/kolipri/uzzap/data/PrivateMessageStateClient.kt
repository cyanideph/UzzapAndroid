package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import com.kolipri.uzzap.BuildConfig
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UnreadPrivateMessage(
    val count: Int,
    val senders: List<String>,
    val countsBySender: Map<String, Int> = emptyMap()
)

object PrivateMessageStateClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).writeTimeout(12, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun request(path: String): Request.Builder = Request.Builder()
        .url("$url$path")
        .header("apikey", key)
        .header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}")
        .header("Content-Type", "application/json")

    private fun rpc(name: String, body: JSONObject, callback: Callback) {
        http.newCall(request("/rest/v1/rpc/$name").post(body.toString().toRequestBody(json)).build()).enqueue(callback)
    }

    fun fetchUnread(username: String, onResult: (UnreadPrivateMessage?) -> Unit) {
        rpc("get_unread_private_messages", JSONObject(), object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post { onResult(null) }
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        main.post { onResult(null) }
                        return
                    }
                    runCatching {
                        val array = JSONArray(it.body?.string().orEmpty())
                        val senders = mutableListOf<String>()
                        val counts = mutableMapOf<String, Int>()
                        for (i in 0 until array.length()) {
                            val sender = array.getJSONObject(i).optString("sender").trim()
                            if (sender.isNotBlank()) {
                                senders += sender
                                val name = sender.lowercase()
                                counts[name] = (counts[name] ?: 0) + 1
                            }
                        }
                        UnreadPrivateMessage(senders.size, senders.distinct(), counts)
                    }.onSuccess { result -> main.post { onResult(result) } }
                        .onFailure { main.post { onResult(null) } }
                }
            }
        })
    }

    fun markConversationRead(username: String, buddy: String, onResult: ((Boolean) -> Unit)? = null) {
        resolveBuddy(buddy) { uid ->
            if (uid == null) {
                main.post { onResult?.invoke(false) }
                return@resolveBuddy
            }
            rpc("mark_direct_messages_read", JSONObject().put("p_other_user", uid), object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    main.post { onResult?.invoke(false) }
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use { success -> main.post { onResult?.invoke(success.isSuccessful) } }
                }
            })
        }
    }

    private fun resolveBuddy(username: String, onResult: (String?) -> Unit) {
        rpc("get_user_id_by_username", JSONObject().put("p_username", username), object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post { onResult(null) }
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        main.post { onResult(null) }
                        return
                    }
                    val value = it.body?.string()?.trim()?.removeSurrounding("\"")?.ifBlank { null }
                    main.post { onResult(value) }
                }
            }
        })
    }
}
