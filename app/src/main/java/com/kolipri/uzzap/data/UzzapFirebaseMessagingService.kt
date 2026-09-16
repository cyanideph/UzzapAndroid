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
        val type = data["type"] ?: "private_message"
        val sender = (data["sender"] ?: remoteMessage.notification?.title ?: "Uzzap")
            .removePrefix("Message from ")
            .removePrefix("Buddy request from ")
            .removePrefix("Room invitation from ")
            .trim()
        val body = data["body"] ?: remoteMessage.notification?.body ?: ""
        val eventId = data["event_id"]?.toLongOrNull()

        when (type) {
            "private_message" -> {
                val activeBuddy = AppState.activeChatBuddy
                if (activeBuddy != null && activeBuddy.equals(sender, ignoreCase = true)) {
                    Log.d(TAG, "Message received from active chat buddy $sender; playing subtle sound")
                    UzzapNotificationManager.playAlertSound(applicationContext)
                    return
                }
                UzzapNotificationManager.notifyPrivateMessages(
                    context = applicationContext,
                    count = 1,
                    senders = listOf(sender),
                    messageIds = listOfNotNull(eventId),
                    customBody = body.ifBlank { "New private message" }
                )
            }
            "buddy_request" -> UzzapNotificationManager.notifyBuddyRequest(
                applicationContext, sender, eventId, body
            )
            "room_invitation" -> UzzapNotificationManager.notifyRoomInvitation(
                applicationContext, sender, data["room_name"].orEmpty(), eventId, body
            )
            else -> UzzapNotificationManager.notifyGeneric(
                applicationContext, sender.ifBlank { "Uzzap" }, body.ifBlank { "You have a new Uzzap notification" }, eventId
            )
        }
    }

    companion object {
        private const val TAG = "UzzapFirebaseService"
    }
}
