package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.model.ChatRoomCategory
import com.kolipri.uzzap.model.ChatRoomInfo
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
import java.util.concurrent.TimeUnit

/** REST/Auth client for the Uzzap Supabase backend. */
object SupabaseClient {
    private const val TAG = "SupabaseClient"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY
    var isOnline: Boolean = false; private set
    var isTablesConfigured: Boolean = false; private set
    var connectionStatus: String = "Connecting to network..."; private set
    var userAccessToken: String? = null; private set
    fun setAccessTokenForLegacy(token: String?) { userAccessToken = token }
    private var userRefreshToken: String? = null
    private var sessionExpiresAtMs: Long = 0L
    private var sessionMonitorStarted = false
    private val sessionRefreshLock = Any()
    private var sessionRefreshInFlight = false
    private val sessionRefreshWaiters = mutableListOf<(Boolean) -> Unit>()
    private fun authenticated(): Boolean = !userAccessToken.isNullOrBlank()

    fun restorePersistedSession() {
        val token = SessionStore.accessToken() ?: return
        userAccessToken = token; userRefreshToken = SessionStore.refreshToken(); sessionExpiresAtMs = SessionStore.expiresAtMs()
        AppState.authToken = token; AppState.email = SessionStore.email(); AppState.nickname = SessionStore.nickname().ifBlank { AppState.nickname }
        AppState.role = SessionStore.role()
        AppState.username = AppState.nickname.lowercase().replace(" ", "_"); startSessionMonitor()
        if (sessionExpiresAtMs > 0L && sessionExpiresAtMs <= System.currentTimeMillis() + 60_000L) refreshSession()
    }
    private fun startSessionMonitor() {
        if (sessionMonitorStarted) return
        sessionMonitorStarted = true
        val tick = object : Runnable {
            override fun run() {
                if (authenticated()) refreshSessionIfNeeded()
                mainHandler.postDelayed(this, 60_000L)
            }
        }
        mainHandler.post(tick)
    }

    private fun refreshSessionIfNeeded() {
        val refresh = userRefreshToken ?: return
        if (sessionExpiresAtMs == 0L || sessionExpiresAtMs <= System.currentTimeMillis() + 120_000L) {
            refreshSession(refresh)
        }
    }

    /**
     * Ensures an authenticated request never intentionally uses an expired access JWT.
     * Refreshes are serialized because Supabase refresh-token rotation normally allows a
     * refresh token to be exchanged only once. Concurrent refresh calls can otherwise race.
     */
    private fun ensureValidSession(forceRefresh: Boolean = false, onResult: (Boolean) -> Unit) {
        if (!authenticated()) {
            mainHandler.post { onResult(false) }
            return
        }

        val needsRefresh = forceRefresh ||
            sessionExpiresAtMs == 0L ||
            sessionExpiresAtMs <= System.currentTimeMillis() + 30_000L

        if (!needsRefresh) {
            mainHandler.post { onResult(true) }
            return
        }

        synchronized(sessionRefreshLock) {
            sessionRefreshWaiters.add(onResult)
            if (sessionRefreshInFlight) return
            sessionRefreshInFlight = true
        }
        refreshSession()
    }

    private fun finishSessionRefresh(success: Boolean) {
        val waiters = synchronized(sessionRefreshLock) {
            sessionRefreshInFlight = false
            val pending = sessionRefreshWaiters.toList()
            sessionRefreshWaiters.clear()
            pending
        }
        mainHandler.post { waiters.forEach { it(success) } }
    }

    private fun refreshSession(refreshToken: String? = userRefreshToken) {
        if (refreshToken.isNullOrBlank()) {
            finishSessionRefresh(false)
            return
        }

        val json = JSONObject().put("refresh_token", refreshToken)
        httpClient.newCall(postJson("/auth/v1/token?grant_type=refresh_token", json, false)).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Session refresh failed", e)
                finishSessionRefresh(false)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        Log.w(TAG, "Session refresh rejected: ${errorTextFromBody(body, "HTTP ${it.code}")}")
                        finishSessionRefresh(false)
                        return
                    }

