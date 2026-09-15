package com.kolipri.uzzap.ui

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
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppDiagnostics
import com.kolipri.uzzap.data.AppState
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

    private val realtimeListener: (ChatMessage, String) -> Unit = { msg, target ->
        if (isAdded && joinedSuccessfully && target.equals(roomName, true)) {
            val current = AppState.messagesFor(roomName)
            if (msg.id > 0 && current.none { it.id == msg.id }) {
                current.add(msg)
                messageAdapter.add(msg)
                AppDiagnostics.success("Room.realtimeMessage", "room=$roomName messageId=${msg.id}")
                scrollToLatest()
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isPollingActive || !isAdded || !joinedSuccessfully || pollInFlight) return
            pollInFlight = true
            val current = AppState.messagesFor(roomName)
            val afterId = current.maxOfOrNull { it.id } ?: 0L
            AppDiagnostics.started("Room.fetchMessages", "room=$roomName afterId=$afterId")
            UnifiedRoomClient.fetchMessages(roomName, afterId) { newMsgs ->
                pollInFlight = false
                if (!isAdded || !isPollingActive || !joinedSuccessfully) return@fetchMessages
                if (newMsgs == null) {
                    AppDiagnostics.failed("Room.fetchMessages", "No message list returned for room=$roomName")
                    scheduleNextPoll()
                    return@fetchMessages
                }
                var added = 0
                var highestId = afterId
                for (msg in newMsgs) {
                    if (msg.id > highestId) highestId = msg.id
                    if (msg.id > 0 && current.none { it.id == msg.id }) {
                        current.add(msg)
                        messageAdapter.add(msg)
                        added++
                    }
                }
                AppDiagnostics.success("Room.fetchMessages", "room=$roomName loaded=${newMsgs.size} added=$added afterId=$afterId highestId=$highestId")
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
        messageList = view.findViewById(R.id.room_messages)
        messageAdapter = RoomMessageAdapter(requireContext())
        messageAdapter.onMessageLongClickListener = { message ->
            if (AppState.isModerator && message.id > 0) showModeratorMessageActions(message)
        }
        layoutManager = LinearLayoutManager(requireContext())
        messageList.layoutManager = layoutManager
        messageList.adapter = messageAdapter
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
                context?.let {
                    formatting = true
                    EmoticonParser.applySpans(it, s, 15f)
                    formatting = false
                }
            }
        })
        view.findViewById<ImageView>(R.id.iv_emoticon_picker).setOnClickListener {
            context?.let { ctx ->
                EmoticonPickerDialog.show(ctx) { code ->
                    val current = etMessage.text.toString()
                    val prefix = if (current.isNotEmpty() && !current.endsWith(" ")) " " else ""
                    etMessage.append("$prefix$code ")
                    etMessage.setSelection(etMessage.text.length)
                }
            }
        }
        SupabaseRealtimeClient.addRoomMessageListener(realtimeListener)
        AppDiagnostics.started("Room.subscribeRealtime", "room=$roomName")
        AppDiagnostics.success("Room.subscribeRealtime", "listener registered for room=$roomName")
        RoomPresenceClient.join(roomName, AppState.nickname) { ok, message ->
            if (!isAdded) return@join
            if (!ok) {
                AppDiagnostics.failed("Room.join", "room=$roomName error=${message ?: "Cannot join room"}")
                AlertDialog.Builder(requireContext()).setTitle("Cannot Join Room").setMessage(message ?: "This room cannot accept you.").setPositiveButton("OK") { _, _ -> host.leaveRoom(roomName) }.show()
                return@join
            }
            joinedSuccessfully = true
            AppDiagnostics.success("Room.join", "room=$roomName joined")
            refreshHistory()
        }
    }

    private fun showModeratorMessageActions(message: ChatMessage) {
        AlertDialog.Builder(requireContext()).setTitle("Moderator: Message #${message.id}").setMessage("${message.sender}:\n${message.body}").setItems(arrayOf("Delete Message", "Mute ${message.sender}", "Kick ${message.sender}", "Report ${message.sender}")) { _, which ->
            when (which) {
                0 -> confirmDeleteMessage(message)
                1 -> applyQuickModeration("mute", message.sender)
                2 -> applyQuickModeration("kick", message.sender)
                3 -> promptReportReason(message.sender)
            }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmDeleteMessage(message: ChatMessage) {
        AlertDialog.Builder(requireContext()).setTitle("Delete message?").setMessage("Delete this message from $roomName?").setPositiveButton("Delete") { _, _ ->
            SafetyClient.deleteRoomMessage(message.id) { success ->
                if (isAdded) Toast.makeText(requireContext(), if (success) "Message deleted" else "Message deletion failed", Toast.LENGTH_SHORT).show()
                if (success) {
                    AppState.messagesFor(roomName).removeAll { it.id == message.id }
                    messageAdapter.removeById(message.id)
                }
            }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun applyQuickModeration(action: String, target: String) {
        SafetyClient.applyModerationAction(target, action, roomName, "Moderator action from $roomName", if (action == "mute") 30 else null) { success ->
            if (isAdded) Toast.makeText(requireContext(), if (success) "${action.uppercase()} applied to $target" else "Moderation action failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshHistory(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!joinedSuccessfully || historyLoadInFlight) return
        if (!force && lastHistoryLoadAt > 0L && now - lastHistoryLoadAt < 1_500L) return
        historyLoadInFlight = true
        isLoadingHistory = true
        val messages = AppState.messagesFor(roomName)
        messages.clear()
        messageAdapter.setItems(emptyList())
        hasMoreHistory = true
        AppDiagnostics.started("Room.fetchLatestMessages", "room=$roomName limit=60")
        UnifiedRoomClient.fetchLatestMessages(roomName, 60) { remote ->
            historyLoadInFlight = false
            isLoadingHistory = false
            if (!isAdded) return@fetchLatestMessages
            if (remote == null) {
                AppDiagnostics.failed("Room.fetchLatestMessages", "No message list returned for room=$roomName")
                Toast.makeText(requireContext(), "Could not load room history.", Toast.LENGTH_SHORT).show()
                return@fetchLatestMessages
            }
            lastHistoryLoadAt = System.currentTimeMillis()
            AppDiagnostics.success("Room.fetchLatestMessages", "room=$roomName loaded=${remote.size}")
            messages.addAll(remote)
            messageAdapter.setItems(remote)
            scrollToLatest()
            hasMoreHistory = remote.size >= 60
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
        AppDiagnostics.started("Room.fetchMessagesBefore", "room=$roomName beforeId=$beforeId limit=50")
        UnifiedRoomClient.fetchMessagesBefore(roomName, beforeId, 50) { older ->
            if (!isAdded) return@fetchMessagesBefore
            isLoadingHistory = false
            if (older == null) {
                AppDiagnostics.failed("Room.fetchMessagesBefore", "No message list returned for room=$roomName beforeId=$beforeId")
                return@fetchMessagesBefore
            }
            AppDiagnostics.success("Room.fetchMessagesBefore", "room=$roomName loaded=${older.size}")
            if (older.isEmpty()) {
                hasMoreHistory = false
                return@fetchMessagesBefore
            }
            current.addAll(0, older)
            messageAdapter.insertAtTop(older)
            layoutManager.scrollToPositionWithOffset(oldFirst + older.size, oldTop)
            if (older.size < 50) hasMoreHistory = false
        }
    }

    private fun scrollToLatest() {
        messageList.post {
            if (messageAdapter.itemCount > 0) messageList.scrollToPosition(messageAdapter.itemCount - 1)
        }
    }

    override fun onResume() {
        super.onResume()
        setupScreen()
        if (joinedSuccessfully) refreshHistory()
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            setupScreen()
            if (joinedSuccessfully) refreshHistory()
        } else {
            stopPolling()
        }
    }

    fun setupScreen() {
        host.setScreenTitle("Chat - $roomName")
        host.showConnectedBall(true)
        host.setSoftkeys(getString(R.string.softkey_options), getString(R.string.softkey_buddies), onLeft = { showOptionsMenu() }, onRight = { host.goToBuddyList() })
    }

    private fun startPolling() {
        if (isPollingActive || !joinedSuccessfully) return
        isPollingActive = true
        pollInFlight = false
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
    }

    private fun scheduleNextPoll() {
        if (isPollingActive && isAdded && joinedSuccessfully) {
            handler.removeCallbacks(pollRunnable)
            handler.postDelayed(pollRunnable, 2_500L)
        }
    }

    private fun stopPolling() {
        isPollingActive = false
        pollInFlight = false
        handler.removeCallbacks(pollRunnable)
    }

    private fun sendCurrentMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty() || !joinedSuccessfully) return
        etMessage.setText("")
        val local = ChatMessage(AppState.nickname, text)
        AppState.messagesFor(roomName).add(local)
        messageAdapter.add(local)
        scrollToLatest()
        AppDiagnostics.started("Room.sendMessage", "room=$roomName chars=${text.length}")
        UnifiedRoomClient.sendMessage(roomName, text) { inserted ->
            if (!isAdded) return@sendMessage
            if (inserted != null) {
                val messages = AppState.messagesFor(roomName)
                val idx = messages.indexOf(local)
                if (idx >= 0) {
                    messages[idx] = inserted
                    val ai = messageAdapter.indexOf(local)
                    if (ai >= 0) messageAdapter.replaceAt(ai, inserted)
                }
                AppDiagnostics.success("Room.sendMessage", "room=$roomName messageId=${inserted.id}")
            } else {
                AppDiagnostics.failed("Room.sendMessage", "No message returned for room=$roomName")
                Toast.makeText(requireContext(), "Message failed to send.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showOptionsMenu() {
        J2mePopup.show(requireContext(), listOf(PopupItem("Send Message"), PopupItem("Send Whisper"), PopupItem("Room Members"), PopupItem("Room Info"), PopupItem("Invite Buddy"), PopupItem("Report"), PopupItem("Leave Room"))) { index ->
            when (index) {
                0 -> etMessage.requestFocus()
                1 -> whisperDialog()
                2 -> startActivity(UzzapParityActivity.intent(requireContext(), UzzapParityActivity.MODE_ROOM_MEMBERS, roomName))
                3 -> AlertDialog.Builder(requireContext()).setTitle(roomName).setMessage("Province chatroom\nLive Supabase room\nMessages: ${AppState.messagesFor(roomName).size}").setPositiveButton("OK", null).show()
                4 -> startActivity(UzzapParityActivity.intent(requireContext(), UzzapParityActivity.MODE_ROOM_ACTIONS, roomName))
                5 -> showReportDialog()
                6 -> host.confirmLeaveRoom()
            }
        }
    }

    private fun showReportDialog() {
        UnifiedRoomClient.fetchParticipants(roomName) { members ->
            if (!isAdded) return@fetchParticipants
            val targets = (members ?: emptyList()).filter { !it.equals(AppState.nickname, true) }
            if (targets.isEmpty()) {
                val input = EditText(requireContext()).apply { hint = "Enter username to report" }
                AlertDialog.Builder(requireContext()).setTitle("Report User in $roomName").setView(input).setPositiveButton("Next") { _, _ ->
                    val target = input.text.toString().trim()
                    if (target.isNotEmpty()) promptReportReason(target)
                }.setNegativeButton(getString(R.string.softkey_cancel), null).show()
                return@fetchParticipants
            }
            AlertDialog.Builder(requireContext()).setTitle("Report User in $roomName").setItems(targets.toTypedArray()) { _, which -> promptReportReason(targets[which]) }.setNegativeButton(getString(R.string.softkey_cancel), null).show()
        }
    }

    private fun promptReportReason(target: String) {
        val reasons = arrayOf("Spam", "Harassment", "Inappropriate Content", "Other")
        AlertDialog.Builder(requireContext()).setTitle("Report $target").setItems(reasons) { _, which ->
            SafetyClient.report(target, reasons[which], "Reported from chatroom: $roomName") { ok ->
                if (isAdded) Toast.makeText(requireContext(), if (ok) "Report submitted for moderation." else "Report submission failed.", Toast.LENGTH_SHORT).show()
            }
        }.setNegativeButton(getString(R.string.softkey_cancel), null).show()
    }

    private fun whisperDialog() {
        UnifiedRoomClient.fetchParticipants(roomName) { members ->
            val targets = (members ?: emptyList()).filter { !it.equals(AppState.nickname, true) }
            if (!isAdded) return@fetchParticipants
            if (targets.isEmpty()) {
                Toast.makeText(requireContext(), "No other room members are online.", Toast.LENGTH_SHORT).show()
                return@fetchParticipants
            }
            AlertDialog.Builder(requireContext()).setTitle("Whisper to").setItems(targets.toTypedArray()) { _, which -> startActivity(PrivateChatActivity.intent(requireContext(), targets[which])) }.setNegativeButton("Cancel", null).show()
        }
    }

    override fun onDestroyView() {
        AppDiagnostics.info(requireContext(), "Room.lifecycle", "destroy room=$roomName")
        SupabaseRealtimeClient.removeRoomMessageListener(realtimeListener)
        stopPolling()
        joinedSuccessfully = false
        historyLoadInFlight = false
        isLoadingHistory = false
        super.onDestroyView()
    }

    companion object {
        private const val ARG_ROOM = "arg_room"
        fun newInstance(roomName: String) = ChatRoomFragment().apply {
            arguments = Bundle().apply { putString(ARG_ROOM, roomName) }
        }
    }
}
