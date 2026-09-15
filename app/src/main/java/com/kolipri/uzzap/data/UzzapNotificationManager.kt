package com.kolipri.uzzap.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kolipri.uzzap.R
import com.kolipri.uzzap.ui.PrivateChatActivity

object UzzapNotificationManager {
    private const val CHANNEL_ID = "uzzap_messages"
    private const val PREFS = "uzzap_notification_state"
    private const val NOTIFICATION_ID = 1001

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Uzzap messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Private messages and Uzzap invitations"
            })
        }
    }

    fun notifyPrivateMessages(context: Context, count: Int, senders: List<String>, messageIds: List<Long> = emptyList()) {
        if (count <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        initialize(context)
        val cleanSenders = senders.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val sender = cleanSenders.firstOrNull() ?: "Uzzap"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signature = "$count:${cleanSenders.sorted().joinToString(",")}:${messageIds.sorted().joinToString(",")}"
        if (prefs.getString("last_signature", "") == signature) return
        prefs.edit().putString("last_signature", signature).apply()

        val pending = PendingIntent.getActivity(
            context,
            sender.lowercase().hashCode(),
            PrivateChatActivity.intent(context, sender),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = when {
            count == 1 -> "New private message from $sender"
            cleanSenders.size == 1 -> "$count unread messages from $sender"
            else -> "$count unread private messages from ${cleanSenders.size} people"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.d_buddylist_icon)
            .setContentTitle("Uzzap")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true)
            .build()

        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification) }
    }

    fun clearPrivateMessageNotification(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("last_signature").apply()
    }
}
