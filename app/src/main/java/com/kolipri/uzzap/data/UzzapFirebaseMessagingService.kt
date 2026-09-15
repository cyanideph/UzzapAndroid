package com.kolipri.uzzap.data

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class UzzapFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received")
        FcmClient.saveTokenLocally(applicationContext, token)
        FcmClient.syncTokenWithSupabase(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val rawSender = data["sender"] ?: remoteMessage.notification?.title ?: "Uzzap"
        val cleanSender = rawSender.removePrefix("Message from ").trim()
        val body = data["body"] ?: remoteMessage.notification?.body ?: "New private message"

        // Avoid intrusive alert if user is actively in conversation with this buddy
        val activeBuddy = AppState.activeChatBuddy
        if (activeBuddy != null && activeBuddy.equals(cleanSender, ignoreCase = true)) {
            Log.d(TAG, "Message received from active chat buddy $cleanSender; playing subtle sound")
            UzzapNotificationManager.playAlertSound(applicationContext)
            return
        }

        UzzapNotificationManager.notifyPrivateMessages(
            context = applicationContext,
            count = 1,
            senders = listOf(cleanSender),
            customBody = body
        )
    }

    companion object {
        private const val TAG = "UzzapFirebaseService"
    }
}
