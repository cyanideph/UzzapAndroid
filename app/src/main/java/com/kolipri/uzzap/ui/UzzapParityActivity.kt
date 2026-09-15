package com.kolipri.uzzap.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.LegacyThemeManager
import com.kolipri.uzzap.data.PresenceClient
import com.kolipri.uzzap.data.RoomInvitationClient
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.model.Presence
import com.kolipri.uzzap.ui.dialogs.J2mePopup
import com.kolipri.uzzap.ui.dialogs.PopupItem

class UzzapParityActivity : BaseChromeActivity() {
    override fun contentLayoutRes(): Int? = null
    private lateinit var title: TextView
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(activeTheme.background) }
        title = TextView(this).apply { textSize = 14f; setTextColor(activeTheme.text); setPadding(dp(4), dp(2), dp(4), dp(2)); includeFontPadding = false }
        list = ListView(this).apply { divider = null; dividerHeight = 0; setPadding(0, 0, 0, 0); setBackgroundColor(activeTheme.background) }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(22)))
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        contentFrame.addView(root, FrameLayout.LayoutParams(-1, -1))
        render(intent.getStringExtra(EXTRA_MODE) ?: MODE_MAIN)
    }

    private fun legacyAdapter(values: List<String>): android.widget.ArrayAdapter<String> = object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, values) {
        override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
            val v = super.getView(position, convertView, parent) as TextView
            v.textSize = 13f
            v.includeFontPadding = false
            v.setTextColor(activeTheme.text)
            v.gravity = Gravity.CENTER_VERTICAL
            v.setPadding(dp(5), 0, dp(4), 0)
            v.minimumHeight = dp(24)
            v.setBackgroundResource(com.kolipri.uzzap.R.drawable.bg_menu_row_selector)
            return v
        }
    }

    private fun render(mode: String) {
        when (mode) {
            MODE_PROFILE -> profile()
            MODE_SETTINGS -> settings()
            MODE_NOTIFICATIONS -> notifications()
            MODE_THEMES -> themes()
            MODE_PRIVATE_CHAT -> privateChat(intent.getStringExtra(EXTRA_BUDDY).orEmpty())
            MODE_ROOM_OPTIONS -> roomOptions(intent.getStringExtra(EXTRA_ROOM).orEmpty())
            MODE_ROOM_ACTIONS -> roomActions(intent.getStringExtra(EXTRA_ROOM).orEmpty())
            MODE_ROOM_MEMBERS -> roomMembers(intent.getStringExtra(EXTRA_ROOM).orEmpty())
            else -> mainScreen()
        }
    }

    private fun mainScreen() {
        setScreenTitle("Uzzap")
        showConnectedBall(true)
        setSoftkeys("Options", "Exit", onLeft = { showTopOptions() }, onRight = { finish() })
        title.text = AppState.nickname.ifBlank { AppState.username }
        val values = listOf("Buddy List", "Chat Rooms", "Private Messages", "My Profile", "Settings")
        list.adapter = legacyAdapter(values)
        list.setOnItemClickListener { _, _, position, _ -> when (position) {
            0 -> startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM))
            1 -> startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_CHAT))
            2 -> startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM))
            3 -> open(MODE_PROFILE)
            4 -> open(MODE_SETTINGS)
        } }
    }

    private fun showRows(screenTitle: String, values: List<String>, actions: List<() -> Unit>) {
        setScreenTitle(screenTitle)
        showConnectedBall(true)
        setSoftkeys("Options", "Back", onLeft = { showTopOptions() }, onRight = { finish() })
        title.text = screenTitle
        list.adapter = legacyAdapter(values)
        list.setOnItemClickListener { _, _, position, _ -> actions[position].invoke() }
    }

    private fun profile() {
        setScreenTitle("Update profile")
        showConnectedBall(true)
        setSoftkeys("Update", "Cancel", onLeft = { saveProfile() }, onRight = { finish() })
        val scroll = ScrollView(this).apply { setBackgroundColor(activeTheme.background); isFillViewport = false }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(3), dp(4), dp(6)) }
        fun field(label: String, value: String, hint: String): EditText {
            root.addView(TextView(this).apply { text = label; textSize = 12f; includeFontPadding = false; setTextColor(activeTheme.secondary); setPadding(dp(2), dp(3), dp(2), dp(1)) }, LinearLayout.LayoutParams(-1, dp(18)))
            return EditText(this).apply {
                setText(value); this.hint = hint; textSize = 13f; singleLine = true; includeFontPadding = false
                setTextColor(activeTheme.text); setHintTextColor(activeTheme.secondary); setPadding(dp(3), 0, dp(3), 0)
                background = LegacyThemeManager.cardBackground(activeTheme)
            }.also { root.addView(it, LinearLayout.LayoutParams(-1, dp(28)).apply { bottomMargin = dp(2) }) }
        }
        val nickname = field("Nickname", AppState.nickname, "Your nickname")
        val first = field("First name", AppState.firstName, "Your first name")
        val last = field("Last name", AppState.lastName, "Your last name")
        val email = field("Email address", AppState.email, "Your email address")
        root.tag = arrayOf(nickname, first, last, email)
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        contentFrame.removeAllViews(); contentFrame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun saveProfile() {
        val scroll = contentFrame.getChildAt(0) as? ScrollView ?: return
        val root = scroll.getChildAt(0) as? LinearLayout ?: return
        val fields = root.tag as? Array<*> ?: return
        val nickname = (fields[0] as? EditText)?.text?.toString()?.trim().orEmpty()
        val first = (fields[1] as? EditText)?.text?.toString()?.trim().orEmpty()
        val last = (fields[2] as? EditText)?.text?.toString()?.trim().orEmpty()
        val email = (fields[3] as? EditText)?.text?.toString()?.trim().orEmpty()
        if (nickname.any { it.isWhitespace() || !it.isLetterOrDigit() && it != '_' && it != '-' }) { legacyError("* Nickname must not contain special symbols"); return }
        if (first.isBlank()) { legacyError("* Firstname is too short."); return }
        if (last.isBlank()) { legacyError("* Lastname is too short."); return }
        if (email.length < 3 || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { legacyError("* Incorrect data\n\nPlease make sure to fill out all fields as instructed:\n\nEmail address has an invalid Entry"); return }
        AppState.nickname = nickname
        AppState.firstName = first
        AppState.lastName = last
        AppState.email = email
        AppState.syncProfileToSupabase()
        J2mePopup.showMessage(this, "Update profile", "Update complete. Your profile has been successfully updated.")
    }

    private fun legacyError(message: String) { J2mePopup.showMessage(this, "Error", message) }

    private fun settings() = startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_SETTINGS)).also { finish() }
    private fun notifications() = startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_TONES)).also { finish() }

    private fun themes() {
        setScreenTitle("Choose Theme")
        showConnectedBall(true)
        setSoftkeys("Select", "Back", onLeft = null, onRight = { finish() })
        val themeList = LegacyThemeManager.themes()
        list.adapter = legacyAdapter(themeList.map { theme -> if (theme.id == LegacyThemeManager.current(this).id) "${theme.displayName} *" else theme.displayName })
        list.setOnItemClickListener { _, _, position, _ ->
            val selected = themeList[position]
            LegacyThemeManager.select(this, selected.id)
            AppState.themeName = selected.id
            recreate()
        }
    }

    private fun choosePresence() {
        val options = listOf(PopupItem("Available"), PopupItem("Not Available"), PopupItem("Invisible"), PopupItem("Status Message.."))
        J2mePopup.show(this, options) { index ->
            when (index) {
                0 -> setPresence(Presence.AVAILABLE)
                1 -> setPresence(Presence.NOT_AVAILABLE)
                2 -> setPresence(Presence.INVISIBLE)
                3 -> statusMessage()
            }
        }
    }

    private fun statusMessage() {
        val input = EditText(this).apply { setText(AppState.statusMessage); hint = "Status Message"; textSize = 13f }
        J2mePopup.show(this, listOf(PopupItem("Save"), PopupItem("Cancel"))) { index ->
            if (index == 0) { AppState.statusMessage = input.text.toString(); AppState.syncProfileToSupabase(); toast("Status updated") }
        }
    }

    private fun setPresence(presence: Presence) { AppState.presence = presence; AppState.syncProfileToSupabase(); PresenceClient.heartbeat(presence != Presence.INVISIBLE); toast("Status: ${presence.label}") }

    private fun roomOptions(room: String) = showRows("Room Options", listOf("Join Room", "Select Category", "Refresh Room List", "Refresh Category List", "Room Actions", "Back"), listOf({ toast("Already joined $room") }, { finish() }, { toast("Room list refreshed.") }, { toast("Category list refreshed.") }, { open(MODE_ROOM_ACTIONS, room = room) }, { finish() }))
    private fun roomActions(room: String) = showRows("Room Actions", listOf("List Participants", "Room Info", "Send Whisper", "Invite Participants", "Leave Chatroom", "Back"), listOf({ open(MODE_ROOM_MEMBERS, room = room) }, { J2mePopup.showMessage(this, room, "Chatroom\n$room") }, { open(MODE_ROOM_MEMBERS, room = room) }, { inviteBuddy(room) }, { setResult(RESULT_OK, Intent().putExtra("leave_room", room)); finish() }, { finish() }))
    private fun roomMembers(room: String) { setScreenTitle("Participants - $room"); showConnectedBall(true); setSoftkeys("Options", "Back", onLeft = { showTopOptions() }, onRight = { finish() }); SupabaseClient.fetchParticipants(room) { members -> if (isFinishing) return@fetchParticipants; val result = (members ?: emptyList()).toMutableList(); if (!result.contains(AppState.nickname)) result.add(0, AppState.nickname); list.adapter = legacyAdapter(if (result.isEmpty()) listOf("No participants") else result); list.setOnItemClickListener { _, _, position, _ -> result.getOrNull(position)?.takeIf { it != AppState.nickname }?.let { privateChat(it) } } } }
    private fun privateChat(buddy: String) { if (buddy.isBlank()) { finish(); return }; startActivity(PrivateChatActivity.intent(this, buddy)); finish() }
    private fun inviteBuddy(room: String) { val input = EditText(this).apply { hint = "Buddy nickname"; textSize = 13f }; J2mePopup.show(this, listOf(PopupItem("Invite"), PopupItem("Cancel"))) { index -> if (index == 0) { val buddy = input.text.toString().trim(); if (buddy.isNotBlank()) RoomInvitationClient.send(room, AppState.username, buddy) { ok -> toast(if (ok) "Invitation sent to $buddy" else "Could not send invitation") } } } }
    private fun showTopOptions() { J2mePopup.show(this, listOf(PopupItem("Settings"), PopupItem("Themes"), PopupItem("Notifications"), PopupItem("Back"))) { index -> when (index) { 0 -> open(MODE_SETTINGS); 1 -> open(MODE_THEMES); 2 -> open(MODE_NOTIFICATIONS); else -> finish() } } }
    private fun open(mode: String, room: String? = null, buddy: String? = null) = startActivity(intent(this, mode, room, buddy))
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_MODE = "mode"; private const val EXTRA_ROOM = "room"; private const val EXTRA_BUDDY = "buddy"
        const val MODE_MAIN = "main"; const val MODE_PROFILE = "profile"; const val MODE_SETTINGS = "settings"; const val MODE_NOTIFICATIONS = "notifications"; const val MODE_THEMES = "themes"; const val MODE_PRIVATE_CHAT = "private_chat"; const val MODE_ROOM_OPTIONS = "room_options"; const val MODE_ROOM_ACTIONS = "room_actions"; const val MODE_ROOM_MEMBERS = "room_members"
        fun intent(context: Context, mode: String, room: String? = null, buddy: String? = null) = Intent(context, UzzapParityActivity::class.java).apply { putExtra(EXTRA_MODE, mode); if (room != null) putExtra(EXTRA_ROOM, room); if (buddy != null) putExtra(EXTRA_BUDDY, buddy) }
    }
}
