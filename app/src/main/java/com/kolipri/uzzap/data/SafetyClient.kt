package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.model.ModerationAction
import com.kolipri.uzzap.model.UserReport
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object SafetyClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).writeTimeout(12, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private fun req(path: String) = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type", "application/json")
    private fun rpc(name: String, body: JSONObject, cb: Callback) { http.newCall(req("/rest/v1/rpc/$name").post(body.toString().toRequestBody(mediaType)).build()).enqueue(cb) }
    private fun simple(result: (Boolean) -> Unit) = object : Callback {
        override fun onFailure(call: Call, e: IOException) { main.post { result(false) } }
        override fun onResponse(call: Call, response: Response) { response.use { main.post { result(it.isSuccessful) } } }
    }
    private fun currentId(): String? = SupabaseClient.userAccessToken?.let { token -> runCatching { val p = token.split('.').getOrNull(1) ?: return@runCatching null; val b = Base64.decode(p, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING); JSONObject(String(b, StandardCharsets.UTF_8)).optString("sub").ifBlank { null } }.getOrNull() }

    fun isBlocked(username: String, onResult: (Boolean) -> Unit) {
        val me = currentId()
        if (me.isNullOrBlank() || username.isBlank()) { main.post { onResult(false) }; return }
        val blocked = URLEncoder.encode(username.trim().lowercase().replace(" ", "_"), "UTF-8")
        val path = "/rest/v1/blocked_users?owner_id=eq.$me&blocked_username=eq.$blocked&select=id&limit=1"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(false) } }
            override fun onResponse(call: Call, response: Response) { response.use { val found = it.isSuccessful && runCatching { JSONArray(it.body?.string().orEmpty()).length() > 0 }.getOrDefault(false); main.post { onResult(found) } } }
        })
    }

    fun block(username: String, onResult: (Boolean) -> Unit) = setBlock(username, true, onResult)
    fun unblock(username: String, onResult: (Boolean) -> Unit) = setBlock(username, false, onResult)
    private fun setBlock(username: String, blocked: Boolean, result: (Boolean) -> Unit) {
        val me = currentId()
        val clean = username.trim().lowercase().replace(" ", "_")
        if (me.isNullOrBlank() || clean.isBlank()) { main.post { result(false) }; return }
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val request = if (blocked) {
            val body = JSONObject().put("owner_id", me).put("blocked_username", clean)
            req("/rest/v1/blocked_users").header("Prefer", "return=minimal").post(body.toString().toRequestBody(mediaType)).build()
        } else {
            req("/rest/v1/blocked_users?owner_id=eq.$me&blocked_username=eq.$encoded").delete().build()
        }
        http.newCall(request).enqueue(simple(result))
    }

    fun report(username: String, reason: String, details: String, onResult: (Boolean) -> Unit) {
        val me = currentId()
        val clean = username.trim().lowercase().replace(" ", "_")
        if (me.isNullOrBlank() || clean.isBlank()) { main.post { onResult(false) }; return }
        val body = JSONObject().put("reporter_id", me).put("reported_username", clean).put("reason", reason.take(100)).put("details", details.take(2000))
        http.newCall(req("/rest/v1/user_reports").header("Prefer", "return=minimal").post(body.toString().toRequestBody(mediaType)).build()).enqueue(simple(onResult))
    }

    fun fetchReports(status: String? = null, onResult: (List<UserReport>) -> Unit) {
        val filter = status?.takeIf { it.isNotBlank() }?.let { "&status=eq.${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
        val path = "/rest/v1/user_reports?select=id,reporter_id,reported_username,reason,details,status,created_at&order=created_at.desc&limit=100$filter"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(emptyList()) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { main.post { onResult(emptyList()) }; return }
                val list = mutableListOf<UserReport>()
                runCatching { val a = JSONArray(it.body?.string().orEmpty()); for (i in 0 until a.length()) { val o = a.getJSONObject(i); list += UserReport(o.optLong("id"), o.optString("reporter_id").ifBlank { null }, o.optString("reported_username"), o.optString("reason"), o.optString("details").ifBlank { null }, o.optString("status"), o.optString("created_at")) } }
                main.post { onResult(list) }
            } }
        })
    }

    fun updateReportStatus(reportId: Long, status: String, onResult: (Boolean) -> Unit) {
        val mapped = when (status.lowercase()) { "pending", "open" -> "reviewing"; "reviewing" -> "reviewing"; "resolved" -> "resolved"; "dismissed" -> "dismissed"; else -> null }
        if (mapped == null || reportId <= 0) { main.post { onResult(false) }; return }
        rpc("resolve_user_report", JSONObject().put("p_report_id", reportId).put("p_status", mapped).put("p_reason", "Moderator status update"), simple(onResult))
    }

    fun applyModerationAction(targetUsername: String, actionType: String, roomName: String?, reason: String, durationMinutes: Int?, onResult: (Boolean) -> Unit) {
        val action = actionType.lowercase()
        if (targetUsername.isBlank() || action !in setOf("mute", "kick", "unmute", "unkick", "warn", "ban", "unban")) { main.post { onResult(false) }; return }
        val body = JSONObject().put("p_username", targetUsername.trim()).put("p_action", action).put("p_room_name", roomName).put("p_reason", reason.take(500))
        if (durationMinutes != null) body.put("p_duration_minutes", durationMinutes)
        rpc("moderate_user", body, simple(onResult))
    }

    fun fetchActiveModerationActions(onResult: (List<ModerationAction>) -> Unit) {
        val path = "/rest/v1/moderation_actions?select=id,moderator_id,target_username,action_type,room_name,reason,expires_at,created_at&action_type=in.(mute,kick,ban)&order=created_at.desc&limit=100"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(emptyList()) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { main.post { onResult(emptyList()) }; return }
                val list = mutableListOf<ModerationAction>()
                runCatching { val a = JSONArray(it.body?.string().orEmpty()); for (i in 0 until a.length()) { val o = a.getJSONObject(i); list += ModerationAction(o.optLong("id"), o.optString("moderator_id").ifBlank { null }, o.optString("target_username"), o.optString("action_type").ifBlank { o.optString("action") }, o.optString("room_name").ifBlank { null }, o.optString("reason"), o.optString("expires_at").ifBlank { null }, o.optString("created_at")) } }
                main.post { onResult(list) }
            } }
        })
    }

    fun revokeModerationAction(actionId: Long, onResult: (Boolean) -> Unit) {
        if (actionId <= 0) { main.post { onResult(false) }; return }
        rpc("revoke_moderation_action", JSONObject().put("p_action_id", actionId), simple(onResult))
    }

    fun deleteRoomMessage(messageId: Long, onResult: (Boolean) -> Unit) {
        if (messageId <= 0) { main.post { onResult(false) }; return }
        rpc("moderate_room_message", JSONObject().put("p_message_id", messageId).put("p_reason", "Moderator deletion"), simple(onResult))
    }

    fun checkUserMuted(username: String, roomName: String?, onResult: (Boolean, String?) -> Unit) {
        if (roomName.isNullOrBlank() || username.isBlank()) { main.post { onResult(false, null) }; return }
        val u = URLEncoder.encode(username.trim().lowercase().replace(" ", "_"), "UTF-8")
        val r = URLEncoder.encode(roomName.trim(), "UTF-8")
        val path = "/rest/v1/room_sanctions?username=eq.$u&room_name=eq.$r&sanction=eq.mute&active=is.true&select=expires_at&limit=20"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(false, null) } }
            override fun onResponse(call: Call, response: Response) { response.use {
                val active = if (!it.isSuccessful) false else runCatching {
                    val a = JSONArray(it.body?.string().orEmpty())
                    (0 until a.length()).any { i ->
                        val expiry = a.getJSONObject(i).optString("expires_at").ifBlank { null }
                        expiry == null || runCatching { java.time.Instant.parse(expiry).isAfter(java.time.Instant.now()) }.getOrDefault(false)
                    }
                }.getOrDefault(false)
                main.post { onResult(active, if (active) "Muted by a moderator" else null) }
            } }
        })
    }
}
