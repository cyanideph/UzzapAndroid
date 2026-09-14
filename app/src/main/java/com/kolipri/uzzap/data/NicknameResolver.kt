package com.kolipri.uzzap.data

import com.kolipri.uzzap.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder

object NicknameResolver {
    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    fun resolve(username: String, onResult: (String?, String?) -> Unit) {
        val normalized = username.trim().lowercase().replace(" ", "_")
        if (normalized.isBlank()) {
            onResult(null, "Nickname is required")
            return
        }
        val encoded = URLEncoder.encode(normalized, "UTF-8")
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/profiles?username=eq.$encoded&select=email&limit=1")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(null, e.localizedMessage ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        onResult(null, "Unable to find that nickname")
                        return
                    }
                    try {
                        val rows = JSONArray(body)
                        val email = if (rows.length() > 0) rows.getJSONObject(0).optString("email").trim() else ""
                        if (email.isBlank()) onResult(null, "That nickname has no registered email account")
                        else onResult(email, null)
                    } catch (_: Exception) {
                        onResult(null, "Invalid account lookup response")
                    }
                }
            }
        })
    }
}
