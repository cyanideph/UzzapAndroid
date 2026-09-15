package com.kolipri.uzzap.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.model.DirectMessage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Supabase Realtime WebSocket client for instant room and private messaging.
 * Uses Phoenix Channels protocol over wss:// with automatic reconnection and keepalive.
 */
object SupabaseRealtimeClient {
    private const val TAG = "SupabaseRealtime"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val refCounter = AtomicLong(1)
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var isConnecting = false
    private var shouldBeConnected = false

    private val roomMessageListeners = CopyOnWriteArrayList<(ChatMessage, String) -> Unit>()
    private val directMessageListeners = CopyOnWriteArrayList<(DirectMessage) -> Unit>()
    private val connectionListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (isConnected && webSocket != null) {
                val ref = refCounter.incrementAndGet().toString()
                val json = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", ref)
                }
                webSocket?.send(json.toString())
                mainHandler.postDelayed(this, 25_000L)
            }
        }
    }

    private val reconnectRunnable = Runnable {
        if (shouldBeConnected && !isConnected && !isConnecting) {
            connect()
        }
    }

    fun start() {
        shouldBeConnected = true
        if (!isConnected && !isConnecting) {
            connect()
        }
    }

    fun stop() {
        shouldBeConnected = false
        mainHandler.removeCallbacks(heartbeatRunnable)
        mainHandler.removeCallbacks(reconnectRunnable)
        webSocket?.close(1000, "Client stop")
        webSocket = null
        isConnected = false
        isConnecting = false
        notifyConnection(false)
    }

    fun addRoomMessageListener(listener: (ChatMessage, String) -> Unit) {
        roomMessageListeners.add(listener)
    }

    fun removeRoomMessageListener(listener: (ChatMessage, String) -> Unit) {
        roomMessageListeners.remove(listener)
    }

    fun addDirectMessageListener(listener: (DirectMessage) -> Unit) {
        directMessageListeners.add(listener)
    }

    fun removeDirectMessageListener(listener: (DirectMessage) -> Unit) {
        directMessageListeners.remove(listener)
    }

    fun addConnectionListener(listener: (Boolean) -> Unit) {
        connectionListeners.add(listener)
        listener(isConnected)
    }

    fun removeConnectionListener(listener: (Boolean) -> Unit) {
        connectionListeners.remove(listener)
    }

    private fun notifyConnection(connected: Boolean) {
        mainHandler.post {
            connectionListeners.forEach { it(connected) }
        }
    }

    @Synchronized
    private fun connect() {
        if (isConnected || isConnecting) return
        val rawUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        if (rawUrl.isBlank()) return
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        val wsUrl = (if (rawUrl.startsWith("https://")) "wss://" + rawUrl.removePrefix("https://")
                     else "ws://" + rawUrl.removePrefix("http://")) +
                     "/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"

        isConnecting = true
        val request = Request.Builder().url(wsUrl).build()
        webSocket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Realtime WebSocket connected")
                isConnecting = false
                isConnected = true
                notifyConnection(true)
                mainHandler.removeCallbacks(heartbeatRunnable)
                mainHandler.postDelayed(heartbeatRunnable, 25_000L)

                // Join room_messages channel
                joinChannel(ws, "realtime:public:room_messages", "room_messages")
                // Join direct_messages channel
                joinChannel(ws, "realtime:public:direct_messages", "direct_messages")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Realtime WebSocket closed: $code / $reason")
                handleDisconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Realtime WebSocket error", t)
                handleDisconnect()
            }
        })
    }

    private fun joinChannel(ws: WebSocket, topic: String, table: String) {
        val ref = refCounter.incrementAndGet().toString()
        val payload = JSONObject().apply {
            val config = JSONObject().apply {
                val postgresChanges = org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("event", "INSERT")
                        put("schema", "public")
                        put("table", table)
                    })
                }
                put("postgres_changes", postgresChanges)
            }
            put("config", config)
        }
        val joinMsg = JSONObject().apply {
            put("topic", topic)
            put("event", "phx_join")
            put("payload", payload)
            put("ref", ref)
        }
        ws.send(joinMsg.toString())
    }

    private fun handleMessage(text: String) {
        try {
            val obj = JSONObject(text)
            val event = obj.optString("event")
            if (event == "postgres_changes") {
                val payload = obj.optJSONObject("payload")
                val data = payload?.optJSONObject("data")
                val table = data?.optString("table")
                val record = data?.optJSONObject("record")
                if (record != null) {
                    when (table) {
                        "room_messages" -> {
                            val room = record.optString("room_name")
                            val msg = ChatMessage(
                                sender = record.optString("sender"),
                                body = record.optString("body"),
                                isSystem = record.optBoolean("is_system"),
                                id = record.optLong("id"),
                                createdAt = record.optString("created_at")
                            )
                            mainHandler.post {
                                roomMessageListeners.forEach { it(msg, room) }
                            }
                        }
                        "direct_messages" -> {
                            val dm = DirectMessage(
                                id = record.optLong("id"),
                                sender = record.optString("sender"),
                                recipient = record.optString("recipient"),
                                body = record.optString("body"),
                                createdAt = record.optString("created_at"),
                                read = record.optBoolean("is_read")
                            )
                            mainHandler.post {
                                directMessageListeners.forEach { it(dm) }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Realtime frame", e)
        }
    }

    private fun handleDisconnect() {
        isConnected = false
        isConnecting = false
        mainHandler.removeCallbacks(heartbeatRunnable)
        notifyConnection(false)
        if (shouldBeConnected) {
            mainHandler.removeCallbacks(reconnectRunnable)
            mainHandler.postDelayed(reconnectRunnable, 5_000L)
        }
    }
}
