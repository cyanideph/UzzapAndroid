package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.model.DirectMessage
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Supabase Realtime client for room and direct messages. */
object SupabaseRealtimeClient {
    private const val TAG = "SupabaseRealtime"
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
    private val refs = AtomicLong(1)
    private var ws: WebSocket? = null
    @Volatile private var connected = false
    private var connecting = false
    private var shouldConnect = false\n    /** Number of active UI/session consumers holding the realtime connection. */\n    private var connectionUsers = 0

    private val roomListeners = CopyOnWriteArrayList<(ChatMessage, String) -> Unit>()
    private val roomDeleteListeners = CopyOnWriteArrayList<(Long, String) -> Unit>()
    private val directListeners = CopyOnWriteArrayList<(DirectMessage) -> Unit>()
    private val connectionListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    private val heartbeat = object : Runnable {
        override fun run() {
            if (connected) {
                ws?.send(
                    JSONObject()
                        .put("topic", "phoenix")
                        .put("event", "heartbeat")
                        .put("payload", JSONObject())
                        .put("ref", refs.incrementAndGet())
                        .toString()
                )
                main.postDelayed(this, 25000)
            }
        }
    }

    private val reconnect = Runnable {
        if (shouldConnect && !connected && !connecting) connect()
    }

    fun start() {
        connectionUsers++
        shouldConnect = true
        if (!connected && !connecting) connect()
    }

    fun stop() {
        if (connectionUsers > 0) connectionUsers--
        if (connectionUsers > 0) return
        shouldConnect = false
        main.removeCallbacks(heartbeat)
        main.removeCallbacks(reconnect)
        ws?.close(1000, "Client stop")
        ws = null
        connected = false
        connecting = false
        notifyConnection(false)
    }

    /** True when the room/direct-message WebSocket is currently connected. */
    fun isConnected(): Boolean = connected

    /** Reconnects using the current Supabase access token after session refresh. */
    fun refreshSubscriptions() { refreshAuth() }

    fun refreshAuth() {
        if (!shouldConnect) return
        main.post {
            if (!shouldConnect) return@post
            main.removeCallbacks(reconnect)
            ws?.close(1000, "Session refreshed")
            ws = null
            connected = false
            connecting = false
            main.removeCallbacks(heartbeat)
            notifyConnection(false)
            connect()
        }
    }

    fun addRoomMessageListener(l: (ChatMessage, String) -> Unit) { roomListeners.add(l) }
    fun removeRoomMessageListener(l: (ChatMessage, String) -> Unit) { roomListeners.remove(l) }
    fun addRoomMessageDeletedListener(l: (Long, String) -> Unit) { roomDeleteListeners.add(l) }
    fun removeRoomMessageDeletedListener(l: (Long, String) -> Unit) { roomDeleteListeners.remove(l) }
    fun addDirectMessageListener(l: (DirectMessage) -> Unit) { directListeners.add(l) }
    fun removeDirectMessageListener(l: (DirectMessage) -> Unit) { directListeners.remove(l) }

    fun addConnectionListener(l: (Boolean) -> Unit) {
        connectionListeners.add(l)
        l(connected)
    }

    fun removeConnectionListener(l: (Boolean) -> Unit) { connectionListeners.remove(l) }

    private fun notifyConnection(v: Boolean) {
        main.post { connectionListeners.forEach { it(v) } }
    }

    @Synchronized
    private fun connect() {
        if (connected || connecting) return
        val raw = BuildConfig.SUPABASE_URL.trimEnd('/')
        if (raw.isBlank()) return
        val key = BuildConfig.SUPABASE_ANON_KEY
        val base = if (raw.startsWith("https://")) "wss://" + raw.removePrefix("https://") else "ws://" + raw.removePrefix("http://")
        val u = "$base/realtime/v1/websocket?apikey=$key&vsn=1.0.0"
        connecting = true
        ws = http.newWebSocket(Request.Builder().url(u).build(), object : WebSocketListener() {
            override fun onOpen(s: WebSocket, r: Response) {
                // refreshAuth() can replace a socket before the old socket finishes
                // closing. Never let a stale callback take ownership of the new
                // connection or tear it down.
                if (ws !== s || !shouldConnect) {
                    s.close(1000, "Stale socket")
                    return
                }
                connecting = false
                connected = true
                notifyConnection(true)
                main.postDelayed(heartbeat, 25000)
                joinRealtimeTables(s)
            }

            override fun onMessage(s: WebSocket, text: String) {
                if (ws === s && connected) handle(text)
            }

            override fun onClosed(s: WebSocket, c: Int, r: String) {
                if (ws === s) disconnect(s)
            }

            override fun onFailure(s: WebSocket, t: Throwable, r: Response?) {
                if (ws === s) disconnect(s)
            }

            override fun onClosing(s: WebSocket, c: Int, r: String) {
                if (ws === s) s.close(1000, null) else s.close(1000, "Stale socket")
            }
        })
    }

