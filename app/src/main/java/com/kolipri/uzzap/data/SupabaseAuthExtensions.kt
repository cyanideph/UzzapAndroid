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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private object SupabaseAuthCompat {
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    fun request(path: String, authenticated: Boolean): Request.Builder {
        val token = SupabaseClient.userAccessToken
        val bearer = if (authenticated && !token.isNullOrBlank()) "Bearer $token" else "Bearer ${SupabaseClient.anonKey}"
        return Request.Builder().url("${SupabaseClient.supabaseUrl}$path").header("apikey", SupabaseClient.anonKey).header("Authorization", bearer).header("Content-Type", "application/json")
    }
    fun error(body: String, fallback: String): String = runCatching { val o = JSONObject(body); o.optString("error_description").ifBlank { o.optString("message").ifBlank { o.optString("msg").ifBlank { fallback } } } }.getOrDefault(fallback)
    fun post(path: String, body: JSONObject, authenticated: Boolean = false): Request = request(path, authenticated).post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
    fun handler() = handler
    fun client() = client
}

private fun String?.ifNullOrBlank(fallback: String): String = if (isNullOrBlank()) fallback else this
private fun utcNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(Date())
private data class ProfileIdentity(val username: String, val nickname: String, val avatar: String)

internal fun normalizeApplicationRole(value: String?): String = when (value?.trim()?.lowercase(Locale.US)) {
    "admin" -> "admin"
    "moderator" -> "moderator"
    else -> "user"
}

internal fun normalizeRegistrationIdentity(nickname: String): String = nickname.trim().replace(Regex("[\\u0000-\\u001F\\u007F]"), "").replace(Regex("\\s+"), " ").take(40)

internal fun resolveApplicationRole(userId: String, token: String, callback: (String) -> Unit) {
    if (userId.isBlank() || token.isBlank()) { callback("user"); return }
    val encodedUserId = URLEncoder.encode(userId, "UTF-8")
    val request = SupabaseAuthCompat.request("/rest/v1/moderator_roles?select=role&user_id=eq.$encodedUserId&limit=1", true).header("Authorization", "Bearer $token").get().build()
    SupabaseAuthCompat.client().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { Log.w("SupabaseAuthCompat", "Role lookup failed; defaulting to user", e); SupabaseAuthCompat.handler().post { callback("user") } }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) { Log.w("SupabaseAuthCompat", "Role lookup HTTP ${it.code}; defaulting to user"); SupabaseAuthCompat.handler().post { callback("user") }; return }
                val role = runCatching { val arr = JSONArray(raw); normalizeApplicationRole(if (arr.length() == 0) null else arr.getJSONObject(0).optString("role")) }.getOrDefault("user")
                SupabaseAuthCompat.handler().post { callback(role) }
            }
        }
    })
}

fun SupabaseClient.signInWithEmail(email: String, password: String, callback: (Boolean, String?, String?, String?) -> Unit) {
    val body = JSONObject().put("email", email).put("password", password)
    SupabaseAuthCompat.client().newCall(SupabaseAuthCompat.post("/auth/v1/token?grant_type=password", body)).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { SupabaseAuthCompat.handler().post { callback(false, null, null, "Network error: ${e.message ?: "connection failed"}") } }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) { SupabaseAuthCompat.handler().post { callback(false, null, null, SupabaseAuthCompat.error(raw, "Login failed (HTTP ${it.code})")) }; return }
                runCatching {
                    val obj = JSONObject(raw); val access = obj.optString("access_token"); if (access.isBlank()) error("No access token returned")
                    val refresh = obj.optString("refresh_token").ifBlank { null }; val expires = System.currentTimeMillis() + obj.optLong("expires_in", 3600L) * 1000L; val user = obj.optJSONObject("user")
                    val userId = user?.optString("id").orEmpty(); if (userId.isBlank()) error("No authenticated user id returned")
                    SupabaseClient.setAccessTokenForLegacy(access); AppState.authToken = access; AppState.email = user?.optString("email").ifNullOrBlank(email)
                    loadProfileData(userId, access) { profile ->
                        runCatching {
                            val username = profile.username.ifBlank { error("No profile username found for this account") }; val nickname = profile.nickname.ifBlank { username }
                            AppState.username = username; AppState.nickname = nickname; AppState.avatar = profile.avatar
                            resolveApplicationRole(userId, access) { role -> AppState.role = role; SessionStore.save(access, refresh, expires, AppState.email, nickname, role, username); SupabaseClient.restorePersistedSession(); SupabaseAuthCompat.handler().post { callback(true, nickname, access, null) } }
                        }.onFailure { e -> Log.e("SupabaseAuthCompat", "Login identity resolution failed", e); SupabaseClient.setAccessTokenForLegacy(null); SupabaseAuthCompat.handler().post { callback(false, null, null, e.message ?: "Unable to load account profile") } }
                    }
                }.onFailure { e -> Log.e("SupabaseAuthCompat", "Login identity resolution failed", e); SupabaseClient.setAccessTokenForLegacy(null); SupabaseAuthCompat.handler().post { callback(false, null, null, e.message ?: "Unable to load account profile") } }
            }
        }
    })
}

