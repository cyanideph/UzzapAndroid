package com.kolipri.uzzap.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kolipri.uzzap.R
import com.kolipri.uzzap.ui.PrivateChatActivity

object UzzapNotificationManager {
    private const val CHANNEL_ID = "uzzap_messages"
    private const val PREFS = "uzzap_notification_state"

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Uzzap messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Private messages and Uzzap invitations"
            })
        }
    }

    fun notifyPrivateMessages(context: Context, count: Int, senders: List<String>) {
        if (count <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        initialize(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signature = "$count:${senders.sorted().joinToString(",")}"
        if (prefs.getString("last_signature", "") == signature) return
        prefs.edit().putString("last_signature", signature).apply()
        val sender = senders.firstOrNull() ?: "Uzzap"
        val intent = PrivateChatActivity.intent(context, sender)
        val pending = PendingIntent.getActivity(context, sender.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = if (count == 1) "New private message from $sender" else "$count unread private messages"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.d_buddylist_icon)
            .setContentTitle("Uzzap")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1001, notification)
        }
    }

    fun clearPrivateMessageNotification(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(1001)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("last_signature").apply()
    }
}
