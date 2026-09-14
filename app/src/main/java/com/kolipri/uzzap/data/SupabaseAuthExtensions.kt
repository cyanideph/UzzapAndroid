package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kolipri.uzzap.model.Presence
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private object SupabaseAuthCompat {
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun request(path: String, authenticated: Boolean): Request.Builder {
        val token = SupabaseClient.userAccessToken
        val bearer = if (authenticated && !token.isNullOrBlank()) "Bearer $token" else "Bearer ${SupabaseClient.anonKey}"
        return Request.Builder()
            .url("${SupabaseClient.supabaseUrl}$path")
            .header("apikey", SupabaseClient.anonKey)
            .header("Authorization", bearer)
            .header("Content-Type", "application/json")
    }

    fun error(body: String, fallback: String): String = runCatching {
        val o = JSONObject(body)
        o.optString("error_description").ifBlank {
            o.optString("message").ifBlank { o.optString("msg").ifBlank { fallback } }
        }
    }.getOrDefault(fallback)

    fun post(path: String, body: JSONObject, authenticated: Boolean = false): Request =
        request(path, authenticated).post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()

    fun handler() = handler
    fun client() = client
}

private fun String?.ifNullOrBlank(fallback: String): String = if (isNullOrBlank()) fallback else this

private fun utcNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = java.util.TimeZone.getTimeZone("UTC")
}.format(Date())

fun SupabaseClient.signInWithEmail(email: String, password: String, callback: (Boolean, String?, String?, String?) -> Unit) {
    val body = JSONObject().put("email", email).put("password", password)
    SupabaseAuthCompat.client().newCall(SupabaseAuthCompat.post("/auth/v1/token?grant_type=password", body)).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            SupabaseAuthCompat.handler().post { callback(false, null, null, "Network error: ${e.message ?: "connection failed"}") }
        }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    SupabaseAuthCompat.handler().post { callback(false, null, null, SupabaseAuthCompat.error(raw, "Login failed (HTTP ${it.code})")) }
                    return
                }
                runCatching {
                    val obj = JSONObject(raw)
                    val access = obj.optString("access_token")
                    if (access.isBlank()) error("No access token returned")
                    val refresh = obj.optString("refresh_token").ifBlank { null }
                    val expires = System.currentTimeMillis() + obj.optLong("expires_in", 3600L) * 1000L
                    val user = obj.optJSONObject("user")
                    val userId = user?.optString("id").orEmpty()
                    SupabaseClient.setAccessTokenForLegacy(access)
                    AppState.authToken = access
                    AppState.email = user?.optString("email").ifNullOrBlank(email)
                    val nickname = loadNickname(userId).ifBlank { AppState.nickname }
                    AppState.nickname = nickname
                    AppState.username = nickname.lowercase().replace(" ", "_")
                    SessionStore.save(access, refresh, expires, AppState.email, nickname)
                    SupabaseAuthCompat.handler().post { callback(true, nickname, access, null) }
                }.onFailure { e ->
                    Log.e("SupabaseAuthCompat", "Login parse failed", e)
                    SupabaseAuthCompat.handler().post { callback(false, null, null, e.message ?: "Invalid login response") }
                }
            }
        }
    })
}

fun SupabaseClient.signUpWithEmail(email: String, password: String, nickname: String, callback: (Boolean, String?, String?) -> Unit) {
    val body = JSONObject().put("email", email).put("password", password).put("data", JSONObject().put("username", nickname).put("nickname", nickname))
    SupabaseAuthCompat.client().newCall(SupabaseAuthCompat.post("/auth/v1/signup", body)).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            SupabaseAuthCompat.handler().post { callback(false, null, "Network error: ${e.message ?: "connection failed"}") }
        }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    SupabaseAuthCompat.handler().post { callback(false, null, SupabaseAuthCompat.error(raw, "Registration failed (HTTP ${it.code})")) }
                    return
                }
                runCatching {
                    val obj = JSONObject(raw)
                    val access = obj.optString("access_token")
                    val user = obj.optJSONObject("user")
                    val returnedEmail = user?.optString("email").ifNullOrBlank(email)
                    if (access.isNotBlank()) {
                        val refresh = obj.optString("refresh_token").ifBlank { null }
                        val expires = System.currentTimeMillis() + obj.optLong("expires_in", 3600L) * 1000L
                        SupabaseClient.setAccessTokenForLegacy(access)
                        AppState.authToken = access
                        AppState.email = returnedEmail
                        AppState.nickname = nickname
                        AppState.username = nickname.lowercase().replace(" ", "_")
                        SessionStore.save(access, refresh, expires, returnedEmail, nickname)
                        SupabaseClient.upsertProfile(nickname, nickname, returnedEmail, Presence.INVISIBLE.label, "", "kool", false)
                        SupabaseAuthCompat.handler().post { callback(true, access, null) }
                    } else {
                        SupabaseAuthCompat.handler().post { callback(false, null, "Account created. Please confirm your email, then log in.") }
                    }
                }.onFailure { e -> SupabaseAuthCompat.handler().post { callback(false, null, e.message ?: "Invalid registration response") } }
            }
        }
    })
}

