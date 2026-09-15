package com.kolipri.uzzap.ui

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.DirectMessageClient
import com.kolipri.uzzap.data.PresenceClient
import com.kolipri.uzzap.data.PrivateMessageStateClient
import com.kolipri.uzzap.data.SafetyClient
import com.kolipri.uzzap.data.SupabaseRealtimeClient
import com.kolipri.uzzap.data.UzzapNotificationManager
import com.kolipri.uzzap.model.DirectMessage
import com.kolipri.uzzap.ui.dialogs.EmoticonPickerDialog
import com.kolipri.uzzap.ui.dialogs.J2mePopup
import com.kolipri.uzzap.ui.dialogs.PopupItem
import com.kolipri.uzzap.ui.util.EmoticonParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Compact Java Uzzap/J2ME-style private-message screen. */
class PrivateChatActivity : BaseChromeActivity() {
    private lateinit var root: LinearLayout
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var buddy = ""
    private var lastId = 0L
    private val listener: (DirectMessage) -> Unit = { dm ->
        if (!isFinishing && ((dm.sender.equals(buddy, true) && dm.recipient.equals(AppState.username, true)) || (dm.sender.equals(AppState.username, true) && dm.recipient.equals(buddy, true))) && dm.id > lastId) { lastId = dm.id; append(dm); markRead() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buddy = intent.getStringExtra(EXTRA_BUDDY).orEmpty()
        if (buddy.isBlank()) { finish(); return }
        buildUi()
        setScreenTitle("PM - $buddy")
        showConnectedBall(true)
        setSoftkeys("Options", "Back", onLeft = { showOptions() }, onRight = { finish() })
        SupabaseRealtimeClient.addDirectMessageListener(listener)
        load(); loadPresence()
    }
    private fun buildUi() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(activeTheme.background) }
        contentFrame.addView(root, FrameLayout.LayoutParams(-1, -1))
        val header = TextView(this).apply { text = "$buddy  •  Private Message"; textSize = 13f; setTextColor(activeTheme.text); gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, dp(4), 0); background = LegacyThemeManager.cardBackground(activeTheme) }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(24)))
        status = TextView(this).apply { textSize = 11f; setTextColor(activeTheme.secondary); setPadding(dp(4), 0, dp(4), 0) }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(18)))
        val scroll = androidx.core.widget.NestedScrollView(this).apply { isFillViewport = true; setBackgroundColor(activeTheme.background) }
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(2), dp(2), dp(2)) }
        scroll.addView(messages, FrameLayout.LayoutParams(-1, -2)); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(2), dp(2), dp(2), dp(2)); background = LegacyThemeManager.cardBackground(activeTheme) }
        val emoji = TextView(this).apply { text = ":)"; textSize = 13f; gravity = Gravity.CENTER; setTextColor(activeTheme.text); setOnClickListener { EmoticonPickerDialog.show(this@PrivateChatActivity) { code -> input.append(if (input.text.isEmpty()) code else " $code") } } }
        composer.addView(emoji, LinearLayout.LayoutParams(dp(32), dp(30)))
        input = EditText(this).apply { textSize = 13f; singleLine = true; hint = "Message"; setTextColor(activeTheme.text); setHintTextColor(activeTheme.secondary); setPadding(dp(3), 0, dp(3), 0); background = null }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(30), 1f))
        val send = TextView(this).apply { text = "Send"; textSize = 12f; gravity = Gravity.CENTER; setTextColor(activeTheme.text); setPadding(dp(3), 0, dp(3), 0); setOnClickListener { send() } }
        composer.addView(send, LinearLayout.LayoutParams(dp(42), dp(30))); root.addView(composer, LinearLayout.LayoutParams(-1, dp(34)))
    }
    private fun append(dm: DirectMessage) {
        val mine = dm.sender.equals(AppState.username, true)
        val row = TextView(this).apply {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            text = EmoticonParser.parse(context, if (mine) "< $time > ${dm.body}" else "* ${dm.sender}:\n  ${dm.body}", 13f)
            textSize = 13f; setTextColor(if (mine) activeTheme.text else activeTheme.specialText); setPadding(dp(2), dp(1), dp(2), dp(1)); gravity = Gravity.TOP or Gravity.START
        }
        messages.addView(row, LinearLayout.LayoutParams(-1, -2)); messages.post { (messages.parent as? androidx.core.widget.NestedScrollView)?.fullScroll(View.FOCUS_DOWN) }
    }
    private fun load() { DirectMessageClient.fetchConversation(AppState.username, buddy, 0) { list -> if (list == null) { Toast.makeText(this, "Could not load messages", Toast.LENGTH_SHORT).show(); return@fetchConversation }; messages.removeAllViews(); lastId = 0L; list.forEach { if (it.id > lastId) lastId = it.id; append(it) }; markRead() } }
    private fun send() { val body = input.text.toString().trim(); if (body.isEmpty()) return; SafetyClient.isBlocked(buddy) { blocked -> if (blocked) { Toast.makeText(this, "$buddy is blocked", Toast.LENGTH_SHORT).show(); return@isBlocked }; DirectMessageClient.sendMessage(AppState.username, buddy, body) { sent -> if (sent == null) Toast.makeText(this, "Message could not be sent", Toast.LENGTH_SHORT).show() else { input.setText(""); if (sent.id > lastId) { lastId = sent.id; append(sent) } } } } }
    private fun loadPresence() { PresenceClient.fetch(buddy) { p -> status.text = if (p?.isOnline == true) "Online" else "Offline" } }
    private fun showOptions() { J2mePopup.show(this, listOf(PopupItem("Send Message"), PopupItem("Send Emoticon"), PopupItem("View Profile"), PopupItem("Block User"), PopupItem("Report User"), PopupItem("Close"))) { index -> when (index) { 0 -> input.requestFocus(); 1 -> EmoticonPickerDialog.show(this) { code -> input.append(" $code") }; 2 -> viewProfile(); 3 -> blockUser(); 4 -> reportUser(); else -> finish() } } }
    private fun viewProfile() { PresenceClient.fetch(buddy) { p -> J2mePopup.showMessage(this, buddy, "Nickname: $buddy\nStatus: ${p?.status ?: "Unknown"}\nPresence: ${if (p?.isOnline == true) "Online" else "Offline"}") } }
    private fun blockUser() { J2mePopup.show(this, listOf(PopupItem("Block $buddy"), PopupItem("Cancel"))) { index -> if (index == 0) SafetyClient.block(buddy) { ok -> if (ok) finish() else Toast.makeText(this, "Could not block user", Toast.LENGTH_SHORT).show() } } }
    private fun reportUser() { J2mePopup.show(this, listOf(PopupItem("Spam"), PopupItem("Harassment"), PopupItem("Impersonation"), PopupItem("Other"))) { index -> val reasons = arrayOf("Spam", "Harassment", "Impersonation", "Other"); SafetyClient.report(buddy, reasons[index], "Reported from private chat") { ok -> Toast.makeText(this, if (ok) "Report submitted" else "Report failed", Toast.LENGTH_SHORT).show() } } }
    private fun markRead() { PrivateMessageStateClient.markConversationRead(AppState.username, buddy) { PrivateMessageStateClient.fetchUnread(AppState.username) { state -> if (state == null || state.count <= 0) UzzapNotificationManager.clearPrivateMessageNotification(this) else UzzapNotificationManager.notifyPrivateMessages(this, state.count, state.senders) } } }
    override fun onDestroy() { SupabaseRealtimeClient.removeDirectMessageListener(listener); super.onDestroy() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    companion object { private const val EXTRA_BUDDY = "extra_buddy"; fun intent(context: Context, buddy: String) = android.content.Intent(context, PrivateChatActivity::class.java).putExtra(EXTRA_BUDDY, buddy) }
}
