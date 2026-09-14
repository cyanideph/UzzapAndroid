package com.kolipri.uzzap.data

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.kolipri.uzzap.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Password recovery flow for the native Android Uzzap client. */
object PasswordRecoveryClient {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    const val RESET_URI = "uzzap://auth/reset"

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val publishableKey = BuildConfig.SUPABASE_ANON_KEY

    fun sendResetEmail(email: String, onResult: (Boolean, String?) -> Unit) {
        val redirect = URLEncoder.encode(RESET_URI, "UTF-8")
        val body = JSONObject().put("email", email.trim())
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/recover?redirect_to=$redirect")
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer $publishableKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        http.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                mainHandler.post { onResult(false, e.localizedMessage ?: "Network error") }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    val message = runCatching {
                        JSONObject(text).optString("message").ifBlank { JSONObject(text).optString("error_description") }
                    }.getOrNull().orEmpty()
                    mainHandler.post {
                        onResult(it.isSuccessful, if (it.isSuccessful) null else message.ifBlank { "Password reset failed (${it.code})" })
                    }
                }
            }
        })
    }

    /** Extracts the recovery access token delivered in the Android deep-link fragment. */
    fun consumeRecoveryIntent(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "uzzap" || uri.host != "auth" || uri.path != "/reset") return false
        val fragment = uri.fragment.orEmpty()
        val values = parseParams(fragment)
        val type = values["type"].orEmpty()
        val access = values["access_token"].orEmpty()
        if (type != "recovery" || access.isBlank()) return false
        val refresh = values["refresh_token"].orEmpty()
        val expiresIn = values["expires_in"]?.toLongOrNull() ?: 3600L
        SupabaseClient.setAccessTokenForLegacy(access)
        AppState.authToken = access
        if (refresh.isNotBlank()) {
            SessionStore.save(access, refresh, System.currentTimeMillis() + expiresIn * 1000L, AppState.email, AppState.nickname)
        }
        return true
    }

    fun updatePassword(accessToken: String, newPassword: String, onResult: (Boolean, String?) -> Unit) {
        val body = JSONObject().put("password", newPassword)
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/user")
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .patch(body.toString().toRequestBody(jsonType))
            .build()
        http.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                mainHandler.post { onResult(false, e.localizedMessage ?: "Network error") }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    val message = runCatching {
                        JSONObject(text).optString("message").ifBlank { JSONObject(text).optString("error_description") }
                    }.getOrNull().orEmpty()
                    mainHandler.post {
                        onResult(it.isSuccessful, if (it.isSuccessful) null else message.ifBlank { "Unable to update password (${it.code})" })
                    }
                }
            }
        })
    }

    private fun parseParams(value: String): Map<String, String> = buildMap {
        value.split('&').forEach { pair ->
            if (pair.isBlank()) return@forEach
            val parts = pair.split('=', limit = 2)
            val key = Uri.decode(parts[0])
            val v = Uri.decode(parts.getOrElse(1) { "" })
            put(key, v)
        }
    }
}
