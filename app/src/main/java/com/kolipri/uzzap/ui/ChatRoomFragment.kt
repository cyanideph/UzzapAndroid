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
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.RoomPresenceClient
import com.kolipri.uzzap.data.SafetyClient
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.data.SupabaseRealtimeClient
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.ui.dialogs.EmoticonPickerDialog
import com.kolipri.uzzap.ui.dialogs.J2mePopup
import com.kolipri.uzzap.ui.dialogs.PopupItem
import com.kolipri.uzzap.ui.util.EmoticonParser

class ChatRoomFragment : Fragment(R.layout.fragment_chat_room) {
    var roomName: String = "Main Lobby"; private set
    private lateinit var messageList: RecyclerView
    private lateinit var messageAdapter: RoomMessageAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var etMessage: EditText
    private val host get() = requireActivity() as ChatHostActivity
    private val handler = Handler(Looper.getMainLooper())
    private var isPollingActive = false
    private var joinedSuccessfully = false
    private var isLoadingHistory = false
    private var hasMoreHistory = true
    private val realtimeListener: (ChatMessage, String) -> Unit = { msg, targetRoom ->
        if (isAdded && joinedSuccessfully && targetRoom.equals(roomName, ignoreCase = true)) {
            val current = AppState.messagesFor(roomName)
            val pending = current.firstOrNull { it.id == 0L && it.sender == msg.sender && it.body == msg.body }
            if (pending != null) {
                val idx = current.indexOf(pending)
                if (idx >= 0) {
                    current[idx] = msg
                    val adapterIndex = messageAdapter.indexOf(pending)
                    if (adapterIndex >= 0) messageAdapter.replaceAt(adapterIndex, msg)
                }
            } else if (msg.id > 0L && current.none { it.id == msg.id }) {
                current.add(msg)
                messageAdapter.add(msg)
                scrollToLatest()
            }
        }
    }
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isPollingActive || !isAdded || !joinedSuccessfully) return
            val current = AppState.messagesFor(roomName)
            val maxId = current.filter { it.id > 0L }.maxOfOrNull { it.id } ?: 0L
            SupabaseClient.fetchMessages(roomName, afterId = maxId) { newMsgs ->
                if (!isAdded || newMsgs == null) return@fetchMessages
                for (msg in newMsgs) {
                    val pending = current.firstOrNull { it.id == 0L && it.sender == msg.sender && it.body == msg.body }
                    if (pending != null) {
                        val idx = current.indexOf(pending)
                        if (idx >= 0) { current[idx] = msg; val adapterIndex = messageAdapter.indexOf(pending); if (adapterIndex >= 0) messageAdapter.replaceAt(adapterIndex, msg) }
                    } else if (msg.id > 0L && current.none { it.id == msg.id }) {
                        current.add(msg); messageAdapter.add(msg)
                    }
                }
                if (newMsgs.isNotEmpty()) scrollToLatest()
            }
            SupabaseClient.pingPresence(roomName, AppState.nickname)
            handler.postDelayed(this, 2500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); arguments?.getString(ARG_ROOM)?.let { roomName = it } }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        messageList = view.findViewById(R.id.room_messages)
        messageAdapter = RoomMessageAdapter(requireContext())
        messageAdapter.onMessageLongClickListener = { message ->
            if (AppState.isModerator && message.id > 0L) showModeratorMessageActions(message)
        }
        layoutManager = LinearLayoutManager(requireContext())
        messageList.layoutManager = layoutManager
        messageList.adapter = messageAdapter
        messageList.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom && messageAdapter.itemCount > 0) messageList.post { scrollToLatest() }
        }
        messageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
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
            override fun afterTextChanged(s: Editable?) { if (formatting || s == null) return; val ctx = context ?: return; formatting = true; EmoticonParser.applySpans(ctx, s, 15f); formatting = false }
        })
        view.findViewById<ImageView>(R.id.iv_emoticon_picker).setOnClickListener { context?.let { ctx -> EmoticonPickerDialog.show(ctx) { code -> val current = etMessage.text.toString(); val prefix = if (current.isNotEmpty() && !current.endsWith(" ")) " " else ""; etMessage.append("$prefix$code "); etMessage.setSelection(etMessage.text.length) } } }
        SupabaseRealtimeClient.addRoomMessageListener(realtimeListener)
        RoomPresenceClient.join(roomName, AppState.nickname) { ok, message ->
            if (!isAdded) return@join
            if (!ok) { AlertDialog.Builder(requireContext()).setTitle("Room Full").setMessage(message ?: "This room cannot accept another participant.").setPositiveButton("OK") { _, _ -> host.leaveRoom(roomName) }.setOnCancelListener { host.leaveRoom(roomName) }.show(); return@join }
            joinedSuccessfully = true
            refreshHistory()
        }
    }

    private fun showModeratorMessageActions(message: ChatMessage) {
        AlertDialog.Builder(requireContext())
            .setTitle("Moderator: Message #${message.id}")
            .setMessage("${message.sender}:\n${message.body}")
            .setItems(arrayOf("Delete Message", "Mute ${message.sender}", "Kick ${message.sender}", "Report ${message.sender}")) { _, which ->
                when (which) {
                    0 -> confirmDeleteMessage(message)
                    1 -> applyQuickModeration("mute", message.sender)
                    2 -> applyQuickModeration("kick", message.sender)
                    3 -> promptReportReason(message.sender)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteMessage(message: ChatMessage) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete message?")
            .setMessage("Delete this message from $roomName? This action is recorded by the backend.")
            .setPositiveButton("Delete") { _, _ ->
                SafetyClient.deleteRoomMessage(message.id) { success ->
                    if (!isAdded) return@deleteRoomMessage
                    if (success) {
                        AppState.messagesFor(roomName).removeAll { it.id == message.id }
                        messageAdapter.removeById(message.id)
                        Toast.makeText(requireContext(), "Message deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Message deletion failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyQuickModeration(action: String, target: String) {
        SafetyClient.applyModerationAction(target, action, roomName, "Moderator action from $roomName", if (action == "mute") 30 else null) { success ->
            if (isAdded) Toast.makeText(requireContext(), if (success) "${action.uppercase()} applied to $target" else "Moderation action failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshHistory() {
        val messages = AppState.messagesFor(roomName)
        messages.clear(); messageAdapter.setItems(emptyList()); hasMoreHistory = true; isLoadingHistory = true
        SupabaseClient.fetchLatestMessages(roomName, limit = 60) { remote ->
            if (!isAdded) return@fetchLatestMessages
            isLoadingHistory = false
            if (remote == null) { Toast.makeText(requireContext(), "Could not load room history. Check your connection.", Toast.LENGTH_SHORT).show(); return@fetchLatestMessages }
            messages.addAll(remote); messageAdapter.setItems(remote); scrollToLatest()
            hasMoreHistory = remote.size >= 60
            startPolling()
        }
    }

    private fun loadOlderHistory() {
        if (!joinedSuccessfully || isLoadingHistory || !hasMoreHistory) return
        val current = AppState.messagesFor(roomName)
        val beforeId = current.filter { it.id > 0L }.minOfOrNull { it.id } ?: return
        isLoadingHistory = true
        val oldFirstVisible = layoutManager.findFirstVisibleItemPosition()
        val oldTop = layoutManager.findViewByPosition(oldFirstVisible)?.top ?: 0
        SupabaseClient.fetchMessagesBefore(roomName, beforeId, 50) { older ->
            if (!isAdded) return@fetchMessagesBefore
            isLoadingHistory = false
            if (older == null) return@fetchMessagesBefore
            if (older.isEmpty()) { hasMoreHistory = false; return@fetchMessagesBefore }
            current.addAll(0, older); messageAdapter.insertAtTop(older)
            layoutManager.scrollToPositionWithOffset(oldFirstVisible + older.size, oldTop)
            if (older.size < 50) hasMoreHistory = false
        }
    }

    private fun scrollToLatest() { messageList.post { if (messageAdapter.itemCount > 0) messageList.scrollToPosition(messageAdapter.itemCount - 1) } }

    override fun onResume() { super.onResume(); setupScreen(); if (joinedSuccessfully) refreshHistory() }
    override fun onPause() { super.onPause(); stopPolling() }
    override fun onHiddenChanged(hidden: Boolean) { super.onHiddenChanged(hidden); if (!hidden) { setupScreen(); if (joinedSuccessfully) refreshHistory() } else stopPolling() }
    fun setupScreen() { host.setScreenTitle("Chat - $roomName"); host.showConnectedBall(true); host.setSoftkeys(getString(R.string.softkey_options), getString(R.string.softkey_buddies), onLeft = { showOptionsMenu() }, onRight = { host.goToBuddyList() }) }
    private fun startPolling() { if (isPollingActive || !joinedSuccessfully) return; isPollingActive = true; handler.removeCallbacks(pollRunnable); handler.post(pollRunnable) }
    private fun stopPolling() { isPollingActive = false; handler.removeCallbacks(pollRunnable) }

    private fun sendCurrentMessage() {
        val text = etMessage.text.toString().trim(); if (text.isEmpty() || !joinedSuccessfully) return
        etMessage.setText("")
        val local = ChatMessage(AppState.nickname, text); AppState.messagesFor(roomName).add(local); messageAdapter.add(local); scrollToLatest()
        SupabaseClient.sendMessage(roomName, AppState.nickname, text) { inserted ->
            if (inserted != null && inserted.id > 0) {
                val messages = AppState.messagesFor(roomName); val idx = messages.indexOf(local)
                if (idx >= 0) { messages[idx] = inserted; val adapterIndex = messageAdapter.indexOf(local); if (adapterIndex >= 0) messageAdapter.replaceAt(adapterIndex, inserted) }
            }
        }
    }

    private fun showOptionsMenu() { J2mePopup.show(requireContext(), listOf(PopupItem("Send Message"), PopupItem("Send Whisper"), PopupItem("Room Members"), PopupItem("Room Info"), PopupItem("Invite Buddy"), PopupItem("Report"), PopupItem("Leave Room"))) { index -> when (index) { 0 -> etMessage.requestFocus(); 1 -> whisperDialog(); 2 -> startActivity(UzzapParityActivity.intent(requireContext(), UzzapParityActivity.MODE_ROOM_MEMBERS, roomName)); 3 -> AlertDialog.Builder(requireContext()).setTitle(roomName).setMessage("Province chatroom\nLive Supabase room\nMessages: ${AppState.messagesFor(roomName).size}").setPositiveButton("OK", null).show(); 4 -> startActivity(UzzapParityActivity.intent(requireContext(), UzzapParityActivity.MODE_ROOM_ACTIONS, roomName)); 5 -> showReportDialog(); 6 -> host.confirmLeaveRoom() } } }
    private fun showReportDialog() {
        SupabaseClient.fetchParticipants(roomName) { members ->
            if (!isAdded) return@fetchParticipants
            val targets = (members ?: emptyList()).filter { !it.equals(AppState.nickname, true) }
            if (targets.isEmpty()) {
                val input = EditText(requireContext()).apply { hint = "Enter username to report" }
                AlertDialog.Builder(requireContext()).setTitle("Report User in $roomName").setView(input).setPositiveButton("Next") { _, _ -> val target = input.text.toString().trim(); if (target.isNotEmpty()) promptReportReason(target) }.setNegativeButton(getString(R.string.softkey_cancel), null).show()
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
    private fun whisperDialog() { SupabaseClient.fetchParticipants(roomName) { members -> val targets = (members ?: emptyList()).filter { !it.equals(AppState.nickname, true) }; if (!isAdded) return@fetchParticipants; if (targets.isEmpty()) { Toast.makeText(requireContext(), "No other room members are online.", Toast.LENGTH_SHORT).show(); return@fetchParticipants }; AlertDialog.Builder(requireContext()).setTitle("Whisper to").setItems(targets.toTypedArray()) { _, which -> startActivity(PrivateChatActivity.intent(requireContext(), targets[which])) }.setNegativeButton("Cancel", null).show() } }
    override fun onDestroyView() {
        super.onDestroyView()
        SupabaseRealtimeClient.removeRoomMessageListener(realtimeListener)
        isPollingActive = false
        handler.removeCallbacksAndMessages(null)
    }
    companion object { private const val ARG_ROOM = "arg_room"; fun newInstance(roomName: String) = ChatRoomFragment().apply { arguments = Bundle().apply { putString(ARG_ROOM, roomName) } } }
}
