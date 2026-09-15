package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.kolipri.uzzap.BuildConfig
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class DeliveryReceipt(val recipient: String, val body: String, val createdAt: String, val isRead: Boolean)

object DeliveryReceiptClient {
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private fun req(path: String) = Request.Builder().url("$url$path").header("apikey", key).header("Authorization", "Bearer ${SupabaseClient.userAccessToken.orEmpty()}").header("Content-Type", "application/json")
    private fun currentUserId(): String? = SupabaseClient.userAccessToken?.let { token -> runCatching { val p = token.split('.').getOrNull(1) ?: return@runCatching null; val b = Base64.decode(p, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING); JSONObject(String(b, StandardCharsets.UTF_8)).optString("sub").ifBlank { null } }.getOrNull() }

    fun fetchRecent(onResult: (List<DeliveryReceipt>?) -> Unit) {
        val fn = "ReadReceipts.fetchRecent"; AppDiagnostics.started(fn)
        val me = currentUserId()
        if (me.isNullOrBlank()) { AppDiagnostics.failed(fn, "Missing authenticated user"); main.post { onResult(null) }; return }
        val path = "/rest/v1/direct_messages?sender_id=eq.$me&select=recipient,body,created_at,is_read&order=id.desc&limit=200"
        http.newCall(req(path).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { AppDiagnostics.failed(fn, e.message ?: "Network error"); main.post { onResult(null) } }
            override fun onResponse(call: Call, response: Response) { response.use { if (!it.isSuccessful) { AppDiagnostics.failed(fn, "HTTP ${it.code}: ${it.message}"); main.post { onResult(null) }; return }; val result = runCatching { val a = JSONArray(it.body?.string().orEmpty()); List(a.length()) { i -> val o = a.getJSONObject(i); DeliveryReceipt(o.optString("recipient"), o.optString("body"), o.optString("created_at"), o.optBoolean("is_read")) } }.getOrNull(); if (result != null) AppDiagnostics.success(fn, "${result.size} receipts") else AppDiagnostics.failed(fn, "Invalid response JSON"); main.post { onResult(result) } } }
        })
    }
}
