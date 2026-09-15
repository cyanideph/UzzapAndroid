package com.kolipri.uzzap

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.PrivateMessageStateClient
import com.kolipri.uzzap.data.PresenceClient
import com.kolipri.uzzap.data.SessionStore
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.data.UzzapNotificationManager
import com.kolipri.uzzap.data.verifySession

class UzzapApplication : Application(), DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private val syncPresenceAndNotifications = object : Runnable {
        override fun run() {
            if (!foreground) return
            if (SupabaseClient.userAccessToken.isNullOrBlank()) {
                handler.postDelayed(this, 10_000L)
                return
            }
            PresenceClient.heartbeat(AppState.presence != AppState.presence.INVISIBLE)
            PrivateMessageStateClient.fetchUnread(AppState.username) { unread ->
                if (foreground && unread != null) {
                    val active = AppState.activeChatBuddy
                    val relevantSenders = if (active != null) unread.senders.filter { !it.equals(active, ignoreCase = true) } else unread.senders
                    val relevantCount = relevantSenders.sumOf { unread.countsBySender[it.lowercase()] ?: 1 }
                    if (relevantCount > 0) UzzapNotificationManager.notifyPrivateMessages(this@UzzapApplication, relevantCount, relevantSenders, unread.messageIds)
                    else if (active != null) UzzapNotificationManager.clearPrivateMessageNotification(this@UzzapApplication)
                }
            }
            handler.postDelayed(this, 10_000L)
        }
    }

    override fun onCreate() {
        super<Application>.onCreate()
        SessionStore.init(this)
        SupabaseClient.restorePersistedSession()
        // A persisted session may contain an older nickname-derived identity. Resolve the
        // authenticated Supabase user to its own profiles.owner_id before app features start.
        SupabaseClient.userAccessToken?.let { token ->
            SupabaseClient.verifySession(token) { _, _, _ -> }
        }
        UzzapNotificationManager.initialize(this)
        com.kolipri.uzzap.data.FcmClient.initializeAndSync(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        handler.removeCallbacks(syncPresenceAndNotifications)
        handler.post(syncPresenceAndNotifications)
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        handler.removeCallbacks(syncPresenceAndNotifications)
        if (!SupabaseClient.userAccessToken.isNullOrBlank()) PresenceClient.heartbeat(false)
    }
}
