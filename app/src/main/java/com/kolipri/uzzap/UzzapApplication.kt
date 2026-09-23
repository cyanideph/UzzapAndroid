package com.kolipri.uzzap

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kolipri.uzzap.data.AppDiagnostics
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.CrashDiagnostics
import com.kolipri.uzzap.data.PrivateMessageStateClient
import com.kolipri.uzzap.data.PresenceClient
import com.kolipri.uzzap.data.SessionStore
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.data.UzzapNotificationManager
import com.kolipri.uzzap.data.verifySession
import com.kolipri.uzzap.model.Presence

class UzzapApplication : Application(), DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private val syncPresenceAndNotifications = object : Runnable {
        override fun run() {
            if (!foreground) return
            if (SupabaseClient.userAccessToken.isNullOrBlank()) { handler.postDelayed(this, 30_000L); return }
            val presenceOp = AppDiagnostics.begin("Presence.heartbeat")
            runCatching { PresenceClient.heartbeat(AppState.presence != Presence.INVISIBLE); presenceOp.pass() }
                .onFailure { presenceOp.fail(it.message ?: it.javaClass.simpleName); AppDiagnostics.exception(this@UzzapApplication, "Presence", it) }
            val unreadOp = AppDiagnostics.begin("Notifications.fetchUnread", "user=${AppState.username}")
            runCatching {
                PrivateMessageStateClient.fetchUnread(AppState.username) { unread ->
                    if (unread != null) {
                        unreadOp.pass("${unread.messageIds.size} unread messages")
                        if (foreground) {
                            val active = AppState.activeChatBuddy
                            val relevantSenders = if (active != null) unread.senders.filter { !it.equals(active, ignoreCase = true) } else unread.senders
                            val relevantCount = relevantSenders.sumOf { unread.countsBySender[it.lowercase()] ?: 1 }
                            if (relevantCount > 0) UzzapNotificationManager.notifyPrivateMessages(this@UzzapApplication, relevantCount, relevantSenders, unread.messageIds)
                            else if (active != null) UzzapNotificationManager.clearPrivateMessageNotification(this@UzzapApplication)
                        }
                    } else unreadOp.fail("Unread check returned no data")
                }
            }.onFailure { unreadOp.fail(it.message ?: it.javaClass.simpleName); AppDiagnostics.exception(this@UzzapApplication, "UnreadMessages", it) }
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate() {
        super<Application>.onCreate()
        AppDiagnostics.init(this)
        CrashDiagnostics.install(this)
        AppDiagnostics.info(this, "Sentry.initialize", "Disabled: Sentry removed from Uzzap")
        AppDiagnostics.info(this, "Application", "UzzapApplication started; whole-app monitor enabled")
        AppDiagnostics.register(
            "Auth.Login", "Auth.Signup", "Auth.Logout", "Auth.RestoreSession", "Auth.verifySession",
            "Profile.Load", "Profile.Update", "Profile.ChangeNickname", "Profile.ChangeNicknameColor",
            "Rooms.LoadCategories", "Rooms.LoadProvinces", "Rooms.LoadList", "Room.join", "Room.leave",
            "Room.history", "Room.historyOlder", "Room.participants", "Room.sendMessage", "Room.realtimeMessage",
            "Presence.heartbeat", "Presence.join", "Presence.leave", "Presence.ping",
            "Buddies.load", "BuddyRequests.fetchPending", "BuddyRequests.send", "BuddyRequests.accept", "BuddyRequests.reject",
            "PrivateChat.open", "PrivateChat.load", "PrivateChat.send", "PrivateChat.delete", "PrivateChat.block", "PrivateChat.report",
            "Receipts.delivery", "Receipts.read", "Notifications.initialize", "Notifications.FCM.initialize", "Notifications.fetchUnread",
            "Invitations.create", "Invitations.accept", "Invitations.reject",
            "Moderation.loadReports", "Moderation.deleteMessage", "Moderation.kick", "Moderation.mute", "Moderation.sanction", "Moderation.roles",
            "Settings.open", "Settings.profile", "Settings.notifications", "Settings.privacy", "Settings.appearance", "Settings.about",
            "Diagnostics.open", "Diagnostics.runChecks", "Diagnostics.export", "Diagnostics.clear",
            "Supabase.health", "Supabase.auth", "Supabase.database", "CrashDiagnostics.capture"
        )
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = AppDiagnostics.monitorActivity(activity, "created")
            override fun onActivityStarted(activity: Activity) = AppDiagnostics.monitorActivity(activity, "started")
            override fun onActivityResumed(activity: Activity) = AppDiagnostics.monitorActivity(activity, "resumed")
            override fun onActivityPaused(activity: Activity) = AppDiagnostics.monitorActivity(activity, "paused")
            override fun onActivityStopped(activity: Activity) = AppDiagnostics.monitorActivity(activity, "stopped")
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = AppDiagnostics.monitorActivity(activity, "destroyed")
        })

        runCatching { SessionStore.init(this); AppDiagnostics.ok(this, "SessionStore", "Initialized") }.onFailure { AppDiagnostics.exception(this, "SessionStore", it); CrashDiagnostics.write(this, Thread.currentThread(), it) }
        runCatching { SupabaseClient.restorePersistedSession(); AppDiagnostics.ok(this, "Session", "Persisted session restored") }.onFailure { AppDiagnostics.exception(this, "SessionRestore", it); CrashDiagnostics.write(this, Thread.currentThread(), it) }
        runCatching { SupabaseClient.userAccessToken?.let { token -> SupabaseClient.verifySession(token) { success, username, _ -> if (success) AppDiagnostics.success("Auth.verifySession", "Verified $username") else AppDiagnostics.failed("Auth.verifySession", "Persisted session could not be verified") } } ?: AppDiagnostics.info(this, "Auth", "No persisted session") }.onFailure { AppDiagnostics.exception(this, "Auth", it) }
        runCatching { UzzapNotificationManager.initialize(this); AppDiagnostics.success("Notifications.initialize") }.onFailure { AppDiagnostics.exception(this, "Notifications", it) }
        runCatching { com.kolipri.uzzap.data.FcmClient.initializeAndSync(this); AppDiagnostics.success("Notifications.FCM.initialize") }.onFailure { AppDiagnostics.exception(this, "FCM", it) }
        runCatching { ProcessLifecycleOwner.get().lifecycle.addObserver(this); AppDiagnostics.success("Lifecycle.installObserver") }.onFailure { AppDiagnostics.exception(this, "Lifecycle", it) }
    }

    override fun onStart(owner: LifecycleOwner) { foreground = true; AppDiagnostics.info(this, "Application", "Entered foreground"); handler.removeCallbacks(syncPresenceAndNotifications); handler.post(syncPresenceAndNotifications) }
    override fun onStop(owner: LifecycleOwner) { foreground = false; AppDiagnostics.info(this, "Application", "Entered background"); handler.removeCallbacks(syncPresenceAndNotifications); runCatching { if (!SupabaseClient.userAccessToken.isNullOrBlank()) PresenceClient.heartbeat(false) }.onFailure { AppDiagnostics.exception(this, "Presence", it) } }
}