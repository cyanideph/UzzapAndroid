package com.kolipri.uzzap.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.content.Intent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.PresenceClient
import com.kolipri.uzzap.data.SessionStore
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.model.Presence
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Modern Uzzap settings screens using the same black/white/black shared chrome. */
class LegacySettingsActivity : BaseChromeActivity() {
    private val http = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private var currentForm: LinearLayout? = null

    override fun contentLayoutRes(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showConnectedBall(true)
        render(intent.getStringExtra(EXTRA_MODE) ?: MODE_SETTINGS)
    }

    private fun render(mode: String) {
        when (mode) {
            MODE_STATUS -> statusScreen()
            MODE_PASSWORD -> passwordScreen()
            MODE_MOBILE -> mobileScreen()
            MODE_OFFLINE -> offlineScreen()
            MODE_TONES -> tonesScreen()
            MODE_BATTERY -> batteryScreen()
            MODE_EM -> extendedMessagingScreen()
            MODE_IM -> instantMessagingScreen()
            MODE_PRIVACY -> privacyScreen()
            MODE_SUBSCRIPTION -> subscriptionScreen()
            else -> settingsScreen()
        }
    }

    private fun prepareScreen(screenTitle: String, left: String? = "Options", right: String? = "Back", onLeft: (() -> Unit)? = { showTopOptions() }, onRight: (() -> Unit)? = { finish() }) {
        setScreenTitle(screenTitle)
        setSoftkeys(left, right, onLeft = onLeft, onRight = onRight)
        showConnectedBall(true)
        contentFrame.removeAllViews()
        currentForm = null
    }

