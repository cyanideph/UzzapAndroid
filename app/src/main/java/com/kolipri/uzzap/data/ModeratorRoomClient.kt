package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder

/** Supabase access for the protected moderator-only room and role management. */
object ModeratorRoomClient {
    private const val TAG = "ModeratorRoomClient"
    private val main = Handler(Looper.getMainLooper())

    private fun request(path: String): Request.Builder = Request.Builder()
        .url("${SupabaseClient.supabaseUrl}$path")
        .header("apikey", SupabaseClient.anonKey)
        .header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}")
        .header("Content-Type", "application/json")

    fun isModeratorOrAdmin(onResult: (Boolean) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) {
            main.post { onResult(false) }
            return
        }
        val req = request("/rest/v1/rpc/is_moderator_or_admin").post(okhttp3.RequestBody.create(null, ByteArray(0))).build()
        SupabaseClient.runAuthorized(req, onResult)
    }

    fun fetchModeratorRoom(onResult: (String?) -> Unit) {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) { main.post { onResult(null) }; return }
        val req = request("/rest/v1/special_rooms?slug=eq.moderator-room&moderator_only=is.true&select=id,name,description&limit=1").get().build()
        SupabaseClient.httpGetJson(req) { body, ok ->
            if (!ok) { main.post { onResult(null) }; return@httpGetJson }
            val name = runCatching { JSONArray(body).optJSONObject(0)?.optString("name")?.ifBlank { null } }.getOrNull()
            main.post { onResult(name) }
        }
    }

    fun setModeratorByEmail(email: String, makeModerator: Boolean, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || SupabaseClient.userAccessToken.isNullOrBlank()) { main.post { onResult(false, "Sign in as an admin first") }; return }
        val encoded = URLEncoder.encode(email.trim(), "UTF-8")
        val req = request("/rest/v1/rpc/set_moderator_by_email")
            .post("{\"target_email\":\"${email.trim().replace("\"", "\\\"")}\",\"make_moderator\":$makeModerator}".toRequestBody("application/json".toMediaType()))
            .build()
        SupabaseClient.httpGetJson(req) { body, ok ->
            val result = runCatching { body.trim().equals("true", true) }.getOrDefault(false)
            main.post { onResult(ok && result, if (ok && result) null else "User must have a registered Uzzap account") }
        }
    }

    fun enterRoom(onResult: (Boolean, String?) -> Unit) {
        isModeratorOrAdmin { allowed ->
            if (!allowed) { onResult(false, "Moderator or Admin privileges required") ; return@isModeratorOrAdmin }
            fetchModeratorRoom { room -> onResult(room != null, room ?: "Moderator Room is not configured") }
        }
    }
}
