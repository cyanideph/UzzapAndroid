package com.kolipri.uzzap.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.kolipri.uzzap.data.AppState
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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 8, 8, 8) }
        title = TextView(this).apply { textSize = 18f; setPadding(8, 8, 8, 12) }
        list = ListView(this); root.addView(title); root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f)); contentFrame.addView(root, FrameLayout.LayoutParams(-1, -1))
        render(intent.getStringExtra(EXTRA_MODE) ?: MODE_MAIN)
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
        title.text = "Welcome, ${AppState.nickname.ifBlank { AppState.username }}"
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("Buddy List", "Chatrooms", "Private Messages", "My Profile", "Settings"))
        list.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM))
                1 -> startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_CHAT))
                2 -> startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM))
                3 -> open(MODE_PROFILE)
                4 -> open(MODE_SETTINGS)
            }
        }
    }

    private fun showRows(screenTitle: String, values: List<String>, actions: List<() -> Unit>) {
        setScreenTitle(screenTitle)
        showConnectedBall(true)
        setSoftkeys("Options", "Back", onLeft = { showTopOptions() }, onRight = { finish() })
        title.text = screenTitle
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, values)
        list.setOnItemClickListener { _, _, position, _ -> actions[position].invoke() }
    }

    private fun profile() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 28)
        }
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        contentFrame.removeAllViews()
        contentFrame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setScreenTitle("My Profile")
        setSoftkeys("Save", "Back", onLeft = { saveProfile(root) }, onRight = { finish() })
        showConnectedBall(true)

        val currentNickname = AppState.nickname.trim().ifBlank { AppState.username }
        val avatar = TextView(this).apply {
            text = currentNickname.firstOrNull()?.uppercase() ?: "U"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(13, 12, 175), 40f)
            contentDescription = "Profile avatar"
        }
        root.addView(avatar, LinearLayout.LayoutParams(76, 76).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = 10 })

        val name = TextView(this).apply {
            text = currentNickname
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(name, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 2 })

        val handle = TextView(this).apply {
            text = "@${AppState.username}"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
        }
        root.addView(handle, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })

        section(root, "PROFILE")
        val nickname = EditText(this).apply {
            hint = "Nickname"
            setText(currentNickname)
            isSingleLine = true
            maxLines = 1
            maxEms = 64
            setPadding(16, 12, 16, 12)
            background = rounded(Color.rgb(245, 245, 245), 14f)
            contentDescription = "Nickname"
        }
        root.addView(nickname, rowParams())

        section(root, "PRESENCE")
        val presence = TextView(this).apply {
            text = "●  ${AppState.presence.label}"
            textSize = 16f
            setPadding(16, 15, 16, 15)
            background = rounded(Color.WHITE, 14f)
            elevation = 2f
            setOnClickListener { choosePresence() }
            contentDescription = "Change presence"
        }
        root.addView(presence, rowParams())

        section(root, "ABOUT ME")
        val status = EditText(this).apply {
            hint = "What are you doing?"
            setText(AppState.statusMessage)
            minLines = 2
            maxLines = 4
            setPadding(16, 12, 16, 12)
            background = rounded(Color.rgb(245, 245, 245), 14f)
            contentDescription = "Status message"
        }
        root.addView(status, rowParams().apply { bottomMargin = 12 })

        section(root, "ACCOUNT")
        val account = TextView(this).apply {
            text = "Username\n${AppState.username}\n\nNickname\n${currentNickname}"
            textSize = 14f
            setPadding(16, 14, 16, 14)
            background = rounded(Color.WHITE, 14f)
        }
        root.addView(account, rowParams())
        root.tag = ProfileFields(nickname, status, avatar, name, account)
    }

    private data class ProfileFields(
        val nickname: EditText,
        val status: EditText,
        val avatar: TextView,
        val name: TextView,
        val account: TextView
    )

    private fun section(root: LinearLayout, label: String) {
        root.addView(TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(13, 12, 175))
            setPadding(4, 14, 4, 7)
        })
    }

    private fun rowParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }

    private fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun saveProfile(root: LinearLayout) {
        val fields = root.tag as? ProfileFields ?: return
        val rawNickname = fields.nickname.text.toString().trim()
        val visibleNickname = rawNickname.trim()
        when {
            rawNickname.isBlank() -> Toast.makeText(this, "Nickname cannot be empty.", Toast.LENGTH_SHORT).show()
            rawNickname.length > 64 -> Toast.makeText(this, "Nickname is limited to 64 characters.", Toast.LENGTH_SHORT).show()
            visibleNickname.isBlank() -> Toast.makeText(this, "Nickname must contain visible text.", Toast.LENGTH_SHORT).show()
            else -> {
                AppState.nickname = visibleNickname
                AppState.statusMessage = fields.status.text.toString().trim()
                fields.account.text = "Username\n${AppState.username}\n\nNickname\n${visibleNickname}"
                fields.avatar.text = visibleNickname.firstOrNull()?.uppercase() ?: "U"
                fields.name.text = visibleNickname
                AppState.syncProfileToSupabase()
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun choosePresence() {
        val values = arrayOf("Available", "Not Available", "Invisible")
        val current = when (AppState.presence) { Presence.AVAILABLE -> 0; Presence.NOT_AVAILABLE -> 1; Presence.INVISIBLE -> 2 }
        AlertDialog.Builder(this).setTitle("Presence")
            .setSingleChoiceItems(values, current) { dialog, which ->
                AppState.presence = when (which) { 1 -> Presence.NOT_AVAILABLE; 2 -> Presence.INVISIBLE; else -> Presence.AVAILABLE }
                AppState.syncProfileToSupabase()
                dialog.dismiss()
                profile()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun roomOptions(room: String) = showRows("Room Options", listOf("Join Room", "Select Category", "Refresh Room List", "Refresh Category List", "Room Actions", "Back"), listOf(
        { Toast.makeText(this, "Already joined $room", Toast.LENGTH_SHORT).show() }, { finish() },
        { Toast.makeText(this, "Room list refreshed.", Toast.LENGTH_SHORT).show() }, { Toast.makeText(this, "Category list refreshed.", Toast.LENGTH_SHORT).show() },
        { open(MODE_ROOM_ACTIONS, room = room) }, { finish() }
    ))

    private fun roomActions(room: String) = showRows("Room Actions", listOf("Room Members", "Room Info", "Whisper / Private Message", "Invite Buddy", "Leave Room", "Report", "Back"), listOf(
        { open(MODE_ROOM_MEMBERS, room = room) },
        { AlertDialog.Builder(this).setTitle(room).setMessage("Province chatroom\nRoom: $room\nBackend: Network").setPositiveButton("Close", null).show() },
        { open(MODE_ROOM_MEMBERS, room = room) },
        { inviteBuddy(room) },
        { setResult(RESULT_OK, Intent().putExtra("leave_room", room)); finish() },
        { Toast.makeText(this, "Report flow available for moderation.", Toast.LENGTH_SHORT).show() }, { finish() }
    ))

    private fun roomMembers(room: String) {
        title.text = "Room Members"; setScreenTitle("Members - $room"); setSoftkeys("Options", "Back", onLeft = { showTopOptions() }, onRight = { finish() })
        SupabaseClient.fetchParticipants(room) { members ->
            if (isFinishing) return@fetchParticipants

            // A nickname can differ from the stable username only by case (for example
            // "CY" vs "cy"). The previous implementation used case-sensitive contains(),
            // which added the current user a second time when the backend already returned
            // the same member under a different case. Normalize and de-duplicate display
            // names before rendering, while preserving the current saved nickname.
            val currentNickname = AppState.nickname.trim()
                .ifBlank { AppState.username.trim() }
            val currentKey = currentNickname.trim().lowercase()

            val normalizedMembers = (members ?: emptyList())
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .toMutableList()

            // Do not add the current user if the backend already returned the same
            // account under a different case. If it did, replace that display value
            // with the current saved nickname so the room reflects profile changes.
            val existingCurrentIndex = normalizedMembers.indexOfFirst { it.lowercase() == currentKey }
            if (existingCurrentIndex >= 0) {
                normalizedMembers[existingCurrentIndex] = currentNickname
            } else if (currentNickname.isNotEmpty()) {
                normalizedMembers.add(0, currentNickname)
            }

            // Final case-insensitive de-duplication protects against duplicate rows
            // returned by the backend as well as username/nickname casing differences.
            val result = normalizedMembers
                .asSequence()
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .toList()

            val display = if (result.isEmpty()) listOf("No members online") else result.map { "● $it" }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, display)
            list.setOnItemClickListener { _, _, position, _ ->
                result.getOrNull(position)
                    ?.takeIf { !it.equals(currentNickname, true) }
                    ?.let { open(MODE_PRIVATE_CHAT, buddy = it) }
            }
        }
    }

    private fun privateChat(buddy: String) { if (buddy.isBlank()) { finish(); return }; startActivity(PrivateChatActivity.intent(this, buddy)); finish() }
    private fun settings() = startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_SETTINGS)).also { finish() }
    private fun notifications() = startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_TONES)).also { finish() }

    private fun themes() {
        val themes = arrayOf("Classic Uzzap", "Dark", "Black", "Blue", "Green")
        val current = getSharedPreferences(PREFS, MODE_PRIVATE).getString("theme", "Classic Uzzap")
        AlertDialog.Builder(this).setTitle("Choose Theme").setSingleChoiceItems(themes, themes.indexOf(current).coerceAtLeast(0)) { dialog, which ->
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("theme", themes[which]).apply(); AppState.themeName = themes[which]; dialog.dismiss(); Toast.makeText(this, "${themes[which]} selected", Toast.LENGTH_SHORT).show(); ThemeManager.setTheme(this, themes[which]); recreate()
        }.show()
    }

    private fun inviteBuddy(room: String) {
        UzzapDialogs.form(this, iconAsset = "anim-icons/menu_connect.gif", title = "Invite to $room", fields = listOf(UzzapDialogs.FieldSpec("Buddy nickname")), positive = "Invite", onPositive = { f ->
            val buddy = f[0].text.toString().trim()
            if (buddy.isBlank()) { f[0].error = "Enter a buddy nickname"; return@form false }
            RoomInvitationClient.send(room, AppState.username, buddy) { ok -> Toast.makeText(this, if (ok) "Invitation sent to $buddy" else "Could not send invitation", Toast.LENGTH_SHORT).show() }
            true
        })
    }

    private fun showTopOptions() {
        J2mePopup.show(this, listOf(PopupItem("Settings"), PopupItem("Themes"), PopupItem("Notifications"), PopupItem("Back"))) { index -> when (index) { 0 -> open(MODE_SETTINGS); 1 -> open(MODE_THEMES); 2 -> open(MODE_NOTIFICATIONS); else -> finish() } }
    }

    private fun open(mode: String, room: String? = null, buddy: String? = null) = startActivity(intent(this, mode, room, buddy))

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_ROOM = "room"
        private const val EXTRA_BUDDY = "buddy"
        private const val PREFS = "uzzap_prefs"
        const val MODE_MAIN = "main"
        const val MODE_PROFILE = "profile"
        const val MODE_SETTINGS = "settings"
        const val MODE_NOTIFICATIONS = "notifications"
        const val MODE_THEMES = "themes"
        const val MODE_PRIVATE_CHAT = "private_chat"
        const val MODE_ROOM_OPTIONS = "room_options"
        const val MODE_ROOM_ACTIONS = "room_actions"
        const val MODE_ROOM_MEMBERS = "room_members"

        fun intent(context: Context, mode: String, room: String? = null, buddy: String? = null) = Intent(context, UzzapParityActivity::class.java).apply {
            putExtra(EXTRA_MODE, mode)
            if (room != null) putExtra(EXTRA_ROOM, room)
            if (buddy != null) putExtra(EXTRA_BUDDY, buddy)
        }
    }
}