    private fun container(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(18))
    }

    private fun scroll(root: View): ScrollView = ScrollView(this).apply {
        isFillViewport = true
        addView(root, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showContent(view: View) {
        contentFrame.removeAllViews()
        contentFrame.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    private fun row(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null): TextView = TextView(this).apply {
        text = if (subtitle.isNullOrBlank()) title else "$title\n$subtitle"
        textSize = 16f
        setTextColor(Color.rgb(25, 25, 25))
        setPadding(dp(16), dp(14), dp(16), dp(if (subtitle.isNullOrBlank()) 14 else 13))
        background = rounded(Color.WHITE, 14f)
        elevation = dp(2).toFloat()
        minHeight = dp(54)
        gravity = Gravity.CENTER_VERTICAL
        contentDescription = title
        isClickable = onClick != null
        if (onClick != null) setOnClickListener { onClick.invoke() }
    }

    private fun addRow(root: LinearLayout, view: View) {
        root.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun section(root: LinearLayout, label: String) {
        root.addView(TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(13, 12, 175))
            setPadding(dp(4), dp(12), dp(4), dp(7))
        })
    }

    private fun rows(title: String, values: List<String>, handlers: List<() -> Unit>, subtitles: List<String?> = emptyList()) {
        prepareScreen(title)
        val root = container()
        section(root, title.uppercase())
        values.forEachIndexed { index, label -> addRow(root, row(label, subtitles.getOrNull(index), handlers[index])) }
        showContent(scroll(root))
    }

    private fun settingsScreen() {
        val labels = listOf("Edit My Profile", "Change Status", "Change Password", "Change Mobile Number", "Offline Settings", "Chatroom Tones", "Battery Saving", "Extended Messaging", "Instant Messaging", "Privacy", "Theme", "Subscription", "Delete Account", "Back")
        val subtitles = listOf("Nickname, presence and about me", "Available / Not Available / Invisible", "Update your account password", "Update the mobile number on your account", "Offline message and status behavior", "Chatroom, PM and buddy notification sounds", "Reduce background activity", "Buddy list and messaging tools", "Private messaging entry point", "Invitation and presence controls", "Choose the Uzzap appearance", "Account subscription information", "Permanently delete account and chat history", "Return to Uzzap")
        rows("Settings", labels, labels.map { label ->
            {
                when (label) {
                    "Edit My Profile" -> startActivity(UzzapParityActivity.intent(this, UzzapParityActivity.MODE_PROFILE))
                    "Change Status" -> open(MODE_STATUS)
                    "Change Password" -> open(MODE_PASSWORD)
                    "Change Mobile Number" -> open(MODE_MOBILE)
                    "Offline Settings" -> open(MODE_OFFLINE)
                    "Chatroom Tones" -> open(MODE_TONES)
                    "Battery Saving" -> open(MODE_BATTERY)
                    "Extended Messaging" -> open(MODE_EM)
                    "Instant Messaging" -> open(MODE_IM)
                    "Privacy" -> open(MODE_PRIVACY)
                    "Theme" -> startActivity(UzzapParityActivity.intent(this, UzzapParityActivity.MODE_THEMES))
                    "Subscription" -> open(MODE_SUBSCRIPTION)
                    "Delete Account" -> confirmDeleteAccount()
                    else -> finish()
                }
            }
        }, subtitles)
    }

    private fun confirmDeleteAccount() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to permanently delete your account?\n\nThis will remove your profile, buddy list, and message history. This action cannot be undone.")
            .setPositiveButton("Delete Permanently") { _, _ ->
                performAccountDeletion()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performAccountDeletion() {
        val progress = AlertDialog.Builder(this)
            .setTitle("Deleting Account")
            .setMessage("Please wait while your account is deleted...")
            .setCancelable(false)
            .show()

        SupabaseClient.deleteAccount { success ->
            progress.dismiss()
            Toast.makeText(this, if (success) "Account deleted successfully." else "Local account session cleared.", Toast.LENGTH_SHORT).show()
            AppState.resetSession()
            SessionStore.clear()
            val intent = Intent(this, SplashMenuActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun statusScreen() {
        rows("Change Status", listOf("Status Message", "Available", "Not Available", "Invisible"), listOf(
            { statusMessageDialog() }, { setPresence(Presence.AVAILABLE) }, { setPresence(Presence.NOT_AVAILABLE) }, { setPresence(Presence.INVISIBLE) }
        ), listOf("Set your personal status message", "Show me as available", "Show me as not available", "Hide my online presence"))
    }

    private fun setPresence(presence: Presence) {
        AppState.presence = presence
        AppState.syncProfileToSupabase()
        PresenceClient.heartbeat(presence != Presence.INVISIBLE)
        toast("Status: ${presence.label}")
    }

    private fun statusMessageDialog() {
        val input = EditText(this).apply { setText(AppState.statusMessage); hint = "Status message" }
        AlertDialog.Builder(this).setTitle("Status Message").setView(input)
            .setPositiveButton("Save") { _, _ -> AppState.statusMessage = input.text.toString(); AppState.syncProfileToSupabase() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun passwordScreen() {
        prepareScreen("Change Password", "Save", "Back", onLeft = {
            val a = currentForm?.getChildAt(1) as? EditText
            val b = currentForm?.getChildAt(2) as? EditText
            val pa = a?.text?.toString().orEmpty(); val pb = b?.text?.toString().orEmpty()
            when {
                pa.length < 6 -> toast("Password must be at least 6 characters")
                pa != pb -> toast("Passwords do not match")
                else -> updateAuthUser(JSONObject().put("password", pa))
            }
        }, onRight = { finish() })
        val root = container()
        section(root, "ACCOUNT SECURITY")
        val newPassword = EditText(this).apply { hint = "New password"; tag = "new"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setPadding(dp(16), dp(12), dp(16), dp(12)); background = rounded(Color.rgb(245,245,245), 14f); minHeight = dp(54) }
        val confirm = EditText(this).apply { hint = "Confirm new password"; tag = "confirm"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setPadding(dp(16), dp(12), dp(16), dp(12)); background = rounded(Color.rgb(245,245,245), 14f); minHeight = dp(54) }
        addRow(root, newPassword); addRow(root, confirm)
        currentForm = root
        showContent(scroll(root))
    }

    private fun mobileScreen() {
        prepareScreen("Change Mobile Number", "Save", "Back", onLeft = {
            val input = currentForm?.getChildAt(1) as? EditText
            val phone = input?.text?.toString()?.trim().orEmpty()
            if (phone.length < 7) toast("Enter a valid mobile number") else updateAuthUser(JSONObject().put("phone", phone))
        })
        val root = container(); section(root, "PHONE")
        val input = EditText(this).apply { hint = "+63 mobile number"; inputType = InputType.TYPE_CLASS_PHONE; setPadding(dp(16), dp(12), dp(16), dp(12)); background = rounded(Color.rgb(245,245,245), 14f); minHeight = dp(54) }
        addRow(root, input); currentForm = root
        showContent(scroll(root))
    }

    private fun toggleScreen(title: String, labels: List<String>, prefix: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        rows(title, labels, labels.map { label ->
            {
                val key = "${prefix}_${label.hashCode()}"
                val next = !prefs.getBoolean(key, true)
                prefs.edit().putBoolean(key, next).apply()
                toast("$label: ${if (next) "ON" else "OFF"}")
            }
        }, labels.map { "Tap to toggle" })
    }

    private fun offlineScreen() = toggleScreen("Offline Settings", listOf("Receive messages while offline", "Store offline messages", "Show offline status"), "offline")
    private fun tonesScreen() {
        val labels = listOf("Chatroom message tone", "Private message tone", "Buddy online tone", "Room invitation tone", "Vibrate", "Play Test Alert Sound & Notification")
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        rows("Chatroom Tones", labels, labels.map { label ->
            {
                if (label == "Play Test Alert Sound & Notification") {
                    com.kolipri.uzzap.data.UzzapNotificationManager.sendTestNotification(this, "uzzapbot", "Kamusta cy! Testing Uzzap notification and alert sound :)")
                    toast("Playing alert sound & test notification!")
                } else {
                    val key = "tone_${label.hashCode()}"
                    val next = !prefs.getBoolean(key, true)
                    prefs.edit().putBoolean(key, next).apply()
                    toast("$label: ${if (next) "ON" else "OFF"}")
                }
            }
        }, labels.map { if (it == "Play Test Alert Sound & Notification") "Tap to test sound & alert" else "Tap to toggle" })
    }

    private fun batteryScreen() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val enabled = prefs.getBoolean("battery_saving", false)
        prepareScreen("Battery Saving", null, "Back", onLeft = null, onRight = { finish() })
        val root = container(); section(root, "BATTERY")
        addRow(root, row("Background activity", if (enabled) "Battery Saving is ON" else "Battery Saving is OFF") {
            prefs.edit().putBoolean("battery_saving", !enabled).apply()
            toast("Battery Saving: ${if (!enabled) "ON" else "OFF"}")
        })
        addRow(root, row("What it does", "Reduces background polling and presence checks."))
        showContent(scroll(root))
    }

    private fun extendedMessagingScreen() = rows("Extended Messaging", listOf("Buddy List", "Add Buddy", "Groups", "Unread Messages"), listOf(
        { startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM)) }, { startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM)) }, { toast("Groups are available from the Buddy List") }, { startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM)) }
    ), listOf("Open your buddies", "Add a contact", "Manage buddy groups", "View unread private messages"))

    private fun instantMessagingScreen() = rows("Instant Messaging", listOf("Choose a buddy", "Back"), listOf({ startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM)) }, { finish() }), listOf("Open the private messaging list", "Return to Settings"))

    private fun privacyScreen() = toggleScreen("Privacy", listOf("Allow buddy invitations", "Allow room invitations", "Show my online presence"), "privacy")

    private fun subscriptionScreen() {
        prepareScreen("Subscription", null, "Back", onLeft = null, onRight = { finish() })
        val root = container(); section(root, "ACCOUNT")
        addRow(root, row("Uzzap Subscription", "Classic carrier subscription is not required for this modern Supabase build."))
        showContent(scroll(root))
    }

    private fun updateAuthUser(payload: JSONObject) {
        val token = com.kolipri.uzzap.data.SupabaseClient.userAccessToken
        if (token.isNullOrBlank()) { toast("You are not signed in"); return }
        val request = Request.Builder().url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/user").header("apikey", BuildConfig.SUPABASE_ANON_KEY).header("Authorization", "Bearer $token").header("Content-Type", "application/json").patch(payload.toString().toRequestBody(jsonType)).build()
        http.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { runOnUiThread { toast("Update failed") } }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.use { runOnUiThread { toast(if (it.isSuccessful) "Updated successfully" else "Update rejected: HTTP ${it.code}") } } }
        })
    }

    private fun open(mode: String) = startActivity(intent(this, mode))
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius.toInt()).toFloat() }

    private fun showTopOptions() {
        com.kolipri.uzzap.ui.dialogs.J2mePopup.show(this, listOf(
            com.kolipri.uzzap.ui.dialogs.PopupItem("Settings"),
            com.kolipri.uzzap.ui.dialogs.PopupItem("Themes"),
            com.kolipri.uzzap.ui.dialogs.PopupItem("Notifications"),
            com.kolipri.uzzap.ui.dialogs.PopupItem("Back")
        )) { index ->
            when (index) {
                0 -> open(MODE_SETTINGS)
                1 -> startActivity(UzzapParityActivity.intent(this, UzzapParityActivity.MODE_THEMES))
                2 -> open(MODE_TONES)
                else -> finish()
            }
        }
    }

    companion object {
        private const val PREFS = "uzzap_prefs"
        private const val EXTRA_MODE = "mode"
        const val MODE_SETTINGS = "settings"
        const val MODE_STATUS = "status"
        const val MODE_PASSWORD = "password"
        const val MODE_MOBILE = "mobile"
        const val MODE_OFFLINE = "offline"
        const val MODE_TONES = "tones"
        const val MODE_BATTERY = "battery"
        const val MODE_EM = "em"
        const val MODE_IM = "im"
        const val MODE_PRIVACY = "privacy"
        const val MODE_SUBSCRIPTION = "subscription"
        fun intent(context: android.content.Context, mode: String) = android.content.Intent(context, LegacySettingsActivity::class.java).apply { putExtra(EXTRA_MODE, mode) }
    }
}
