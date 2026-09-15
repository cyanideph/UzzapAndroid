package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.model.ModerationAction
import com.kolipri.uzzap.model.UserReport
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object SafetyClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun req(path: String) = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type", "application/json")
    private fun encoded(value: String) = URLEncoder.encode(value.lowercase().replace(" ", "_"), "UTF-8")

    fun isBlocked(username: String, onResult: (Boolean) -> Unit) {
        val owner = currentUserId() ?: run { main.post { onResult(false) }; return }
        val u = encoded(username)
        val o = encoded(owner)
        http.newCall(req("/rest/v1/blocked_users?owner_id=eq.$o&blocked_username=eq.$u&select=id&limit=1").get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(false) } }
            override fun onResponse(call: Call, response: Response) { response.use { val body = it.body?.string().orEmpty(); main.post { onResult(it.isSuccessful && JSONArray(body).length() > 0) } } }
        })
    }

    fun block(username: String, onResult: (Boolean) -> Unit) {
        val owner = currentUserId() ?: run { main.post { onResult(false) }; return }
        val body = JSONObject().put("owner_id", owner).put("blocked_username", username.lowercase().replace(" ", "_"))
        http.newCall(req("/rest/v1/blocked_users?on_conflict=owner_id,blocked_username").header("Prefer", "resolution=merge-duplicates,return=minimal").post(body.toString().toRequestBody(jsonMediaType)).build()).enqueue(callback(onResult))
    }

    fun unblock(username: String, onResult: (Boolean) -> Unit) {
        val owner = currentUserId() ?: run { main.post { onResult(false) }; return }
        val u = encoded(username)
        val o = encoded(owner)
        http.newCall(req("/rest/v1/blocked_users?owner_id=eq.$o&blocked_username=eq.$u").delete().build()).enqueue(callback(onResult))
    }

    fun report(username: String, reason: String, details: String, onResult: (Boolean) -> Unit) {
        val reporter = currentUserId() ?: run { main.post { onResult(false) }; return }
        val body = JSONObject().put("reporter_id", reporter).put("reported_username", username.lowercase().replace(" ", "_")).put("reason", reason.take(100)).put("details", details.take(1000))
        http.newCall(req("/rest/v1/user_reports").post(body.toString().toRequestBody(jsonMediaType)).build()).enqueue(callback(onResult))
    }

    fun fetchReports(status: String? = null, onResult: (List<UserReport>) -> Unit) {
        val filter = if (!status.isNullOrBlank()) "&status=eq.${encoded(status)}" else ""
        http.newCall(req("/rest/v1/user_reports?order=created_at.desc$filter&select=*").get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(emptyList()) } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { main.post { onResult(emptyList()) }; return }
                    val body = it.body?.string().orEmpty()
                    val list = mutableListOf<UserReport>()
                    runCatching {
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            list.add(UserReport(
                                id = o.optLong("id"),
                                reporterId = o.optString("reporter_id").ifBlank { null },
                                reportedUsername = o.optString("reported_username"),
                                reason = o.optString("reason"),
                                details = o.optString("details").ifBlank { null },
                                status = o.optString("status", "pending"),
                                createdAt = o.optString("created_at")
                            ))
                        }
                    }
                    main.post { onResult(list) }
                }
            }
        })
    }

    fun updateReportStatus(reportId: Long, status: String, onResult: (Boolean) -> Unit) {
        val body = JSONObject().put("status", status)
        http.newCall(req("/rest/v1/user_reports?id=eq.$reportId").patch(body.toString().toRequestBody(jsonMediaType)).build()).enqueue(callback(onResult))
    }

    fun applyModerationAction(targetUsername: String, actionType: String, roomName: String?, reason: String, durationMinutes: Int?, onResult: (Boolean) -> Unit) {
        val modId = currentUserId()
        val target = targetUsername.lowercase().replace(" ", "_")
        val body = JSONObject().apply {
            put("target_username", target)
            put("action_type", actionType)
            if (!roomName.isNullOrBlank()) put("room_name", roomName)
            put("reason", reason.take(200))
            if (modId != null) put("moderator_id", modId)
            if (durationMinutes != null && durationMinutes > 0) {
                val exp = System.currentTimeMillis() + durationMinutes * 60_000L
                val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(exp))
                put("expires_at", iso)
            }
        }
        http.newCall(req("/rest/v1/moderation_actions").post(body.toString().toRequestBody(jsonMediaType)).build()).enqueue(callback(onResult))
    }

    fun fetchActiveModerationActions(onResult: (List<ModerationAction>) -> Unit) {
        http.newCall(req("/rest/v1/moderation_actions?order=created_at.desc&select=*").get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(emptyList()) } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { main.post { onResult(emptyList()) }; return }
                    val body = it.body?.string().orEmpty()
                    val list = mutableListOf<ModerationAction>()
                    runCatching {
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            list.add(ModerationAction(
                                id = o.optLong("id"),
                                moderatorId = o.optString("moderator_id").ifBlank { null },
                                targetUsername = o.optString("target_username"),
                                actionType = o.optString("action_type"),
                                roomName = o.optString("room_name").ifBlank { null },
                                reason = o.optString("reason"),
                                expiresAt = o.optString("expires_at").ifBlank { null },
                                createdAt = o.optString("created_at")
                            ))
                        }
                    }
                    main.post { onResult(list) }
                }
            }
        })
    }

    fun revokeModerationAction(actionId: Long, onResult: (Boolean) -> Unit) {
        http.newCall(req("/rest/v1/moderation_actions?id=eq.$actionId").delete().build()).enqueue(callback(onResult))
    }

    fun deleteRoomMessage(messageId: Long, onResult: (Boolean) -> Unit) {
        http.newCall(req("/rest/v1/room_messages?id=eq.$messageId").delete().build()).enqueue(callback(onResult))
    }

    fun checkUserMuted(username: String, roomName: String?, onResult: (Boolean, String?) -> Unit) {
        val target = encoded(username)
        http.newCall(req("/rest/v1/moderation_actions?target_username=eq.$target&select=*").get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(false, null) } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) { main.post { onResult(false, null) }; return }
                    val body = it.body?.string().orEmpty()
                    var isMuted = false
                    var reason: String? = null
                    runCatching {
                        val arr = JSONArray(body)
                        val now = System.currentTimeMillis()
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val act = o.optString("action_type")
                            val r = o.optString("room_name").ifBlank { null }
                            if (act == "mute" || act == "ban") {
                                if (r == null || roomName == null || r.equals(roomName, true)) {
                                    val expStr = o.optString("expires_at").ifBlank { null }
                                    val notExpired = if (expStr == null) true else {
                                        val expDate = runCatching { sdf.parse(expStr.take(19)) }.getOrNull()
                                        expDate != null && expDate.time > now
                                    }
                                    if (notExpired) {
                                        isMuted = true
                                        reason = o.optString("reason")
                                        break
                                    }
                                }
                            }
                        }
                    }
                    main.post { onResult(isMuted, reason) }
                }
            }
        })
    }

    private fun currentUserId(): String? {
        val token = SupabaseClient.userAccessToken ?: return null
        return runCatching {
            val payload = token.split('.').getOrNull(1) ?: return@runCatching null
            val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            JSONObject(String(bytes, Charsets.UTF_8)).optString("sub").ifBlank { null }
        }.getOrNull()
    }

    private fun callback(onResult: (Boolean) -> Unit) = object : Callback {
        override fun onFailure(call: Call, e: IOException) { main.post { onResult(false) } }
        override fun onResponse(call: Call, response: Response) { response.use { main.post { onResult(it.isSuccessful) } } }
    }
}
