package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.model.ModerationAction
import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

object ModerationAuditClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private fun req(path: String) = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type", "application/json")

    fun fetch(onResult: (List<ModerationAction>?) -> Unit) {
        val path = "/rest/v1/moderation_actions?select=id,moderator_id,target_username,action_type,room_name,reason,expires_at,created_at&order=created_at.desc&limit=200"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { main.post { onResult(null) }; return }
                val result = runCatching {
                    val a = JSONArray(it.body?.string().orEmpty())
                    List(a.length()) { i ->
                        val o = a.getJSONObject(i)
                        ModerationAction(
                            o.optLong("id"),
                            o.optString("moderator_id").ifBlank { null },
                            o.optString("target_username"),
                            o.optString("action_type").ifBlank { "unknown" },
                            o.optString("room_name").ifBlank { null },
                            o.optString("reason"),
                            o.optString("expires_at").ifBlank { null },
                            o.optString("created_at")
                        )
                    }
                }.getOrNull()
                main.post { onResult(result) }
            } }
        })
    }
}
