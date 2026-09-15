package com.kolipri.uzzap.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.kolipri.uzzap.R
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

/** Modern Android private chat while preserving Uzzap's existing data flow. */
class PrivateChatActivity : BaseChromeActivity() {
    private lateinit var chatRoot: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var messagesContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var sendButton: TextView
    private lateinit var statusText: TextView
    private lateinit var emptyState: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private var buddy = ""
    private var lastId = 0L
    private var keyboardVisible = false
    // The initial conversation is loaded once from onCreate. onResume must not
    // immediately start the same afterId=0 request a second time.
    private var initialConversationLoadStarted = false

    private val realtimeListener: (DirectMessage) -> Unit = { dm ->
        val me = AppState.username
        val isForThisChat = (dm.sender.equals(buddy, true) && dm.recipient.equals(me, true)) ||
                            (dm.sender.equals(me, true) && dm.recipient.equals(buddy, true))
        if (isForThisChat && dm.id > lastId) {
            lastId = dm.id
            append(dm)
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            markReadAndRefreshNotification()
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!polling || isFinishing) return
            DirectMessageClient.fetchConversation(AppState.username, buddy, lastId) { messages ->
                if (messages != null) {
                    messages.forEach { message ->
                        if (message.id > lastId) {
                            lastId = message.id
                            append(message)
                        }
                    }
                    markReadAndRefreshNotification()
                }
                if (polling) handler.postDelayed(this, 2500L)
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
        // The modern chat has its own header/actions; hide the legacy softkeys.
        setSoftkeys(null, null)
        SupabaseRealtimeClient.addDirectMessageListener(realtimeListener)

        ViewCompat.setOnApplyWindowInsetsListener(chatRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val bottomPadding = if (keyboardVisible) ime.bottom else bars.bottom
            view.updatePadding(bottom = bottomPadding)
            val composer = input.parent as? View
            composer?.let {
                val lp = it.layoutParams as? LinearLayout.LayoutParams
                if (lp != null) {
                    lp.bottomMargin = if (keyboardVisible) 0 else 8
                    it.layoutParams = lp
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(chatRoot)

        initialConversationLoadStarted = true
        loadConversation()
        loadPresence()
    }

    private fun buildUi() {
        chatRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 248, 250))
        }
        contentFrame.addView(chatRoot, FrameLayout.LayoutParams(-1, -1))

        header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(232, 234, 238))
            }
        }

        val back = actionButton("‹", 30).apply {
            setOnClickListener { finish() }
            contentDescription = "Back"
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(48)))

        val avatar = TextView(this).apply {
            text = buddy.take(1).uppercase(Locale.getDefault())
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(55, 112, 82))
            }
            contentDescription = "$buddy avatar"
        }
        header.addView(avatar, LinearLayout.LayoutParams(dp(42), dp(42)))

        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
        }
        identity.addView(TextView(this).apply {
            text = buddy
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(25, 27, 31))
            maxLines = 1
        })
        statusText = TextView(this).apply {
            text = "Checking status…"
            textSize = 12f
            setTextColor(Color.rgb(100, 105, 112))
        }
        identity.addView(statusText)
        header.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))

        val profile = actionButton("ⓘ", 21).apply {
            contentDescription = "View profile"
            setOnClickListener { viewProfile() }
        }
        val more = actionButton("⋮", 25).apply {
            contentDescription = "$buddy options"
            setOnClickListener { showOptions() }
        }
        header.addView(profile, LinearLayout.LayoutParams(dp(44), dp(48)))
        header.addView(more, LinearLayout.LayoutParams(dp(44), dp(48)))
        chatRoot.addView(header, LinearLayout.LayoutParams(-1, -2))

        val body = FrameLayout(this).apply { setBackgroundColor(Color.rgb(247, 248, 250)) }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(8), dp(10), dp(8), dp(10))
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        messagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        scroll.addView(messagesContainer, ViewGroup.LayoutParams(-1, -2))
        body.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        emptyState = TextView(this).apply {
            text = "Start a conversation with $buddy 👋"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(105, 110, 118))
            setPadding(dp(30), dp(30), dp(30), dp(30))
            visibility = View.GONE
        }
        body.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        chatRoot.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundColor(Color.WHITE)
            elevation = dp(3).toFloat()
        }
        val emoji = actionButton("☺", 22).apply {
            contentDescription = "Add emoticon"
            setOnClickListener {
                EmoticonPickerDialog.show(this@PrivateChatActivity) { code ->
                    val prefix = if (input.text.isNotEmpty() && !input.text.endsWith(" ")) " " else ""
                    input.append("$prefix$code ")
                    input.setSelection(input.text.length)
                }
            }
        }
        composer.addView(emoji, LinearLayout.LayoutParams(dp(44), dp(48)))

        input = EditText(this).apply {
            hint = "Message $buddy…"
            textSize = 15f
            setTextColor(Color.rgb(30, 32, 36))
            setHintTextColor(Color.rgb(145, 149, 156))
            setPadding(dp(15), dp(5), dp(15), dp(5))
            minHeight = dp(44)
            maxLines = 5
            isSingleLine = false
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.rgb(245, 246, 248))
                setStroke(dp(1), Color.rgb(228, 230, 234))
            }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(7) })

        sendButton = TextView(this).apply {
            text = "➤"
            textSize = 21f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(45, 112, 75))
            }
            contentDescription = "Send message"
            isEnabled = false
            alpha = 0.45f
        }
        composer.addView(sendButton, LinearLayout.LayoutParams(dp(46), dp(46)))
        chatRoot.addView(composer, LinearLayout.LayoutParams(-1, -2))

        input.addTextChangedListener(SimpleTextWatcher { updateSendState() })
        sendButton.setOnClickListener { sendCurrentMessage() }
    }

    private fun sendCurrentMessage() {
        val body = input.text.toString().trim()
        if (body.isEmpty()) return
        SafetyClient.isBlocked(buddy) { blocked ->
            if (blocked) {
                Toast.makeText(this, "$buddy is blocked", Toast.LENGTH_SHORT).show()
                return@isBlocked
            }
            input.isEnabled = false
            sendButton.isEnabled = false
            DirectMessageClient.sendMessage(AppState.username, buddy, body) { sent ->
                input.isEnabled = true
                if (sent != null) {
                    input.setText("")
                    if (sent.id > lastId) {
                        lastId = sent.id
                        append(sent)
                    }
                } else {
                    updateSendState()
                    Toast.makeText(this, "Message could not be sent. The recipient may have blocked this conversation.", Toast.LENGTH_SHORT).show()
                }
                updateSendState()
            }
        }
    }

    private fun loadConversation() {
        DirectMessageClient.fetchConversation(AppState.username, buddy, 0) { messages ->
            if (messages == null) {
                emptyState.text = "Unable to load conversation.\nCheck your connection and try again."
                emptyState.visibility = View.VISIBLE
                return@fetchConversation
            }
            messagesContainer.removeAllViews()
            lastId = 0L
            if (messages.isEmpty()) emptyState.visibility = View.VISIBLE else emptyState.visibility = View.GONE
            messages.forEach {
                if (it.id > lastId) lastId = it.id
                append(it)
            }
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            markReadAndRefreshNotification()
        }
    }

    private fun append(message: DirectMessage) {
        emptyState.visibility = View.GONE
        val mine = message.sender.equals(AppState.username, true)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (mine) Gravity.END else Gravity.START
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }

        val bubble = TextView(this).apply {
            text = message.body
            textSize = 15f
            setTextColor(if (mine) Color.WHITE else Color.rgb(35, 37, 41))
            setPadding(dp(13), dp(9), dp(13), dp(6))
            maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (mine) Color.rgb(47, 116, 78) else Color.WHITE)
                if (!mine) setStroke(dp(1), Color.rgb(229, 231, 235))
            }
        }
        row.addView(bubble, LinearLayout.LayoutParams(-2, -2))

        val meta = TextView(this).apply {
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            text = if (mine) "$time  ✓" else time
            textSize = 10f
            setTextColor(Color.rgb(125, 130, 138))
            setPadding(dp(5), dp(2), dp(5), 0)
        }
        row.addView(meta)
        messagesContainer.addView(row, LinearLayout.LayoutParams(-1, -2))
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun loadPresence() {
        PresenceClient.fetch(buddy) { p ->
            if (p?.isOnline == true) {
                statusText.text = "● Online"
                statusText.setTextColor(Color.rgb(38, 133, 75))
            } else {
                statusText.text = if (p?.lastSeen != null) "Offline · Last seen recently" else "Offline"
                statusText.setTextColor(Color.rgb(105, 110, 118))
            }
        }
    }

    private fun showOptions() {
        AlertDialog.Builder(this)
            .setTitle("$buddy Options")
            .setItems(arrayOf("Send Message", "Add Emoticon", "View Profile", "Block User", "Report User", "Cancel")) { _, which ->
                when (which) {
                    0 -> { input.requestFocus(); (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
                    1 -> EmoticonPickerDialog.show(this) { code -> input.append(if (input.text.isNotEmpty()) " $code " else "$code ") }
                    2 -> viewProfile()
                    3 -> blockUser()
                    4 -> reportUser()
                }
            }.show()
    }

    private fun viewProfile() {
        PresenceClient.fetch(buddy) { p ->
            AlertDialog.Builder(this)
                .setTitle(buddy)
                .setMessage("Nickname: $buddy\nStatus: ${p?.status ?: "Unknown"}\nPresence: ${if (p?.isOnline == true) "Online" else "Offline"}")
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun blockUser() {
        AlertDialog.Builder(this).setTitle("Block $buddy?")
            .setMessage("Blocked users cannot send you private messages, and you cannot send them private messages.")
            .setPositiveButton("Block") { _, _ ->
                SafetyClient.block(buddy) { ok ->
                    Toast.makeText(this, if (ok) "$buddy blocked" else "Could not block user", Toast.LENGTH_SHORT).show()
                    if (ok) finish()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun reportUser() {
        val reasons = arrayOf("Spam", "Harassment", "Impersonation", "Other")
        AlertDialog.Builder(this).setTitle("Report $buddy").setItems(reasons) { _, which ->
            SafetyClient.report(buddy, reasons[which], "Reported from private chat") { ok ->
                Toast.makeText(this, if (ok) "Report submitted" else "Report failed", Toast.LENGTH_SHORT).show()
            }
        }.show()
    }

    private fun markReadAndRefreshNotification() {
        PrivateMessageStateClient.markConversationRead(AppState.username, buddy) {
            PrivateMessageStateClient.fetchUnread(AppState.username) { state ->
                if (state == null || state.count <= 0) UzzapNotificationManager.clearPrivateMessageNotification(this)
                else UzzapNotificationManager.notifyPrivateMessages(this, state.count, state.senders)
            }
        }
    }

    private fun updateSendState() {
        if (!::sendButton.isInitialized) return
        val enabled = input.text.toString().trim().isNotEmpty() && input.isEnabled
        sendButton.isEnabled = enabled
        sendButton.alpha = if (enabled) 1f else 0.45f
    }

    private fun actionButton(label: String, size: Int): TextView = TextView(this).apply {
        text = label
        textSize = size.toFloat()
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(45, 50, 56))
        isClickable = true
        isFocusable = true
        background = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onResume() {
        super.onResume()
        AppState.activeChatBuddy = buddy
        polling = true
        handler.removeCallbacks(poll)
        // Do not immediately re-fetch the conversation here: onCreate already
        // starts the initial afterId=0 load. Begin incremental polling after
        // the normal polling interval so the initial request cannot duplicate.
        handler.postDelayed(poll, 2500L)
        loadPresence()
    }

    override fun onPause() {
        if (AppState.activeChatBuddy.equals(buddy, ignoreCase = true)) {
            AppState.activeChatBuddy = null
        }
        polling = false
        handler.removeCallbacks(poll)
        super.onPause()
    }

    override fun onDestroy() {
        polling = false
        handler.removeCallbacksAndMessages(null)
        SupabaseRealtimeClient.removeDirectMessageListener(realtimeListener)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_BUDDY = "buddy"
        fun intent(context: Context, buddy: String) = android.content.Intent(context, PrivateChatActivity::class.java).apply {
            putExtra(EXTRA_BUDDY, buddy)
            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    private class SimpleTextWatcher(private val action: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = action()
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }
}