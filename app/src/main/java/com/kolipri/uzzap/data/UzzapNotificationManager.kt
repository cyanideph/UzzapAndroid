package com.kolipri.uzzap.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kolipri.uzzap.R
import com.kolipri.uzzap.ui.PrivateChatActivity

object UzzapNotificationManager {
    private const val CHANNEL_ID = "uzzap_messages_v2"
    private const val PREFS = "uzzap_notification_state"
    private const val KEY_NOTIFIED_IDS = "notified_message_ids"
    private const val NOTIFICATION_ID = 1001
    private const val MAX_TRACKED_IDS = 500

    fun getAlertSoundUri(context: Context): Uri {
        return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.alert}")
    }

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val soundUri = getAlertSoundUri(context)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(CHANNEL_ID, "Uzzap messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Private messages and Uzzap invitations"
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun readNotifiedIds(context: Context): MutableSet<Long> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_NOTIFIED_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toMutableSet()
    }

    private fun saveNotifiedIds(context: Context, ids: Set<Long>) {
        val trimmed = ids.toList().sortedDescending().take(MAX_TRACKED_IDS).map(Long::toString).toSet()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_NOTIFIED_IDS, trimmed)
            .apply()
    }

    fun playAlertSound(context: Context) {
        runCatching {
            val mp = MediaPlayer.create(context.applicationContext, R.raw.alert)
            mp?.setOnCompletionListener { it.release() }
            mp?.start()
        }
    }

    fun notifyPrivateMessages(
        context: Context,
        count: Int,
        senders: List<String>,
        messageIds: List<Long> = emptyList(),
        customBody: String? = null
    ) {
        if (count <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        initialize(context)
        val cleanSenders = senders.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val sender = cleanSenders.firstOrNull() ?: "Uzzap"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val notifiedIds = readNotifiedIds(context)
        val newMessageIds = messageIds.filter { it > 0L && !notifiedIds.contains(it) }

        // The unread poll runs repeatedly. Do not alert again for the same unread messages.
        // A new notification is only raised when at least one previously unseen message ID appears.
        if (messageIds.isNotEmpty() && newMessageIds.isEmpty()) return

        if (messageIds.isNotEmpty()) {
            notifiedIds.addAll(newMessageIds)
            saveNotifiedIds(context, notifiedIds)
        }

        val pending = PendingIntent.getActivity(
            context,
            sender.lowercase().hashCode(),
            PrivateChatActivity.intent(context, sender),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = customBody ?: when {
            count == 1 -> "New private message from $sender"
            cleanSenders.size == 1 -> "$count unread messages from $sender"
            else -> "$count unread private messages from ${cleanSenders.size} people"
        }
        val soundUri = getAlertSoundUri(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.d_buddylist_icon)
            .setContentTitle("Uzzap")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setSound(soundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        }
        // Do not manually play the sound here. The Android notification channel handles
        // the alert, preventing a second sound on every unread polling cycle.
    }

    fun sendTestNotification(
        context: Context,
        sender: String = "uzzapbot",
        messageText: String = "Kamusta cy! Welcome to Uzzap. This is a test message with alert sound :)"
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        notifyPrivateMessages(
            context = context,
            count = 1,
            senders = listOf(sender),
            messageIds = listOf(System.currentTimeMillis()),
            customBody = messageText
        )
    }

    fun clearPrivateMessageNotification(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
