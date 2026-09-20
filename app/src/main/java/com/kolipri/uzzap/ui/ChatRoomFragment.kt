package com.kolipri.uzzap.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppDiagnostics
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.DirectMessageClient
import com.kolipri.uzzap.data.ModeratorRoleClient
import com.kolipri.uzzap.data.RoomPresenceClient
import com.kolipri.uzzap.data.SafetyClient
import com.kolipri.uzzap.data.UnifiedRoomClient
import com.kolipri.uzzap.data.SupabaseRealtimeClient
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.ui.dialogs.EmoticonPickerDialog
import com.kolipri.uzzap.ui.dialogs.J2mePopup
import com.kolipri.uzzap.ui.dialogs.PopupItem
import com.kolipri.uzzap.ui.util.EmoticonParser

class ChatRoomFragment : Fragment(R.layout.fragment_chat_room) {
    var roomName: String = "Main Lobby"
        private set

    private lateinit var messageList: RecyclerView
    private lateinit var messageAdapter: RoomMessageAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var etMessage: EditText
    private val host get() = requireActivity() as ChatHostActivity
    private val handler = Handler(Looper.getMainLooper())
    private var isPollingActive = false
    private var pollInFlight = false
    private var joinedSuccessfully = false
    private var isLoadingHistory = false
    private var hasMoreHistory = true
    private var historyLoadInFlight = false
    private var lastHistoryLoadAt = 0L
    private var realtimeConnected = false

    private data class PendingSend(val local: ChatMessage, val text: String, var attempts: Int = 0)
    private val pendingSends = LinkedHashMap<ChatMessage, PendingSend>()

    private val realtimeListener: (ChatMessage, String) -> Unit = { msg, target ->
        if (isAdded && joinedSuccessfully && target.equals(roomName, true)) {
            val current = AppState.messagesFor(roomName)
            if (msg.id > 0 && current.none { it.id == msg.id }) {
                // Realtime can arrive before the REST send callback. Reconcile the
                // optimistic local message with the database row instead of appending
                // the same message a second time.
                val pendingEntry = pendingSends.entries.firstOrNull { (local, pending) ->
                    local.id <= 0L &&
                        local.sender.equals(msg.sender, ignoreCase = true) &&
                        pending.text == msg.body
                }
                if (pendingEntry != null) {
                    val local = pendingEntry.key
                    val index = current.indexOf(local)
                    if (index >= 0) {
                        current[index] = msg
                        val adapterIndex = messageAdapter.indexOf(local)
                        if (adapterIndex >= 0) messageAdapter.replaceAt(adapterIndex, msg)
                    } else {
                        current.add(msg)
                        messageAdapter.add(msg)
                    }
                    pendingSends.remove(local)
                } else {
                    current.add(msg)
                    messageAdapter.add(msg)
                }
                AppDiagnostics.success("Room.realtimeMessage", "room=$roomName messageId="+msg.id)
                scrollToLatest()
            }
        }
    }

    private val realtimeDeleteListener: (Long, String) -> Unit = { id, target ->
        if (isAdded && target.equals(roomName, true) && id > 0) {
            AppState.messagesFor(roomName).removeAll { it.id == id }
            messageAdapter.removeById(id)
            AppDiagnostics.success("Room.realtimeDelete", "room=$roomName messageId=$id")
        }
    }

