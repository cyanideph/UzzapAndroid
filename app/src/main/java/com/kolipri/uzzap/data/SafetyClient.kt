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
    private fun userId(username: String, result: (String?) -> Unit) {
        rpc("get_user_id_by_username", JSONObject().put("p_username", username), object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { result(null) } }
            override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { main.post { result(null) }; return }; val v = it.body?.string()?.trim()?.removeSurrounding("\"")?.ifBlank { null }; main.post { result(v) } } }
        })
    }
    private fun roomId(room: String, result: (String?) -> Unit) {
        rpc("get_room_id_by_name", JSONObject().put("p_room_name", room), object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { result(null) } }
            override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { main.post { result(null) }; return }; val v = it.body?.string()?.trim()?.removeSurrounding("\"")?.ifBlank { null }; main.post { result(v) } } }
        })
    }
    private fun currentId(): String? = SupabaseClient.userAccessToken?.let { token -> runCatching { val p = token.split('.').getOrNull(1) ?: return@runCatching null; val b = Base64.decode(p, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING); JSONObject(String(b, StandardCharsets.UTF_8)).optString("sub").ifBlank { null } }.getOrNull() }

    fun isBlocked(username: String, onResult: (Boolean) -> Unit) {
        userId(username) { uid -> val me = currentId(); if (uid == null || me == null) { onResult(false); return@userId }; val path = "/rest/v1/blocks?blocker_id=eq.$me&blocked_id=eq.$uid&select=blocked_id&limit=1"; http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(false) } }
            override fun onResponse(call: Call, response: Response) { response.use { val found = it.isSuccessful && runCatching { JSONArray(it.body?.string().orEmpty()).length() > 0 }.getOrDefault(false); main.post { onResult(found) } } }
        }) }
    }
    fun block(username: String, onResult: (Boolean) -> Unit) = setBlock(username, true, onResult)
    fun unblock(username: String, onResult: (Boolean) -> Unit) = setBlock(username, false, onResult)
    private fun setBlock(username: String, blocked: Boolean, result: (Boolean) -> Unit) { userId(username) { uid -> if (uid == null) { result(false); return@userId }; rpc("set_blocked_user", JSONObject().put("p_other_user", uid).put("p_blocked", blocked), simple(result)) } }
    fun report(username: String, reason: String, details: String, onResult: (Boolean) -> Unit) { userId(username) { uid -> if (uid == null) { onResult(false); return@userId }; rpc("submit_user_report", JSONObject().put("p_reported_user", uid).put("p_reason", reason.take(100)).put("p_details", details.take(2000)), simple(onResult)) } }

    fun fetchReports(status: String? = null, onResult: (List<UserReport>) -> Unit) {
        val body = JSONObject().apply { if (!status.isNullOrBlank()) put("p_status", status) }
        rpc("get_reports_for_moderators", body, object : Callback {
            override fun onFailure(call: Call, e: IOException) { main.post { onResult(emptyList()) } }
            override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { main.post { onResult(emptyList()) }; return }; val list = mutableListOf<UserReport>(); runCatching { val a = JSONArray(it.body?.string().orEmpty()); for (i in 0 until a.length()) { val o = a.getJSONObject(i); list += UserReport(MessageIdRegistry.register(o.optString("id")), o.optString("reporter_id").ifBlank { null }, o.optString("reported_username"), o.optString("reason"), o.optString("details").ifBlank { null }, o.optString("status"), o.optString("created_at")) } }; main.post { onResult(list) } } }
        })
    }
    fun updateReportStatus(reportId: Long, status: String, onResult: (Boolean) -> Unit) { val uuid = MessageIdRegistry.uuidFor(reportId) ?: run { onResult(false); return }; val mapped = when (status.lowercase()) { "pending", "open" -> "open"; "reviewing" -> "reviewing"; "resolved" -> "resolved"; "dismissed" -> "dismissed"; else -> null }; if (mapped == null) { onResult(false); return }; rpc("update_report_status", JSONObject().put("p_report_id", uuid).put("p_status", mapped), simple(onResult)) }
    fun applyModerationAction(targetUsername: String, actionType: String, roomName: String?, reason: String, durationMinutes: Int?, onResult: (Boolean) -> Unit) { userId(targetUsername) { uid -> if (uid == null) { onResult(false); return@userId }; val body = JSONObject().put("p_target_user", uid).put("p_action", actionType).put("p_reason", reason.take(500)).apply { if (durationMinutes != null) put("p_duration_minutes", durationMinutes) }; if (roomName.isNullOrBlank()) { rpc("moderate_user_action", body, simple(onResult)); return@userId }; roomId(roomName) { rid -> if (rid == null) { onResult(false); return@roomId }; body.put("p_room_id", rid); rpc("moderate_user_action", body, simple(onResult)) } } }
    fun fetchActiveModerationActions(onResult: (List<ModerationAction>) -> Unit) { http.newCall(req("/rest/v1/moderation_actions?order=created_at.desc&select=*").get().build()).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { main.post { onResult(emptyList()) } }
        override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { main.post { onResult(emptyList()) }; return }; val list = mutableListOf<ModerationAction>(); runCatching { val a = JSONArray(it.body?.string().orEmpty()); for (i in 0 until a.length()) { val o = a.getJSONObject(i); list += ModerationAction(MessageIdRegistry.register(o.optString("id")), o.optString("moderator_id").ifBlank { null }, o.optString("target_user_id"), o.optString("action"), o.optString("room_id").ifBlank { null }, o.optString("reason"), o.optString("expires_at").ifBlank { null }, o.optString("created_at")) } }; main.post { onResult(list) } } }
    }) }
    fun revokeModerationAction(actionId: Long, onResult: (Boolean) -> Unit) { val uuid = MessageIdRegistry.uuidFor(actionId) ?: run { onResult(false); return }; rpc("revoke_moderation_action", JSONObject().put("p_action_id", uuid), simple(onResult)) }
    fun deleteRoomMessage(messageId: Long, onResult: (Boolean) -> Unit) { val uuid = MessageIdRegistry.uuidFor(messageId) ?: run { onResult(false); return }; rpc("moderate_message_delete", JSONObject().put("p_message_id", uuid).put("p_reason", "Moderator deletion"), simple(onResult)) }
    fun checkUserMuted(username: String, roomName: String?, onResult: (Boolean, String?) -> Unit) { if (roomName.isNullOrBlank()) { onResult(false, null); return }; userId(username) { uid -> if (uid == null) { onResult(false, null); return@userId }; roomId(roomName) { rid -> if (rid == null) { onResult(false, null); return@roomId }; val path = "/rest/v1/room_mutes?user_id=eq.$uid&room_id=eq.$rid&select=expires_at&limit=1"; http.newCall(req(path).get().build()).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) { main.post { onResult(false, null) } }; override fun onResponse(call: Call, response: Response) { response.use { val active = it.isSuccessful && runCatching { JSONArray(it.body?.string().orEmpty()).length() > 0 }.getOrDefault(false); main.post { onResult(active, if (active) "Muted by a moderator" else null) } } } }) } } }
}