fun SupabaseClient.verifySession(token: String, callback: (Boolean, String?, String?) -> Unit) {
    if (token.isBlank()) { SupabaseAuthCompat.handler().post { callback(false, null, null) }; return }
    val request = SupabaseAuthCompat.request("/auth/v1/user", true).header("Authorization", "Bearer $token").get().build()
    SupabaseAuthCompat.client().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { SupabaseAuthCompat.handler().post { callback(false, null, null) } }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) { SupabaseAuthCompat.handler().post { callback(false, null, null) }; return }
                runCatching {
                    val user = JSONObject(raw)
                    val id = user.optString("id")
                    val email = user.optString("email")
                    val profileRequest = SupabaseAuthCompat.request("/rest/v1/profiles?select=username,nickname,email&id=eq.$id", true).header("Authorization", "Bearer $token").get().build()
                    SupabaseAuthCompat.client().newCall(profileRequest).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) { SupabaseAuthCompat.handler().post { callback(true, email, null) } }
                        override fun onResponse(call: Call, profileResponse: Response) {
                            profileResponse.use {
                                var nick: String? = null
                                if (it.isSuccessful) runCatching {
                                    val arr = org.json.JSONArray(it.body?.string().orEmpty())
                                    if (arr.length() > 0) {
                                        val p = arr.getJSONObject(0)
                                        nick = p.optString("nickname").ifBlank { p.optString("username") }.ifBlank { null }
                                    }
                                }
                                SupabaseAuthCompat.handler().post { callback(true, email, nick) }
                            }
                        }
                    })
                }.onFailure { SupabaseAuthCompat.handler().post { callback(false, null, null) } }
            }
        }
    })
}

fun SupabaseClient.upsertProfile(username: String, nickname: String, email: String?, status: String, statusMessage: String, avatar: String, isOnline: Boolean) {
    val token = SupabaseClient.userAccessToken ?: return
    val body = JSONObject().put("username", username).put("nickname", nickname).put("status", status).put("status_message", statusMessage).put("avatar", avatar).put("is_online", isOnline).put("last_seen", utcNow())
    if (!email.isNullOrBlank()) body.put("email", email)
    SupabaseAuthCompat.client().newCall(SupabaseAuthCompat.request("/rest/v1/profiles?on_conflict=id", true).header("Authorization", "Bearer $token").header("Prefer", "resolution=merge-duplicates").post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { Log.w("SupabaseAuthCompat", "Profile sync failed", e) }
        override fun onResponse(call: Call, response: Response) { response.close() }
    })
}

fun SupabaseClient.signOut() {
    val token = SupabaseClient.userAccessToken
    if (!token.isNullOrBlank()) {
        SupabaseAuthCompat.client().newCall(SupabaseAuthCompat.request("/auth/v1/logout", true).header("Authorization", "Bearer $token").post("".toRequestBody("application/json".toMediaType())).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { Log.w("SupabaseAuthCompat", "Logout request failed", e) }
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
    SupabaseClient.setAccessTokenForLegacy(null)
    AppState.authToken = ""
    SessionStore.clear()
}

private fun loadNickname(userId: String): String {
    if (userId.isBlank()) return ""
    return try {
        val request = SupabaseAuthCompat.request("/rest/v1/profiles?select=username,nickname&id=eq.$userId", true).get().build()
        val response = SupabaseAuthCompat.client().newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return ""
            val arr = org.json.JSONArray(it.body?.string().orEmpty())
            if (arr.length() == 0) return ""
            val p = arr.getJSONObject(0)
            p.optString("nickname").ifBlank { p.optString("username") }
        }
    } catch (_: Exception) { "" }
}