    private val connectionListener: (Boolean) -> Unit = { connected ->
        realtimeConnected = connected
        if (isAdded && joinedSuccessfully) {
            if (connected) {
                AppDiagnostics.success("Room.realtimeConnection", "connected room=$roomName")
                syncAfterReconnect()
                retryPendingSends()
            } else {
                AppDiagnostics.failed("Room.realtimeConnection", "disconnected room=$roomName; REST fallback enabled")
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isPollingActive || !isAdded || !joinedSuccessfully || pollInFlight) return
            pollInFlight = true
            val current = AppState.messagesFor(roomName)
            val afterId = current.maxOfOrNull { it.id } ?: 0L
            UnifiedRoomClient.fetchMessages(roomName, afterId) { newMsgs ->
                pollInFlight = false
                if (!isAdded || !isPollingActive || !joinedSuccessfully) return@fetchMessages
                if (newMsgs == null) {
                    AppDiagnostics.failed("Room.fetchMessages", "No message list returned for room=$roomName")
                    scheduleNextPoll()
                    return@fetchMessages
                }
                var added = 0
                for (msg in newMsgs) {
                    if (msg.id > 0 && current.none { it.id == msg.id }) {
                        current.add(msg)
                        messageAdapter.add(msg)
                        added++
                    }
                }
                if (added > 0) scrollToLatest()
                scheduleNextPoll()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(ARG_ROOM)?.let { roomName = it }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh = view.findViewById(R.id.room_swipe_refresh)
        messageList = view.findViewById(R.id.room_messages)
        messageAdapter = RoomMessageAdapter(requireContext())
        messageAdapter.onMessageLongClickListener = { message ->
            if (message.id > 0) ModeratorRoleClient.refresh { if (AppState.isModerator) showModeratorMessageActions(message) }
        }
        layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        messageList.layoutManager = layoutManager
        messageList.adapter = messageAdapter
        swipeRefresh.setOnRefreshListener { refreshHistory(force = true, fromPull = true) }
        messageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(r: RecyclerView, dx: Int, dy: Int) {
                if (layoutManager.findFirstVisibleItemPosition() <= 2 && dy <= 0) loadOlderHistory()
            }
        })
        etMessage = view.findViewById(R.id.et_message)
        view.findViewById<TextView>(R.id.tv_to_room).text = "To: $roomName"
        view.findViewById<TextView>(R.id.btn_send).setOnClickListener { sendCurrentMessage() }
        var formatting = false
        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (formatting || s == null) return
                context?.let { formatting = true; EmoticonParser.applySpans(it, s, 15f); formatting = false }
            }
        })
        view.findViewById<ImageView>(R.id.iv_emoticon_picker).setOnClickListener {
            context?.let { ctx -> EmoticonPickerDialog.show(ctx) { code ->
                val current = etMessage.text.toString()
                val prefix = if (current.isNotEmpty() && !current.endsWith(" ")) " " else ""
                etMessage.append("$prefix$code ")
                etMessage.setSelection(etMessage.text.length)
            } }
        }
        SupabaseRealtimeClient.addRoomMessageListener(realtimeListener)
        SupabaseRealtimeClient.addRoomMessageDeletedListener(realtimeDeleteListener)
        SupabaseRealtimeClient.addConnectionListener(connectionListener)
        RoomPresenceClient.join(roomName, AppState.nickname) { ok, message ->
            if (!isAdded) return@join
            if (!ok) {
                AppDiagnostics.failed("Room.join", "room=$roomName error=${message ?: "Cannot join room"}")
                UzzapDialogs.show(requireContext(), title = "Cannot Join Room", message = message ?: "This room cannot accept you.", positive = "OK", onPositive = { host.hideRoom(roomName) }, negative = null)
                return@join
            }
            joinedSuccessfully = true
            AppDiagnostics.success("Room.join", "room=$roomName joined")
            refreshHistory()
            if (realtimeConnected) retryPendingSends()
        }
    }

    private fun syncAfterReconnect() {
        if (!joinedSuccessfully) return
        val current = AppState.messagesFor(roomName)
        val afterId = current.maxOfOrNull { it.id } ?: 0L
        AppDiagnostics.started("Room.reconnectSync", "room=$roomName afterId=$afterId")
        UnifiedRoomClient.fetchMessages(roomName, afterId, forceRest = true) { messages ->
            if (!isAdded || !joinedSuccessfully || messages == null) return@fetchMessages
            var added = 0
            val state = AppState.messagesFor(roomName)
            messages.forEach { msg ->
                if (msg.id > 0 && state.none { it.id == msg.id }) {
                    state.add(msg)
                    messageAdapter.add(msg)
                    added++
                }
            }
            if (added > 0) scrollToLatest()
            AppDiagnostics.success("Room.reconnectSync", "room=$roomName added=$added")
        }
    }

    private fun showModeratorMessageActions(message: ChatMessage) {
        UzzapDialogs.items(requireContext(), title = "Moderator: Message #${message.id}", message = "${message.sender}:\n${message.body}", items = arrayOf("Delete Message", "Mute ${message.sender}", "Kick ${message.sender}", "Report ${message.sender}"), onPick = { which ->
            when (which) { 0 -> confirmDeleteMessage(message); 1 -> applyQuickModeration("mute", message.sender); 2 -> applyQuickModeration("kick", message.sender); 3 -> promptReportReason(message.sender) }
        })
    }

    private fun confirmDeleteMessage(message: ChatMessage) {
        UzzapDialogs.show(requireContext(), iconAsset = "anim-icons/delete_trash.gif", title = "Delete Message", message = "Delete this message from $roomName?", positive = "Delete", onPositive = {
            SafetyClient.deleteRoomMessage(message.id) { success ->
                if (isAdded) Toast.makeText(requireContext(), if (success) "Message deleted" else "Message deletion failed", Toast.LENGTH_SHORT).show()
                if (success) {
                    AppState.messagesFor(roomName).removeAll { it.id == message.id }
                    messageAdapter.removeById(message.id)
                }
            }
        })
    }

    private fun applyQuickModeration(action: String, target: String) {
        SafetyClient.applyModerationAction(target, action, roomName, "Moderator action from $roomName", if (action == "mute") 30 else null) { success ->
            if (isAdded) Toast.makeText(requireContext(), if (success) "${action.uppercase()} applied to $target" else "Moderation action failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshHistory(force: Boolean = false, fromPull: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!joinedSuccessfully || historyLoadInFlight) {
            if (fromPull && ::swipeRefresh.isInitialized) swipeRefresh.isRefreshing = false
            return
        }
        if (!force && lastHistoryLoadAt > 0L && now - lastHistoryLoadAt < 1_500L) return
        historyLoadInFlight = true
        isLoadingHistory = true
        if (fromPull && ::swipeRefresh.isInitialized) swipeRefresh.isRefreshing = true

        val state = AppState.messagesFor(roomName)
        val previousIds = state.filter { it.id > 0 }.map { it.id }.toSet()
        UnifiedRoomClient.fetchLatestMessages(roomName, 60) { remote ->
            historyLoadInFlight = false
            isLoadingHistory = false
            if (::swipeRefresh.isInitialized) swipeRefresh.isRefreshing = false
            if (!isAdded) return@fetchLatestMessages
            if (remote == null) {
                Toast.makeText(requireContext(), "Could not load room history.", Toast.LENGTH_SHORT).show()
                return@fetchLatestMessages
            }
            lastHistoryLoadAt = System.currentTimeMillis()

            // Never clear the local/realtime list before a REST refresh completes.
            // Merge by database ID so a foreground/pull refresh cannot roll the UI back.
            val merged = LinkedHashMap<Long, ChatMessage>()
            state.filter { it.id > 0 }.forEach { merged[it.id] = it }
            remote.filter { it.id > 0 }.forEach { merged[it.id] = it }
            val localOnly = state.filter { it.id <= 0 }
            val ordered = merged.values.sortedBy { it.id }
            val finalMessages = localOnly + ordered

            state.clear()
            state.addAll(finalMessages)
            messageAdapter.setItems(finalMessages)
            hasMoreHistory = remote.size >= 60
            if (remote.any { it.id > (previousIds.maxOrNull() ?: 0L) }) scrollToLatest()
            else if (fromPull) scrollToLatest()
            startPolling()
        }
    }

    private fun loadOlderHistory() {
        if (!joinedSuccessfully || isLoadingHistory || !hasMoreHistory) return
        val current = AppState.messagesFor(roomName)
        val beforeId = current.firstOrNull { it.id > 0 }?.id ?: return
        isLoadingHistory = true
        val oldFirst = layoutManager.findFirstVisibleItemPosition()
        val oldTop = layoutManager.findViewByPosition(oldFirst)?.top ?: 0
        UnifiedRoomClient.fetchMessagesBefore(roomName, beforeId, 50) { older ->
            if (!isAdded) return@fetchMessagesBefore
            isLoadingHistory = false
            if (older == null) return@fetchMessagesBefore
            if (older.isEmpty()) { hasMoreHistory = false; return@fetchMessagesBefore }
            val unique = older.filter { it.id > 0 && current.none { existing -> existing.id == it.id } }
            if (unique.isNotEmpty()) {
                current.addAll(0, unique)
                messageAdapter.insertAtTop(unique)
                layoutManager.scrollToPositionWithOffset(oldFirst + unique.size, oldTop)
            }
            if (older.size < 50) hasMoreHistory = false
        }
    }

    private fun scrollToLatest() {
        if (!::messageList.isInitialized || !::messageAdapter.isInitialized) return
        messageList.post {
            val last = messageAdapter.itemCount - 1
            if (last >= 0) layoutManager.scrollToPositionWithOffset(last, Int.MIN_VALUE)
        }
    }

    override fun onResume() { super.onResume(); setupScreen(); if (joinedSuccessfully) refreshHistory() }
    override fun onPause() { stopPolling(); super.onPause() }
    override fun onHiddenChanged(hidden: Boolean) { super.onHiddenChanged(hidden); if (!hidden) { setupScreen(); if (joinedSuccessfully) refreshHistory() } else stopPolling() }

    override fun onDestroyView() {
        stopPolling()
        SupabaseRealtimeClient.removeRoomMessageListener(realtimeListener)
        SupabaseRealtimeClient.removeRoomMessageDeletedListener(realtimeDeleteListener)
        SupabaseRealtimeClient.removeConnectionListener(connectionListener)
        super.onDestroyView()
    }

    fun setupScreen() {
        host.setScreenTitle("Chat - $roomName")
        host.showConnectedBall(true)
        host.setSoftkeys(getString(R.string.softkey_options), getString(R.string.softkey_buddies), onLeft = { showOptionsMenu() }, onRight = { host.goToBuddyList() })
    }

    private fun startPolling() { if (isPollingActive || !joinedSuccessfully) return; isPollingActive = true; pollInFlight = false; handler.removeCallbacks(pollRunnable); handler.post(pollRunnable) }
    private fun scheduleNextPoll() { if (isPollingActive && isAdded && joinedSuccessfully) { handler.removeCallbacks(pollRunnable); handler.postDelayed(pollRunnable, 2_500L) } }
    private fun stopPolling() { isPollingActive = false; pollInFlight = false; handler.removeCallbacks(pollRunnable) }

    private fun hasNetwork(): Boolean {
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val n = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(n) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun sendCurrentMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty() || !joinedSuccessfully) return
        etMessage.setText("")
        val local = ChatMessage(AppState.nickname, text)
        AppState.messagesFor(roomName).add(local)
        messageAdapter.add(local)
        scrollToLatest()
        val pending = PendingSend(local, text)
        pendingSends[local] = pending
        sendPending(pending)
    }

    private fun sendPending(pending: PendingSend) {
        if (!isAdded || !joinedSuccessfully) return
        pending.attempts++
        UnifiedRoomClient.sendMessage(roomName, pending.text) { inserted ->
            if (!isAdded) return@sendMessage
            if (inserted != null) {
                pendingSends.remove(pending.local)
                val messages = AppState.messagesFor(roomName)
                val idx = messages.indexOf(pending.local)
                if (idx >= 0) messages[idx] = inserted
                val ai = messageAdapter.indexOf(pending.local)
                if (ai >= 0) messageAdapter.replaceAt(ai, inserted)
                return@sendMessage
            }
            if (pending.attempts < 4) {
                val delay = (1L shl pending.attempts).coerceAtMost(8L) * 1000L
                handler.postDelayed({ sendPending(pending) }, delay)
            } else {
                pendingSends.remove(pending.local)
                etMessage.setText(pending.text)
                etMessage.setSelection(etMessage.text.length)
                Toast.makeText(requireContext(), if (hasNetwork()) "Message failed. Press Send to retry." else "Offline. Message restored to the composer for retry.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun retryPendingSends() { pendingSends.values.toList().forEach { if (it.attempts < 4) sendPending(it) } }

    private fun showOptionsMenu() {
        J2mePopup.show(requireContext(), listOf(PopupItem("Send Message"), PopupItem("Send Whisper"), PopupItem("Room Members"), PopupItem("Room Info"), PopupItem("Invite Buddy"), PopupItem("Report"), PopupItem("Hide Room"), PopupItem("Leave Room"))) { index ->
            when (index) {
                0 -> etMessage.requestFocus()
                1 -> whisperDialog()
                2 -> startActivity(UzzapParityActivity.intent(requireContext(), UzzapParityActivity.MODE_ROOM_MEMBERS, roomName))
                3 -> UzzapDialogs.show(requireContext(), iconAsset = "anim-icons/menu_backend.gif", title = roomName, message = "Province chatroom\nLive room\nMessages: ${AppState.messagesFor(roomName).size}", positive = "OK")
                4 -> startActivity(UzzapParityActivity.intent(requireContext(), UzzapParityActivity.MODE_ROOM_ACTIONS, roomName))
                5 -> showReportDialog()
                6 -> host.confirmHideRoom()
                7 -> host.confirmLeaveRoom()
            }
        }
    }

    private fun showReportDialog() {
        UnifiedRoomClient.fetchParticipants(roomName) { members ->
            if (!isAdded) return@fetchParticipants
            val targets = (members ?: emptyList()).filter { !it.equals(AppState.nickname, true) }
            if (targets.isEmpty()) {
                UzzapDialogs.form(requireContext(), iconAsset = "anim-icons/privacy_lock.gif", title = "Report User in $roomName", fields = listOf(UzzapDialogs.FieldSpec("Enter username to report")), positive = "Next", onPositive = { f ->
                    val target = f[0].text.toString().trim()
                    if (target.isEmpty()) { f[0].error = "Enter a username"; return@form false }
                    promptReportReason(target); true
                })
                return@fetchParticipants
            }
            UzzapDialogs.items(requireContext(), iconAsset = "anim-icons/privacy_lock.gif", title = "Report User in $roomName", items = targets.toTypedArray(), onPick = { which -> promptReportReason(targets[which]) })
        }
    }

    private fun promptReportReason(target: String) {
        UzzapDialogs.show(requireContext(), iconAsset = "anim-icons/privacy_lock.gif", title = "Report User",
            message = "Report \"$target\" for inappropriate behavior? Moderators will review this report.",
            inputHint = "Add details (optional)...", positive = "Report", onPositive = { details ->
                SafetyClient.report(target, "Other", details.ifBlank { "Reported from chatroom: $roomName" }) { ok ->
                    if (isAdded) Toast.makeText(requireContext(), if (ok) "Report submitted for moderation." else "Report submission failed.", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun whisperDialog() {
        UnifiedRoomClient.fetchParticipants(roomName) { members ->
            val targets = (members ?: emptyList()).filter { !it.equals(AppState.username, true) && !it.equals(AppState.nickname, true) }
            if (!isAdded) return@fetchParticipants
            if (targets.isEmpty()) { Toast.makeText(requireContext(), "No other room members available.", Toast.LENGTH_SHORT).show(); return@fetchParticipants }
            UzzapDialogs.items(requireContext(), title = "Whisper in $roomName", items = targets.toTypedArray(), onPick = { which ->
                val target = targets[which]
                UzzapDialogs.form(requireContext(), title = "Whisper to $target", fields = listOf(UzzapDialogs.FieldSpec("Whisper to $target")), positive = "Send", onPositive = { f ->
                    val text = f[0].text.toString().trim()
                    if (text.isEmpty()) { f[0].error = "Enter a message"; return@form false }
                    DirectMessageClient.sendMessage(AppState.username, target, text) { sent ->
                        if (isAdded) Toast.makeText(requireContext(), if (sent != null) "Whisper sent privately to $target" else "Whisper failed.", Toast.LENGTH_SHORT).show()
                    }
                    true
                })
            })
        }
    }

    companion object {
        private const val ARG_ROOM = "room"
        fun newInstance(roomName: String) = ChatRoomFragment().apply { arguments = Bundle().apply { putString(ARG_ROOM, roomName) } }
    }
}
