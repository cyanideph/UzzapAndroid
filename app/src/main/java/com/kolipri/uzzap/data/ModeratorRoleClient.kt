package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.kolipri.uzzap.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Reads the authoritative moderator/admin role from Supabase after authentication. */
object ModeratorRoleClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()

    fun refresh(onResult: (String) -> Unit = {}) {
        val token = SupabaseClient.userAccessToken
        if (token.isNullOrBlank()) {
            AppState.role = "user"
            main.post { onResult(AppState.role) }
            return
        }
        val userId = currentUserId(token)
        if (userId.isNullOrBlank()) {
            AppState.role = "user"
            main.post { onResult(AppState.role) }
            return
        }
        val id = URLEncoder.encode(userId, "UTF-8")
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/moderator_roles?user_id=eq.$id&select=role&limit=1")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                main.post { onResult(AppState.role) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    val role = if (it.isSuccessful) {
                        runCatching { JSONArray(body).optJSONObject(0)?.optString("role") }.getOrNull()
                            ?.takeIf { value -> value == "moderator" || value == "admin" }
                            ?: "user"
                    } else {
                        "user"
                    }
                    AppState.role = role
                    main.post { onResult(role) }
                }
            }
        })
    }

    private fun currentUserId(token: String): String? = runCatching {
        val payload = token.split('.').getOrNull(1) ?: return@runCatching null
        val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        JSONObject(String(bytes, Charsets.UTF_8)).optString("sub").ifBlank { null }
    }.getOrNull()
}
