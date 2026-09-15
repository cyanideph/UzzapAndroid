package com.kolipri.uzzap.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kolipri.uzzap.R
import com.kolipri.uzzap.ui.PrivateChatActivity

object UzzapNotificationManager {
    private const val CHANNEL_ID = "uzzap_messages_v2"
    private const val PREFS = "uzzap_notification_state"
    private const val NOTIFICATION_ID = 1001

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
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // Still play in-app sound if permission is not granted
            playAlertSound(context)
            return
        }

        initialize(context)
        val cleanSenders = senders.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val sender = cleanSenders.firstOrNull() ?: "Uzzap"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signature = "$count:${cleanSenders.sorted().joinToString(",")}:${messageIds.sorted().joinToString(",")}:$customBody"
        if (prefs.getString("last_signature", "") == signature) return
        prefs.edit().putString("last_signature", signature).apply()

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

        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification) }
        playAlertSound(context)
    }

    fun sendTestNotification(
        context: Context,
        sender: String = "uzzapbot",
        messageText: String = "Kamusta cy! Welcome to Uzzap. This is a test message with alert sound :)"
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("last_signature").apply()
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("last_signature").apply()
    }
}