fun SupabaseClient.signUpWithEmail(email: String, password: String, nickname: String, callback: (Boolean, String?, String?) -> Unit) {
    val cleanNickname = normalizeRegistrationIdentity(nickname)
    if (cleanNickname.isBlank()) { SupabaseAuthCompat.handler().post { callback(false, null, "Nickname is required") }; return }
    val cleanEmail = email.trim()
    if (cleanEmail.isBlank()) { SupabaseAuthCompat.handler().post { callback(false, null, "Email is required") }; return }
    val body = JSONObject().put("email", cleanEmail).put("password", password).put("data", JSONObject().put("username", cleanNickname).put("nickname", cleanNickname))
    SupabaseAuthCompat.client().newCall(SupabaseAuthCompat.post("/auth/v1/signup", body)).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { SupabaseAuthCompat.handler().post { callback(false, null, "Network error: ${e.message ?: "connection failed"}") } }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) { SupabaseAuthCompat.handler().post { callback(false, null, SupabaseAuthCompat.error(raw, "Registration failed (HTTP ${it.code})")) }; return }
                runCatching {
                    val obj = JSONObject(raw); val access = obj.optString("access_token"); val user = obj.optJSONObject("user"); val returnedEmail = user?.optString("email").ifNullOrBlank(cleanEmail)
                    if (access.isNotBlank()) {
                        val refresh = obj.optString("refresh_token").ifBlank { null }; val expires = System.currentTimeMillis() + obj.optLong("expires_in", 3600L) * 1000L
                        val userId = user?.optString("id").orEmpty(); if (userId.isBlank()) error("No authenticated user id returned")
                        SupabaseClient.setAccessTokenForLegacy(access); AppState.authToken = access; AppState.email = returnedEmail; AppState.nickname = cleanNickname; AppState.username = cleanNickname; AppState.role = "user"
                        SessionStore.save(access, refresh, expires, returnedEmail, cleanNickname, "user", cleanNickname); SupabaseClient.restorePersistedSession()
                        loadProfileData(userId, access) { profile ->
                            if (profile.username.isBlank()) SupabaseAuthCompat.handler().post { callback(false, null, "Account created but profile provisioning was not completed") }
                            else SupabaseAuthCompat.handler().post { callback(true, access, null) }
                        }
                    } else {
                        SupabaseAuthCompat.handler().post { callback(true, null, "Account created. Please confirm your email, then log in.") }
                    }
                }.onFailure { e -> SupabaseAuthCompat.handler().post { callback(false, null, e.message ?: "Invalid registration response") } }
            }
        }
    })
}