    private fun joinRealtimeTables(s: WebSocket) {
        val accessToken = SupabaseClient.userAccessToken.orEmpty()
        val joinedRooms = AppState.joinedRooms.distinct().filter { it.isNotBlank() }

        // Never subscribe to the entire room_messages table. Split large room sets into
        // independently named Realtime topics so the filter remains bounded and hidden/
        // unjoined rooms are never pulled into this client.
        joinedRooms.chunked(100).forEachIndexed { chunkIndex, chunk ->
            val roomFilter = chunk.joinToString(prefix = "room_name=in.(", postfix = ")", separator = ",") { quoteRealtimeFilterValue(it) }
            val roomChanges = JSONArray()
                .put(JSONObject().apply {
                    put("event", "INSERT"); put("schema", "public"); put("table", "room_messages")
                    put("filter", roomFilter)
                    put("select", JSONArray().put("id").put("sender").put("body").put("is_system").put("created_at").put("room_name"))
                })
                .put(JSONObject().apply {
                    put("event", "UPDATE"); put("schema", "public"); put("table", "room_messages")
                    put("filter", roomFilter)
                    put("select", JSONArray().put("id").put("sender").put("body").put("is_system").put("created_at").put("room_name"))
                })
                .put(JSONObject().apply {
                    put("event", "DELETE"); put("schema", "public"); put("table", "room_messages")
                    put("filter", roomFilter)
                    put("select", JSONArray().put("id").put("sender").put("body").put("is_system").put("created_at").put("room_name"))
                })
            s.send(
                JSONObject()
                    .put("topic", "realtime:public:room_messages_$chunkIndex")
                    .put("event", "phx_join")
                    .put("payload", JSONObject().put("config", JSONObject().put("postgres_changes", roomChanges)).put("access_token", accessToken))
                    .put("ref", refs.incrementAndGet())
                    .toString()
            )
        }

        val currentUser = AppState.username.trim()
        if (currentUser.isBlank()) return
        val dmChanges = JSONArray()
            .put(JSONObject().apply {
                put("event", "INSERT"); put("schema", "public"); put("table", "direct_messages")
                put("filter", "sender=eq." + quoteRealtimeFilterValue(currentUser))
                put("select", JSONArray().put("id").put("sender").put("recipient").put("body").put("created_at").put("is_read"))
            })
            .put(JSONObject().apply {
                put("event", "INSERT"); put("schema", "public"); put("table", "direct_messages")
                put("filter", "recipient=eq." + quoteRealtimeFilterValue(currentUser))
                put("select", JSONArray().put("id").put("sender").put("recipient").put("body").put("created_at").put("is_read"))
            })
            .put(JSONObject().apply {
                put("event", "UPDATE"); put("schema", "public"); put("table", "direct_messages")
                put("filter", "recipient=eq." + quoteRealtimeFilterValue(currentUser))
                put("select", JSONArray().put("id").put("sender").put("recipient").put("body").put("created_at").put("is_read"))
            })
        s.send(
            JSONObject()
                .put("topic", "realtime:public:direct_messages")
                .put("event", "phx_join")
                .put("payload", JSONObject().put("config", JSONObject().put("postgres_changes", dmChanges)).put("access_token", accessToken))
                .put("ref", refs.incrementAndGet())
                .toString()
        )
    }

    private fun quoteRealtimeFilterValue(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

        return "\"$escaped\""
    }

    private fun handle(text: String) {
        runCatching {
            val o = JSONObject(text)
            if (o.optString("event") != "postgres_changes") return
            val d = o.optJSONObject("payload")?.optJSONObject("data") ?: return
            val table = d.optString("table")
            val changeType = d.optString("type", o.optString("type")).uppercase()
            val rec = d.optJSONObject("record")
            val old = d.optJSONObject("old_record")

            when (table) {
                "room_messages" -> {
                    if (changeType == "DELETE") {
                        val rawId = old?.opt("id") ?: rec?.opt("id")
                        val id = when (rawId) {
                            is Number -> rawId.toLong()
                            is String -> rawId.toLongOrNull() ?: MessageIdRegistry.register(rawId)
                            else -> 0L
                        }
                        val roomName = (old?.optString("room_name") ?: rec?.optString("room_name")).orEmpty()
                        if (id > 0 && roomName.isNotBlank()) {
                            main.post { roomDeleteListeners.forEach { it(id, roomName) } }
                        }
                        return@runCatching
                    }
                    if (rec == null) return@runCatching
                    val rawId = rec.opt("id")
                    val id = when (rawId) {
                        is Number -> rawId.toLong()
                        is String -> rawId.toLongOrNull() ?: MessageIdRegistry.register(rawId)
                        else -> 0L
                    }
                    val sender = rec.optString("sender")
                    val body = rec.optString("body")
                    val isSystem = rec.optBoolean("is_system", false)
                    val createdAt = rec.optString("created_at")
                    val roomName = rec.optString("room_name")
                    if (roomName.isNotBlank() && sender.isNotBlank()) {
                        val msg = ChatMessage(sender, body, isSystem, id, createdAt)
                        main.post { roomListeners.forEach { it(msg, roomName) } }
                    }
                }
                "direct_messages" -> {
                    if (rec == null) return@runCatching
                    val rawId = rec.opt("id")
                    val id = when (rawId) {
                        is Number -> rawId.toLong()
                        is String -> rawId.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    val sender = rec.optString("sender")
                    val recipient = rec.optString("recipient")
                    val body = rec.optString("body")
                    val createdAt = rec.optString("created_at")
                    val isRead = rec.optBoolean("is_read", false)
                    if (sender.isNotBlank() && recipient.isNotBlank()) {
                        val dm = DirectMessage(id, sender, recipient, body, createdAt, isRead)
                        main.post { directListeners.forEach { it(dm) } }
                    }
                }
            }
        }.onFailure { e -> Log.w(TAG, "Realtime parse error", e) }
    }

    private fun disconnect(socket: WebSocket) {
        if (ws !== socket) return
        ws = null
        connected = false
        connecting = false
        main.removeCallbacks(heartbeat)
        notifyConnection(false)
        if (shouldConnect) {
            main.removeCallbacks(reconnect)
            main.postDelayed(reconnect, 5000)
        }
    }
}
