package com.kolipri.uzzap.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.kolipri.uzzap.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object FcmClient {
    private const val TAG = "UzzapFCM"
    private const val PREFS_NAME = "uzzap_fcm_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_LAST_TOO_MANY_ERROR = "fcm_too_many_reg_timestamp"

    // Throttle retries after TOO_MANY_REGISTRATIONS for at least 1 hour to avoid log spam & hard loops
    private const val TOO_MANY_REG_BACKOFF_MS = 60 * 60 * 1000L

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun saveTokenLocally(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
    }

    fun getLocalToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FCM_TOKEN, null)
    }

    fun initializeAndSync(context: Context) {
        val cachedToken = getLocalToken(context)
        if (!cachedToken.isNullOrBlank()) {
            syncTokenWithSupabase(context, cachedToken)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTooManyError = prefs.getLong(KEY_LAST_TOO_MANY_ERROR, 0L)
        val now = System.currentTimeMillis()
        if (lastTooManyError > 0 && (now - lastTooManyError) < TOO_MANY_REG_BACKOFF_MS) {
            Log.d(TAG, "Skipping FCM token request due to active TOO_MANY_REGISTRATIONS cooldown")
            return
        }

        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    val exc = task.exception
                    val errorMsg = exc?.message ?: "Token retrieval failed"
                    Log.w(TAG, "Fetching FCM registration token failed: $errorMsg", exc)
                    AppDiagnostics.warn(context, "FCM", "Token retrieval failed: $errorMsg")

                    // When Google Play Services reports TOO_MANY_REGISTRATIONS, mark timestamp to avoid repeated attempts
                    if (errorMsg.contains("TOO_MANY_REGISTRATIONS", ignoreCase = true)) {
                        prefs.edit().putLong(KEY_LAST_TOO_MANY_ERROR, System.currentTimeMillis()).apply()
                        Log.i(TAG, "TOO_MANY_REGISTRATIONS detected; clearing stale FCM token registration")
                        runCatching {
                            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { delTask ->
                                if (delTask.isSuccessful) {
                                    AppDiagnostics.info(context, "FCM", "Stale FCM registration reset successfully")
                                    // Reset cooldown once deleted so next session or restart can re-attempt cleanly
                                    prefs.edit().remove(KEY_LAST_TOO_MANY_ERROR).apply()
                                } else {
                                    Log.w(TAG, "FCM deleteToken completed with error", delTask.exception)
                                }
                            }
                        }
                    }
                    return@addOnCompleteListener
                }
                val token = task.result ?: return@addOnCompleteListener
                prefs.edit().remove(KEY_LAST_TOO_MANY_ERROR).apply()
                saveTokenLocally(context, token)
                AppDiagnostics.success("Notifications.FCM.token", "Token acquired")
                syncTokenWithSupabase(context, token)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Exception initiating FCM token retrieval", t)
            AppDiagnostics.exception(context, "FCM", t)
        }
    }

    private fun extractUserId(): String? {
        return SupabaseClient.userAccessToken?.let { token ->
            runCatching {
                val payload = token.split('.').getOrNull(1) ?: return@runCatching null
                val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                JSONObject(String(bytes, StandardCharsets.UTF_8)).optString("sub").ifBlank { null }
            }.getOrNull()
        }
    }

    fun syncTokenWithSupabase(context: Context, token: String? = null) {
        val fcmToken = token ?: getLocalToken(context) ?: return
        val userToken = SupabaseClient.userAccessToken
        val username = AppState.username
        if (userToken.isNullOrBlank() || username.isBlank()) {
            return
        }

        val bodyJson = JSONObject().apply { put("p_token", fcmToken) }

        val request = Request.Builder()
            .url("$url/rest/v1/rpc/upsert_fcm_token")
            .header("apikey", key)
            .header("Authorization", "Bearer $userToken")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Failed to sync FCM token to Supabase", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        Log.d(TAG, "FCM token successfully registered to Supabase")
                    } else {
                        Log.w(TAG, "Supabase FCM token registration response: ${it.code}")
                    }
                }
            }
        })
    }
}
