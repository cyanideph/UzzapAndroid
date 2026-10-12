package com.kolipri.uzzap.data

import com.kolipri.uzzap.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

object NicknameResolver {
    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    fun resolve(username: String, onResult: (String?, String?) -> Unit) {
        val normalized = canonicalizeUsername(username)
        if (normalized.isBlank()) {
            onResult(null, "Nickname is required")
            return
        }
        val payload = JSONObject().put("p_username", normalized).toString()
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/resolve_login_email")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .post(okhttp3.RequestBody.create(null, payload))
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
                        // RPC returns a scalar JSON string or null (not a row array).
                        val email = if (body.trim() == "null") "" else body.trim().removeSurrounding("\"")
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
