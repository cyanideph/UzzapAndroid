package com.kolipri.uzzap.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kolipri.uzzap.R
import com.kolipri.uzzap.ui.AppGridActivity
import com.kolipri.uzzap.ui.PrivateChatActivity

object UzzapNotificationManager {
    private const val CHANNEL_ID = "uzzap_messages_v2"
    private const val EVENT_CHANNEL_ID = "uzzap_events_v1"
    private const val PREFS = "uzzap_notification_state"
    private const val KEY_NOTIFIED_IDS = "notified_message_ids"
    private const val NOTIFICATION_ID = 1001
    private const val EVENT_NOTIFICATION_ID = 1002
    private const val MAX_TRACKED_IDS = 500

    fun getAlertSoundUri(context: Context): Uri =
        Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.alert}")

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Uzzap messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Private messages"
                setSound(getAlertSoundUri(context), audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            })
            manager.createNotificationChannel(NotificationChannel(EVENT_CHANNEL_ID, "Uzzap activity", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Buddy requests and room invitations"
                setSound(getAlertSoundUri(context), audioAttributes)
                enableVibration(true)
            })
        }
    }

    private fun readNotifiedIds(context: Context): MutableSet<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_NOTIFIED_IDS, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }.toMutableSet()

    private fun saveNotifiedIds(context: Context, ids: Set<Long>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_NOTIFIED_IDS, ids.toList().sortedDescending().take(MAX_TRACKED_IDS).map(Long::toString).toSet())
            .apply()
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun playAlertSound(context: Context) {
        runCatching {
            MediaPlayer.create(context.applicationContext, R.raw.alert)?.also { player ->
                player.setOnCompletionListener { it.release() }
                player.start()
            }
        }
    }

    fun notifyPrivateMessages(context: Context, count: Int, senders: List<String>, messageIds: List<Long> = emptyList(), customBody: String? = null) {
        if (count <= 0 || !canNotify(context)) return
        initialize(context)
        val cleanSenders = senders.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val sender = cleanSenders.firstOrNull() ?: "Uzzap"
        val notifiedIds = readNotifiedIds(context)
        val newIds = messageIds.filter { it > 0 && notifiedIds.add(it) }
        if (messageIds.isNotEmpty() && newIds.isEmpty()) return
        if (newIds.isNotEmpty()) saveNotifiedIds(context, notifiedIds)
        val text = customBody ?: when {
            count == 1 -> "New private message from $sender"
            cleanSenders.size == 1 -> "$count unread messages from $sender"
            else -> "$count unread private messages from ${cleanSenders.size} people"
        }
        val pending = PendingIntent.getActivity(
            context, sender.lowercase().hashCode(), PrivateChatActivity.intent(context, sender),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        post(context, NOTIFICATION_ID, CHANNEL_ID, "Uzzap", text, pending, NotificationCompat.CATEGORY_MESSAGE)
    }

    fun notifyBuddyRequest(context: Context, sender: String, eventId: Long?, body: String?) {
        notifyEvent(context, "Buddy request", "$sender wants to be your buddy${body?.takeIf { it.isNotBlank() }?.let { ": $it"} ?: ""}", eventId)
    }

    fun notifyRoomInvitation(context: Context, sender: String, roomName: String, eventId: Long?, body: String?) {
        val room = roomName.ifBlank { "a chatroom" }
        notifyEvent(context, "Room invitation", "$sender invited you to $room${body?.takeIf { it.isNotBlank() }?.let { ": $it"} ?: ""}", eventId)
    }

    fun notifyGeneric(context: Context, title: String, body: String, eventId: Long?) {
        notifyEvent(context, title, body, eventId)
    }

    private fun notifyEvent(context: Context, title: String, body: String, eventId: Long?) {
        if (!canNotify(context)) return
        initialize(context)
        val key = eventId ?: System.currentTimeMillis()
        val pending = PendingIntent.getActivity(
            context, key.hashCode(), Intent(context, AppGridActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        post(context, EVENT_NOTIFICATION_ID, EVENT_CHANNEL_ID, title, body, pending, NotificationCompat.CATEGORY_SOCIAL)
    }

    private fun post(context: Context, id: Int, channelId: String, title: String, text: String, pending: PendingIntent, category: String) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.d_buddylist_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(category)
            .build()
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, notification) }
    }

    fun sendTestNotification(context: Context, sender: String = "uzzapbot", messageText: String = "Kamusta cy! Welcome to Uzzap. This is a test message with alert sound :)") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        notifyPrivateMessages(context, 1, listOf(sender), listOf(System.currentTimeMillis()), messageText)
    }

    fun clearPrivateMessageNotification(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
