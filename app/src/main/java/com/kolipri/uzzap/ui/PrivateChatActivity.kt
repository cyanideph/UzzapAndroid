package com.kolipri.uzzap.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.UserProfileClient
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.DirectMessageClient
import com.kolipri.uzzap.data.PrivateMessageStateClient
import com.kolipri.uzzap.data.PresenceClient
import com.kolipri.uzzap.data.SafetyClient
import com.kolipri.uzzap.data.SupabaseRealtimeClient
import com.kolipri.uzzap.data.UzzapNotificationManager
import com.kolipri.uzzap.model.DirectMessage
import com.kolipri.uzzap.ui.dialogs.EmoticonPickerDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrivateChatActivity : BaseChromeActivity() {
    private lateinit var chatRoot: LinearLayout
    private lateinit var messageList: RecyclerView
    private lateinit var messageAdapter: DirectMessageAdapter
    private val layoutManager: LinearLayoutManager?
        get() = if (::messageList.isInitialized) messageList.layoutManager as? LinearLayoutManager else null
    private lateinit var input: EditText
    private lateinit var sendButton: TextView
    private lateinit var statusText: TextView
    private lateinit var emptyState: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private var realtimeConnected = false
    private var buddy = ""
    private var lastId = 0L
    private var oldestId = 0L
    private var hasMoreHistory = true
    private var historyLoading = false
    private val renderedIds = LinkedHashSet<Long>()
    private var failedBody: String? = null
    private var notificationRefreshScheduled = false
    private val notificationRefresh = Runnable {
        notificationRefreshScheduled = false
        PrivateMessageStateClient.fetchUnread(AppState.username) { state ->
            if (state == null || state.count <= 0) UzzapNotificationManager.clearPrivateMessageNotification(this)
            else UzzapNotificationManager.notifyPrivateMessages(this, state.count, state.senders, state.messageIds)
        }
    }

    private val realtimeListener: (DirectMessage) -> Unit = realtimeListener@{ dm ->
        val me = AppState.username
        val isForThisChat = (dm.sender.equals(buddy, true) && dm.recipient.equals(me, true)) || (dm.sender.equals(me, true) && dm.recipient.equals(buddy, true))
        if (isFinishing || isDestroyed || !::messageList.isInitialized || !::messageAdapter.isInitialized) return@realtimeListener
        if (isForThisChat && dm.id > lastId && renderedIds.add(dm.id)) {
            lastId = dm.id
            appendMessage(dm, true)
            markReadAndRefreshNotification()
        }
    }

    private val connectionListener: (Boolean) -> Unit = { connected ->
        realtimeConnected = connected
        if (connected) {
            polling = false
            handler.removeCallbacks(poll)
        } else if (!isFinishing) {
            polling = true
            handler.removeCallbacks(poll)
            handler.post(poll)
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!polling || realtimeConnected || isFinishing || isDestroyed) return
            DirectMessageClient.fetchConversation(AppState.username, buddy, lastId) { messages ->
                if (isFinishing || isDestroyed || !::messageList.isInitialized || !::messageAdapter.isInitialized) return@fetchConversation
                // Mark-read issues a server write and busts the unread cache:
                // only do it when the poll actually delivered something new.
                if (messages != null) {
                    var added = false
                    messages.forEach { if (it.id > lastId && renderedIds.add(it.id)) { lastId = it.id; appendMessage(it, true); added = true } }
                    if (added) markReadAndRefreshNotification()
                }
                if (polling && !realtimeConnected) handler.postDelayed(this, 2500L)
            }
        }
    }

    override fun contentLayoutRes(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buddy = intent.getStringExtra(EXTRA_BUDDY).orEmpty()
        if (buddy.isBlank()) { finish(); return }
        buildUi()
        setScreenTitle("")
        showConnectedBall(false)
        setSoftkeys(null, null)
        SupabaseRealtimeClient.addDirectMessageListener(realtimeListener)
        SupabaseRealtimeClient.addConnectionListener(connectionListener)
        messageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (layoutManager?.findFirstVisibleItemPosition()?.let { it <= 2 } == true && dy <= 0) loadOlderHistory()
            }
        })
        loadConversation()
        loadPresence()
    }

    private fun buildUi() {
        chatRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ThemeManager.resolve(this@PrivateChatActivity, R.attr.uzzapColorBg)) }
        contentFrame.addView(chatRoot, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)); background = GradientDrawable().apply { setColor(ThemeManager.card(this@PrivateChatActivity)); setStroke(dp(1), ThemeManager.resolve(this@PrivateChatActivity, R.attr.uzzapColorDivider)) } }
        header.addView(actionButton("‹", 30).apply { setOnClickListener { finish() }; contentDescription = "Back" }, LinearLayout.LayoutParams(dp(44), dp(48)))
        val identity = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, dp(6), 0) }
        identity.addView(TextView(this).apply { text = buddy; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.resolve(this@PrivateChatActivity, R.attr.uzzapColorText)); maxLines = 1 })
        statusText = TextView(this).apply { text = "Checking status…"; textSize = 12f; setTextColor(ThemeManager.resolve(this@PrivateChatActivity, R.attr.uzzapColorMuted)) }
        identity.addView(statusText)
        header.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionButton("ⓘ", 21).apply { contentDescription = "View profile"; setOnClickListener { viewProfile() } }, LinearLayout.LayoutParams(dp(44), dp(48)))
        header.addView(actionButton("⋮", 25).apply { contentDescription = "$buddy options"; setOnClickListener { showOptions() } }, LinearLayout.LayoutParams(dp(44), dp(48)))
        chatRoot.addView(header, LinearLayout.LayoutParams(-1, -2))
        val body = FrameLayout(this).apply { setBackgroundColor(ThemeManager.resolve(this@PrivateChatActivity, R.attr.uzzapColorBg)) }
        messageList = RecyclerView(this).apply {
            setPadding(dp(8), dp(10), dp(8), dp(10))
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutManager = LinearLayoutManager(this@PrivateChatActivity)
            itemAnimator = null
        }
        messageAdapter = DirectMessageAdapter(this)
        messageList.adapter = messageAdapter
        body.addView(messageList, FrameLayout.LayoutParams(-1, -1))
        emptyState = TextView(this).apply { text = "Start a conversation with $buddy 👋"; textSize = 15f; gravity = Gravity.CENTER; setTextColor(ThemeManager.resolve(this@PrivateChatActivity, R.attr.uzzapColorMuted)); setPadding(dp(30), dp(30), dp(30), dp(30)); visibility = View.GONE }
        body.addView(emptyState, FrameLayout.LayoutParams(-1, -1)); chatRoot.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; setPadding(dp(8), dp(7), dp(8), dp(7)); setBackgroundColor(ThemeManager.card(this@PrivateChatActivity)); elevation = dp(3).toFloat() }
        composer.addView(actionButton("☺", 22).apply { contentDescription = "Add emoticon"; setOnClickListener { EmoticonPickerDialog.show(this@PrivateChatActivity) { code -> val prefix = if (input.text.isNotEmpty() && !input.text.endsWith(" ")) " " else ""; input.append("$prefix$code "); input.setSelection(input.text.length) } } }, LinearLayout.LayoutParams(dp(44), dp(48)))
        input = EditText(this).apply { hint = "Message $buddy…"; textSize = 15f; setTextColor(ThemeManager.resolve(this@PrivateChatActivity, R.attr.uzzapColorText)); setHintTextColor(ThemeManager.resolve(this@PrivateChatActivity, R.attr.uzzapColorMuted)); setPadding(dp(15), dp(5), dp(15), dp(5)); minHeight = dp(44); maxLines = 5; isSingleLine = false; background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(ThemeManager.resolve(this@PrivateChatActivity, R.attr.uzzapColorBg)); setStroke(dp(1), ThemeManager.resolve(this@PrivateChatActivity, R.attr.uzzapColorDivider)) } }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(7) })
        sendButton = TextView(this).apply { text = "➤"; textSize = 21f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.card(this@PrivateChatActivity)); background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ThemeManager.success(this@PrivateChatActivity)) }; contentDescription = "Send message"; isEnabled = false; alpha = 0.45f }
        composer.addView(sendButton, LinearLayout.LayoutParams(dp(46), dp(46))); chatRoot.addView(composer, LinearLayout.LayoutParams(-1, -2))
        input.addTextChangedListener(SimpleTextWatcher { updateSendState() }); sendButton.setOnClickListener { sendCurrentMessage() }
    }

    private fun loadConversation() {
        DirectMessageClient.fetchConversation(AppState.username, buddy, 0) { messages ->
            if (isFinishing || isDestroyed || !::messageList.isInitialized || !::messageAdapter.isInitialized) return@fetchConversation
            if (messages == null) { emptyState.text = "Unable to load conversation.\nCheck your connection and try again."; emptyState.visibility = View.VISIBLE; return@fetchConversation }
            renderedIds.clear(); lastId = 0L
            messages.forEach { if (it.id > 0) renderedIds.add(it.id); lastId = maxOf(lastId, it.id) }
            oldestId = messages.firstOrNull { it.id > 0 }?.id ?: 0L; hasMoreHistory = messages.size >= 100
            emptyState.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
            messageAdapter.setItems(messages)
            messageList.post { scrollToBottom() }; markReadAndRefreshNotification()
        }
    }

    private fun loadOlderHistory() {
        if (historyLoading || !hasMoreHistory || oldestId <= 0L || isFinishing || isDestroyed) return
        val lm = layoutManager ?: return
        historyLoading = true
        val oldFirst = lm.findFirstVisibleItemPosition()
        val oldTop = lm.findViewByPosition(oldFirst)?.top ?: 0
        DirectMessageClient.fetchConversationBefore(AppState.username, buddy, oldestId, 50) { older ->
            historyLoading = false
            if (isFinishing || isDestroyed || !::messageList.isInitialized || !::messageAdapter.isInitialized || older == null) return@fetchConversationBefore
            val unique = older.filter { it.id > 0 && renderedIds.add(it.id) }
            if (unique.isEmpty()) { hasMoreHistory = false; return@fetchConversationBefore }
            val current = messageAdapter.itemsSnapshot()
            messageAdapter.setItems(unique + current)
            oldestId = unique.first().id
            if (unique.size < 50) hasMoreHistory = false
            messageList.post { if (!isFinishing && !isDestroyed) layoutManager?.scrollToPositionWithOffset(oldFirst + unique.size, oldTop) }
        }
    }

    private fun appendMessage(message: DirectMessage, scrollToBottom: Boolean, atTop: Boolean = false): View {
        emptyState.visibility = View.GONE
        val current = messageAdapter.itemsSnapshot().toMutableList()
        if (message.id > 0 && current.any { it.id == message.id }) return messageList
        if (atTop) current.add(0, message) else current.add(message)
        messageAdapter.setItems(current)
        if (scrollToBottom) messageList.post { scrollToBottom() }
        return messageList
    }

    private fun scrollToBottom() {
        val last = messageAdapter.itemCount - 1
        if (last >= 0 && !isFinishing && !isDestroyed) layoutManager?.scrollToPosition(last)
    }

    private fun sendCurrentMessage() {
        val body = (failedBody ?: input.text.toString()).trim(); if (body.isEmpty()) return
        failedBody = null; input.setText(""); input.isEnabled = false; sendButton.isEnabled = false
        DirectMessageClient.sendMessageWithRetry(AppState.username, buddy, body, 4, { sent ->
            input.isEnabled = true
            if (sent != null && sent.id > 0 && renderedIds.add(sent.id)) { lastId = maxOf(lastId, sent.id); oldestId = if (oldestId == 0L) sent.id else minOf(oldestId, sent.id); appendMessage(sent, true) }
            updateSendState()
        }, { error ->
            input.isEnabled = true; failedBody = body; input.setText(body); input.setSelection(input.text.length); updateSendState(); Toast.makeText(this, "$error\nPress Send to retry.", Toast.LENGTH_LONG).show()
        })
    }

    private fun loadPresence() { PresenceClient.fetch(buddy) { p ->
            if (isFinishing || isDestroyed || !::statusText.isInitialized) return@fetch
  if (p?.isOnline == true) { statusText.text = "● Online"; statusText.setTextColor(ThemeManager.success(this)) } else { statusText.text = if (p?.lastSeen != null) "Offline · Last seen recently" else "Offline" } } }
    private fun showOptions() { AlertDialog.Builder(this).setTitle("$buddy Options").setItems(arrayOf("Send Message", "Add Emoticon", "View Profile", "Block User", "Report User", "Cancel")) { _, which -> when (which) { 0 -> { input.requestFocus(); (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }; 1 -> EmoticonPickerDialog.show(this) { code -> input.append(if (input.text.isNotEmpty()) " $code " else "$code ") }; 2 -> viewProfile(); 3 -> blockUser(); 4 -> reportUser() } }.show() }
    private fun viewProfile() {
        UserProfileClient.fetch(buddy) { profile ->
            if (isFinishing || isDestroyed) return@fetch
            val p = profile
            val nickname = p?.nickname?.ifBlank { buddy } ?: buddy
            val status = p?.status?.ifBlank { "Unknown" } ?: "Unknown"
            val presence = if (p?.isOnline == true) "Online" else "Offline"
            val about = p?.statusMessage?.ifBlank { "—" } ?: "—"
            val lastSeen = if (p?.isOnline == false && !p.lastSeen.isNullOrBlank()) "\nLast seen: ${p.lastSeen}" else ""
            UzzapDialogs.show(
                this,
                iconAsset = "anim-icons/im_profile.gif",
                title = nickname,
                message = "Username: ${p?.username ?: buddy}\nStatus: $status\nPresence: $presence\nAbout Me: $about$lastSeen",
                positive = "Close"
            )
        }
    }
    private fun blockUser() { UzzapDialogs.show(this, iconAsset = "anim-icons/privacy_lock.gif", title = "Block $buddy?", message = "Blocked users cannot send you private messages, and you cannot send them private messages.", positive = "Block", onPositive = { SafetyClient.block(buddy) { ok -> Toast.makeText(this, if (ok) "$buddy blocked" else "Could not block user", Toast.LENGTH_SHORT).show(); if (ok) finish() } }) }
    private fun reportUser() { UzzapDialogs.show(this, iconAsset = "anim-icons/privacy_lock.gif", title = "Report User", message = "Report \"$buddy\" for inappropriate behavior? Moderators will review this report.", inputHint = "Add details (optional)...", positive = "Report", onPositive = { details -> SafetyClient.report(buddy, "Other", details.ifBlank { "Reported from private chat" }) { ok -> Toast.makeText(this, if (ok) "Report submitted" else "Report failed", Toast.LENGTH_SHORT).show() } }) }
    private fun markReadAndRefreshNotification() {
        PrivateMessageStateClient.markConversationRead(AppState.username, buddy) {
            if (!notificationRefreshScheduled) {
                notificationRefreshScheduled = true
                handler.removeCallbacks(notificationRefresh)
                handler.postDelayed(notificationRefresh, 750L)
            }
        }
    }
    private fun updateSendState() { if (!::sendButton.isInitialized) return; val enabled = input.text.toString().trim().isNotEmpty() && input.isEnabled; sendButton.isEnabled = enabled; sendButton.alpha = if (enabled) 1f else 0.45f }
    private fun actionButton(label: String, size: Int): TextView = TextView(this).apply { text = label; textSize = size.toFloat(); gravity = Gravity.CENTER; setTextColor(ThemeManager.resolve(this@PrivateChatActivity, R.attr.uzzapColorText)); isClickable = true; isFocusable = true; background = null }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    override fun onResume() { super.onResume(); AppState.activeChatBuddy = buddy; if (!realtimeConnected) { polling = true; handler.removeCallbacks(poll); handler.postDelayed(poll, 2500L) }; loadPresence() }
    override fun onPause() { if (AppState.activeChatBuddy.equals(buddy, ignoreCase = true)) AppState.activeChatBuddy = null; polling = false; handler.removeCallbacks(poll); super.onPause() }
    override fun onDestroy() { polling = false; historyLoading = false; notificationRefreshScheduled = false; handler.removeCallbacksAndMessages(null); SupabaseRealtimeClient.removeDirectMessageListener(realtimeListener); SupabaseRealtimeClient.removeConnectionListener(connectionListener); super.onDestroy() }
    companion object { private const val EXTRA_BUDDY = "buddy"; fun intent(context: Context, buddy: String) = android.content.Intent(context, PrivateChatActivity::class.java).apply { putExtra(EXTRA_BUDDY, buddy); addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP) } }
    private class SimpleTextWatcher(private val action: () -> Unit) : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = action(); override fun afterTextChanged(s: android.text.Editable?) = Unit }
}