fun SupabaseClient.verifySession(token: String, callback: (Boolean, String?, String?) -> Unit) {
    if (token.isBlank()) { SupabaseAuthCompat.handler().post { callback(false, null, null) }; return }
    withValidSession { ready ->
        if (!ready) { SupabaseAuthCompat.handler().post { callback(false, null, null) }; return@withValidSession }
        val activeToken = userAccessToken ?: token
        val request = SupabaseAuthCompat.request("/auth/v1/user", true).header("Authorization", "Bearer $activeToken").get().build()
        SupabaseAuthCompat.client().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { SupabaseAuthCompat.handler().post { callback(false, null, null) } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty(); if (!it.isSuccessful) { SupabaseAuthCompat.handler().post { callback(false, null, null) }; return }
                    runCatching {
                        val user = JSONObject(raw); val id = user.optString("id"); val email = user.optString("email")
                        loadProfileData(id, activeToken) { profile ->
                            if (profile.username.isNotBlank()) {
                                AppState.username = profile.username; AppState.nickname = profile.nickname.ifBlank { profile.username }
                                resolveApplicationRole(id, activeToken) { role -> AppState.role = role; SessionStore.save(activeToken, SessionStore.refreshToken(), SessionStore.expiresAtMs(), email, AppState.nickname, role, AppState.username); SupabaseAuthCompat.handler().post { callback(true, email, AppState.nickname) } }
                            } else {
                                val saved = SessionStore.username()
                                if (saved.isBlank()) SupabaseAuthCompat.handler().post { callback(false, null, null) }
                                else { AppState.username = saved; AppState.nickname = SessionStore.nickname().ifBlank { AppState.username }; AppState.role = normalizeApplicationRole(SessionStore.role()); SupabaseAuthCompat.handler().post { callback(true, email, AppState.nickname) } }
                            }
                        }
                    }.onFailure { SupabaseAuthCompat.handler().post { callback(false, null, null) } }
                }
            }
        })
    }
}

fun SupabaseClient.upsertProfile(username: String, nickname: String, email: String?, status: String, statusMessage: String, avatar: String, isOnline: Boolean) {
    val token = SupabaseClient.userAccessToken ?: return
    val encodedUsername = URLEncoder.encode(username.trim(), "UTF-8")
    val body = JSONObject().put("nickname", nickname).put("status", status).put("status_message", statusMessage).put("avatar", avatar).put("is_online", isOnline).put("last_seen", utcNow())
    if (!email.isNullOrBlank()) body.put("email", email)
    val request = SupabaseAuthCompat.request("/rest/v1/profiles?username=eq.$encodedUsername", true).header("Authorization", "Bearer $token").header("Prefer", "return=minimal").patch(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
    SupabaseAuthCompat.client().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { Log.w("SupabaseAuthCompat", "Profile sync failed", e) }
        override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) Log.w("SupabaseAuthCompat", "Profile sync HTTP ${it.code}") } }
    })
}

fun SupabaseClient.signOut() {
    val token = SupabaseClient.userAccessToken
    if (!token.isNullOrBlank()) SupabaseAuthCompat.client().newCall(SupabaseAuthCompat.request("/auth/v1/logout", true).header("Authorization", "Bearer $token").post("".toRequestBody("application/json".toMediaType())).build()).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) { Log.w("SupabaseAuthCompat", "Logout request failed", e) }; override fun onResponse(call: Call, response: Response) { response.close() } })
    SupabaseClient.setAccessTokenForLegacy(null); AppState.authToken = ""; AppState.username = ""; AppState.nickname = ""; AppState.email = ""; AppState.role = "user"; SessionStore.clear()
}

private fun loadProfileData(userId: String, token: String = SupabaseClient.userAccessToken.orEmpty(), onResult: (ProfileIdentity) -> Unit) {
    if (userId.isBlank() || token.isBlank()) { SupabaseAuthCompat.handler().post { onResult(ProfileIdentity("", "", "")) }; return }
    val request = SupabaseAuthCompat.request("/rest/v1/profiles?select=username,nickname,avatar&owner_id=eq.$userId", true).header("Authorization", "Bearer $token").get().build()
    SupabaseAuthCompat.client().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { Log.w("SupabaseAuthCompat", "Profile identity lookup failed", e); SupabaseAuthCompat.handler().post { onResult(ProfileIdentity("", "", "")) } }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!it.isSuccessful) { SupabaseAuthCompat.handler().post { onResult(ProfileIdentity("", "")) }; return }
                val arr = JSONArray(it.body?.string().orEmpty()); val p = if (arr.length() == 0) null else arr.getJSONObject(0)
                SupabaseAuthCompat.handler().post { onResult(if (p == null) ProfileIdentity("", "") else ProfileIdentity(p.optString("username").trim(), p.optString("nickname").trim(), p.optString("avatar").trim())) }
            }
        }
    })
}