                    val success = runCatching {
                        val obj = JSONObject(body)
                        val access = obj.optString("access_token")
                        if (access.isBlank()) error("Missing access token")
                        val nextRefresh = obj.optString("refresh_token").ifBlank { refreshToken }
                        val expiresIn = obj.optLong("expires_in", 3600L)

                        userAccessToken = access
                        userRefreshToken = nextRefresh
                        sessionExpiresAtMs = System.currentTimeMillis() + expiresIn * 1000L
                        AppState.authToken = access
                        SessionStore.save(
                            access,
                            nextRefresh,
                            sessionExpiresAtMs,
                            AppState.email,
                            AppState.nickname,
                            AppState.role
                        )
                        true
                    }.getOrElse { e ->
                        Log.w(TAG, "Invalid refresh response", e)
                        false
                    }
                    finishSessionRefresh(success)
                }
            }
        })
    }

    /** Runs an authenticated operation after silently refreshing the session when needed. */
    fun withValidSession(onReady: (Boolean) -> Unit) {
        ensureValidSession(onResult = onReady)
    }
    fun authHeader(): String = "Bearer ${userAccessToken ?: anonKey}"

    private fun request(path: String, authenticated: Boolean = false): Request.Builder { val bearer = if (authenticated && !userAccessToken.isNullOrBlank()) "Bearer ${userAccessToken!!}" else "Bearer $anonKey"; return Request.Builder().url("$supabaseUrl$path").header("apikey", anonKey).header("Authorization", bearer).header("Content-Type", "application/json") }
    private fun postJson(path: String, json: JSONObject, authenticated: Boolean = true): Request = request(path, authenticated).post(json.toString().toRequestBody(jsonMediaType)).build()
    private fun errorText(response: Response, fallback: String): String = errorTextFromBody(response.body?.string().orEmpty(), fallback)
    private fun errorTextFromBody(body: String, fallback: String): String = try { val obj = JSONObject(body); obj.optString("error_description").ifBlank { obj.optString("message").ifBlank { obj.optString("msg").ifBlank { fallback } } } } catch (_: Exception) { fallback }

    fun checkHealth(onResult: (Boolean, Boolean, String) -> Unit) { if (anonKey.isBlank()) { isOnline = false; isTablesConfigured = false; connectionStatus = "Missing Supabase key (check build config)"; mainHandler.post { onResult(false, false, connectionStatus) }; return }; val req = request("/rest/v1/room_messages?select=id&limit=1").get().build(); httpClient.newCall(req).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) { isOnline = false; isTablesConfigured = false; connectionStatus = "Offline / Connection Failed"; mainHandler.post { onResult(false, false, connectionStatus) } }; override fun onResponse(call: Call, response: Response) { response.use { val body = it.body?.string().orEmpty(); when { it.isSuccessful -> { isOnline = true; isTablesConfigured = true; connectionStatus = "Network Online" }; it.code == 401 || it.code == 403 -> { isOnline = false; isTablesConfigured = false; connectionStatus = "Invalid Supabase key (check SUPABASE_PUBLISHABLE_KEY)" }; it.code == 404 && body.contains("PGRST205") -> { isOnline = true; isTablesConfigured = false; connectionStatus = "Network Online (setup required)" }; else -> { isOnline = false; isTablesConfigured = false; connectionStatus = "Network HTTP ${it.code}" } }; mainHandler.post { onResult(isOnline, isTablesConfigured, connectionStatus) } } } }) }

    fun fetchCategoriesAndRooms(onResult: (List<ChatRoomCategory>?) -> Unit) { val req = request("/rest/v1/chat_categories?select=id,name,sort_order&order=sort_order.asc").get().build(); httpClient.newCall(req).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) { mainHandler.post { onResult(null) } }; override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { mainHandler.post { onResult(null) }; return }; try { val categories = JSONArray(it.body?.string().orEmpty()); val roomReq = request("/rest/v1/chat_rooms?select=id,category_id,name,capacity&order=name.asc").get().build(); httpClient.newCall(roomReq).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) { mainHandler.post { onResult(null) } }; override fun onResponse(call: Call, roomResponse: Response) { roomResponse.use { if (!it.isSuccessful) { mainHandler.post { onResult(null) }; return }; try { val rooms = JSONArray(it.body?.string().orEmpty()); val byCategory = mutableMapOf<String, MutableList<ChatRoomInfo>>(); for (i in 0 until rooms.length()) { val o = rooms.getJSONObject(i); byCategory.getOrPut(o.optString("category_id")) { mutableListOf() }.add(ChatRoomInfo(o.optString("name"), 0, o.optInt("capacity", 50))) }; val result = buildList { for (i in 0 until categories.length()) { val c = categories.getJSONObject(i); val list = byCategory[c.optString("id")] ?: emptyList(); add(ChatRoomCategory(c.optString("name"), list.sumOf { it.userCount }, list.sumOf { it.capacity }, list)) } }; mainHandler.post { onResult(result) } } catch (e: Exception) { Log.e(TAG, "Room parsing failed", e); mainHandler.post { onResult(null) } } } } }); } catch (e: Exception) { Log.e(TAG, "Category parsing failed", e); mainHandler.post { onResult(null) } } } } }) }

    fun fetchMessages(roomName: String, afterId: Long = 0, onResult: (List<ChatMessage>?) -> Unit) {
        if (!authenticated()) { Log.w(TAG, "fetchMessages skipped: no authenticated session"); mainHandler.post { onResult(null) }; return }
        val room = URLEncoder.encode(roomName, "UTF-8")
        val after = if (afterId > 0) "&id=gt.$afterId" else ""
        val req = request("/rest/v1/room_messages?room_name=eq.$room&select=id,sender,body,is_system,created_at&order=id.asc&limit=60$after", true).get().build()
        httpClient.newCall(req).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) { Log.w(TAG, "fetchMessages network failure for $roomName", e); mainHandler.post { onResult(null) } }; override fun onResponse(call: Call, response: Response) { response.use { val body = it.body?.string().orEmpty(); if (!it.isSuccessful) { Log.w(TAG, "fetchMessages HTTP ${it.code} for $roomName: ${errorTextFromBody(body, "HTTP ${it.code}")}"); mainHandler.post { onResult(null) }; return }; try { val arr = JSONArray(body); val result = mutableListOf<ChatMessage>(); for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); result.add(ChatMessage(o.optString("sender"), o.optString("body"), o.optBoolean("is_system"), o.optLong("id"), o.optString("created_at"))) }; mainHandler.post { onResult(result) } } catch (e: Exception) { Log.e(TAG, "Message parsing failed", e); mainHandler.post { onResult(null) } } } } })
    }

    /** Fetches the next older page before the supplied message id. Results are oldest-first. */
    fun fetchMessagesBefore(roomName: String, beforeId: Long, limit: Int = 50, onResult: (List<ChatMessage>?) -> Unit) {
        if (!authenticated() || beforeId <= 0L) { mainHandler.post { onResult(emptyList()) }; return }
        val room = URLEncoder.encode(roomName, "UTF-8"); val safeLimit = limit.coerceIn(1, 100)
        val req = request("/rest/v1/room_messages?room_name=eq.$room&id=lt.$beforeId&select=id,sender,body,is_system,created_at&order=id.desc&limit=$safeLimit", true).get().build()
        httpClient.newCall(req).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) { Log.w(TAG, "fetchMessagesBefore network failure for $roomName", e); mainHandler.post { onResult(null) } }; override fun onResponse(call: Call, response: Response) { response.use { val body = it.body?.string().orEmpty(); if (!it.isSuccessful) { Log.w(TAG, "fetchMessagesBefore HTTP ${it.code}: ${errorTextFromBody(body, "HTTP ${it.code}")}"); mainHandler.post { onResult(null) }; return }; try { val arr = JSONArray(body); val result = mutableListOf<ChatMessage>(); for (i in arr.length() - 1 downTo 0) { val o = arr.getJSONObject(i); result.add(ChatMessage(o.optString("sender"), o.optString("body"), o.optBoolean("is_system"), o.optLong("id"), o.optString("created_at"))) }; mainHandler.post { onResult(result) } } catch (e: Exception) { Log.e(TAG, "Older message parsing failed", e); mainHandler.post { onResult(null) } } } } })
    }

    fun fetchLatestMessages(roomName: String, limit: Int = 60, onResult: (List<ChatMessage>?) -> Unit) {
        if (!authenticated()) { mainHandler.post { onResult(null) }; return }
        val room = URLEncoder.encode(roomName, "UTF-8"); val safeLimit = limit.coerceIn(1, 100)
        val req = request("/rest/v1/room_messages?room_name=eq.$room&select=id,sender,body,is_system,created_at&order=id.desc&limit=$safeLimit", true).get().build()
        httpClient.newCall(req).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) { Log.w(TAG, "fetchLatestMessages network failure for $roomName", e); mainHandler.post { onResult(null) } }; override fun onResponse(call: Call, response: Response) { response.use { val body = it.body?.string().orEmpty(); if (!it.isSuccessful) { mainHandler.post { onResult(null) }; return }; try { val arr = JSONArray(body); val result = mutableListOf<ChatMessage>(); for (i in arr.length() - 1 downTo 0) { val o = arr.getJSONObject(i); result.add(ChatMessage(o.optString("sender"), o.optString("body"), o.optBoolean("is_system"), o.optLong("id"), o.optString("created_at"))) }; mainHandler.post { onResult(result) } } catch (e: Exception) { Log.e(TAG, "Latest message parsing failed", e); mainHandler.post { onResult(null) } } } } })
    }

    fun sendMessage(roomName: String, sender: String, body: String, isSystem: Boolean = false, onResult: (ChatMessage?) -> Unit) {
        if (!authenticated() || body.trim().isEmpty() || body.length > 2000) { mainHandler.post { onResult(null) }; return }
        val json = JSONObject().apply { put("room_name", roomName); put("sender", sender); put("body", body.trim()); put("is_system", isSystem) }
        httpClient.newCall(request("/rest/v1/room_messages", true).header("Prefer", "return=representation").post(json.toString().toRequestBody(jsonMediaType)).build()).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) { mainHandler.post { onResult(null) } }; override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { Log.w(TAG, "sendMessage failed: ${errorText(it, "HTTP ${it.code}")}"); mainHandler.post { onResult(null) }; return }; try { val o = JSONArray(it.body?.string().orEmpty()).optJSONObject(0); mainHandler.post { onResult(o?.let { x -> ChatMessage(x.optString("sender"), x.optString("body"), x.optBoolean("is_system"), x.optLong("id"), x.optString("created_at")) } ?: ChatMessage(sender, body.trim())) } } catch (_: Exception) { mainHandler.post { onResult(ChatMessage(sender, body.trim())) } } } } })
    }
    fun fetchOnlineCount(onResult: (Int) -> Unit) { val req = request("/rest/v1/profiles?is_online=eq.true&select=username").get().build(); httpClient.newCall(req).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) { mainHandler.post { onResult(-1) } }; override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { mainHandler.post { onResult(-1) }; return }; try { val body = it.body?.string().orEmpty(); val count = JSONArray(body).length(); mainHandler.post { onResult(count) } } catch (e: Exception) { mainHandler.post { onResult(-1) } } } } }) }

    fun fetchParticipants(roomName: String, onResult: (List<String>?) -> Unit) { val room = URLEncoder.encode(roomName, "UTF-8"); val req = request("/rest/v1/room_participants?room_name=eq.$room&select=username&order=username.asc").get().build(); httpClient.newCall(req).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) { mainHandler.post { onResult(null) } }; override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { mainHandler.post { onResult(null) }; return }; try { val arr = JSONArray(it.body?.string().orEmpty()); mainHandler.post { onResult(List(arr.length()) { i -> arr.getJSONObject(i).optString("username") }) } } catch (_: Exception) { mainHandler.post { onResult(null) } } } } }) }
    fun fetchLiveRoomCounts(onResult: (Map<String, Int>?) -> Unit) {
        val req = request("/rest/v1/room_participants?select=room_name,username").get().build()
        httpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "fetchLiveRoomCounts failed", e)
                mainHandler.post { onResult(null) }
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        mainHandler.post { onResult(null) }
                        return
                    }
                    try {
                        val arr = JSONArray(it.body?.string().orEmpty())
                        val counts = mutableMapOf<String, MutableSet<String>>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val room = o.optString("room_name").trim()
                            val user = o.optString("username").trim()
                            if (room.isNotEmpty() && user.isNotEmpty()) {
                                counts.getOrPut(room) { mutableSetOf() }.add(user)
                            }
                        }
                        val map = counts.mapValues { entry -> entry.value.size }
                        mainHandler.post { onResult(map) }
                    } catch (e: Exception) {
                        Log.e(TAG, "fetchLiveRoomCounts parse failed", e)
                        mainHandler.post { onResult(null) }
                    }
                }
            }
        })
    }
    private fun extractUserId(): String? {
        val token = userAccessToken ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size < 2) null
            else {
                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING), Charsets.UTF_8)
                JSONObject(payload).optString("sub").takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) { null }
    }

    fun pingPresence(roomName: String, username: String, onResult: ((Boolean) -> Unit)? = null) {
        if (!authenticated()) {
            mainHandler.post { onResult?.invoke(false) }
            return
        }

        val json = JSONObject().put("room_name", roomName).put("username", username)

        fun sendPresence(retryAfterRefresh: Boolean) {
            val request = request("/rest/v1/room_participants?on_conflict=room_name,username", true)
                .header("Prefer", "resolution=merge-duplicates")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "Join room network failure for $roomName", e)
                    mainHandler.post { onResult?.invoke(false) }
                }

                override fun onResponse(call: Call, response: Response) {
                    val code = response.code
                    response.close()

                    if (code == 401 && retryAfterRefresh) {
                        Log.i(TAG, "Join room received 401; refreshing session and retrying once")
                        ensureValidSession(forceRefresh = true) { refreshed ->
                            if (refreshed) sendPresence(false)
                            else mainHandler.post { onResult?.invoke(false) }
                        }
                        return
                    }

                    mainHandler.post { onResult?.invoke(code in 200..299) }
                }
            })
        }

        ensureValidSession { ready ->
            if (ready) sendPresence(true)
            else {
                Log.w(TAG, "Join room blocked because the session could not be refreshed")
                mainHandler.post { onResult?.invoke(false) }
            }
        }
    }
    fun leaveRoom(roomName: String, username: String) { if (!authenticated()) return; val room = URLEncoder.encode(roomName, "UTF-8"); val uid = extractUserId()?.let { URLEncoder.encode(it, "UTF-8") } ?: return; httpClient.newCall(request("/rest/v1/room_participants?room_name=eq.$room&owner_id=eq.$uid", true).delete().build()).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) = Unit; override fun onResponse(call: Call, response: Response) { response.close() } }) }
    fun fetchBuddies(userHandle: String, onResult: (Map<String, List<String>>?) -> Unit) { if (!authenticated()) { mainHandler.post { onResult(null) }; return }; val req = request("/rest/v1/buddies?select=buddy_handle,group_name&order=buddy_handle.asc", true).get().build(); httpClient.newCall(req).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) { Log.w(TAG, "fetchBuddies network failure", e); mainHandler.post { onResult(null) } }; override fun onResponse(call: Call, response: Response) { response.use { val body = it.body?.string().orEmpty(); if (!it.isSuccessful) { Log.w(TAG, "fetchBuddies HTTP ${it.code}: $body"); mainHandler.post { onResult(null) }; return }; try { val arr = JSONArray(body); val grouped = mutableMapOf<String, MutableList<String>>(); for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val buddy = o.optString("buddy_handle").trim(); if (buddy.isNotEmpty()) grouped.getOrPut(o.optString("group_name").ifBlank { "My Friends" }) { mutableListOf() }.add(buddy) }; Log.d(TAG, "fetchBuddies: ${arr.length()} rows for $userHandle"); mainHandler.post { onResult(grouped) } } catch (e: Exception) { Log.e(TAG, "fetchBuddies parse failed", e); mainHandler.post { onResult(null) } } } } }) }
    fun addBuddy(userHandle: String, buddyHandle: String, groupName: String, onResult: (Boolean) -> Unit) { if (!authenticated()) { mainHandler.post { onResult(false) }; return }; val json = JSONObject().put("user_handle", userHandle).put("buddy_handle", buddyHandle).put("group_name", groupName); httpClient.newCall(request("/rest/v1/buddies?on_conflict=user_handle,buddy_handle", true).header("Prefer", "resolution=merge-duplicates").post(json.toString().toRequestBody(jsonMediaType)).build()).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) { mainHandler.post { onResult(false) } }; override fun onResponse(call: Call, response: Response) { response.use { mainHandler.post { onResult(it.isSuccessful) } } } }) }
    fun deleteBuddy(userHandle: String, buddyHandle: String, onResult: (Boolean) -> Unit) {
        if (!authenticated()) { mainHandler.post { onResult(false) }; return }
        val uid = extractUserId()?.let { URLEncoder.encode(it, "UTF-8") } ?: run { mainHandler.post { onResult(false) }; return }; val buddy = URLEncoder.encode(buddyHandle, "UTF-8")
        val req = request("/rest/v1/buddies?owner_id=eq.$uid&buddy_handle=eq.$buddy", true).delete().build()
        httpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { Log.w(TAG, "deleteBuddy network failure", e); mainHandler.post { onResult(false) } }
            override fun onResponse(call: Call, response: Response) { response.use { val ok = it.isSuccessful; if (!ok) Log.w(TAG, "deleteBuddy HTTP ${it.code}"); mainHandler.post { onResult(ok) } } }
        })
    }

    fun deleteAccount(onResult: (Boolean) -> Unit) {
        if (!authenticated()) { mainHandler.post { onResult(true) }; return }
        // RLS policies allow authenticated users to delete their own profile, buddies, and DM entries
        val profileReq = request("/rest/v1/profiles", true).delete().build()
        httpClient.newCall(profileReq).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { mainHandler.post { onResult(false) } }
            override fun onResponse(call: Call, response: Response) {
                response.close()
                val buddiesReq = request("/rest/v1/buddies", true).delete().build()
                httpClient.newCall(buddiesReq).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { mainHandler.post { onResult(true) } }
                    override fun onResponse(call: Call, resp2: Response) {
                        resp2.close()
                        signOut()
                        mainHandler.post { onResult(true) }
                    }
                })
            }
        })
    }
}
