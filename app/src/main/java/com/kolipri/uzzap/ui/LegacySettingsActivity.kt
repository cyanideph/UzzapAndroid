package com.kolipri.uzzap.ui

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

/** Legacy Java/J2ME-style Settings screens. Java Uzzap is the visual source of truth. */
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

    private fun prepareScreen(title: String, left: String? = "Options", right: String? = "Back", onLeft: (() -> Unit)? = { showTopOptions() }, onRight: (() -> Unit)? = { finish() }) {
        setScreenTitle(title)
        setSoftkeys(left, right, onLeft = onLeft, onRight = onRight)
        showConnectedBall(true)
        contentFrame.removeAllViews()
        currentForm = null
    }

    private fun container(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(2), dp(2), dp(2), dp(2))
        setBackgroundColor(activeTheme.background)
    }

    private fun scroll(root: View): ScrollView = ScrollView(this).apply {
        isFillViewport = true
        setBackgroundColor(activeTheme.background)
        addView(root, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showContent(view: View) {
        contentFrame.removeAllViews()
        contentFrame.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    private fun row(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null): TextView = TextView(this).apply {
        text = if (subtitle.isNullOrBlank()) title else "$title\n$subtitle"
        textSize = if (subtitle.isNullOrBlank()) 13f else 12f
        setTextColor(activeTheme.text)
        setPadding(dp(4), dp(2), dp(4), dp(if (subtitle.isNullOrBlank()) 2 else 3))
        background = LegacyThemeManager.cardBackground(activeTheme)
        minHeight = dp(if (subtitle.isNullOrBlank()) 24 else 30)
        gravity = Gravity.CENTER_VERTICAL
        contentDescription = title
        isClickable = onClick != null
        if (onClick != null) setOnClickListener { onClick.invoke() }
    }

    private fun addRow(root: LinearLayout, view: View) {
        root.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(1) })
    }

    private fun section(root: LinearLayout, label: String) {
        root.addView(TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(activeTheme.secondary)
            setPadding(dp(3), dp(3), dp(3), dp(2))
        }, LinearLayout.LayoutParams(-1, dp(20)))
    }

    private fun rows(title: String, values: List<String>, handlers: List<() -> Unit>, subtitles: List<String?> = emptyList()) {
        prepareScreen(title)
        val root = container()
        section(root, title)
        values.forEachIndexed { index, label -> addRow(root, row(label, subtitles.getOrNull(index), handlers[index])) }
        showContent(scroll(root))
    }

    /** Exact top-level Settings ordering confirmed from old Java aa.java. */
    private fun settingsScreen() {
        val labels = listOf(
            "Edit My Profile",
            "Change Status",
            "Change Password",
            "Change Mobile Number",
            "Offline Settings",
            "Chatroom Tones"
        )
        rows("Settings", labels, listOf(
            { startActivity(UzzapParityActivity.intent(this, UzzapParityActivity.MODE_PROFILE)) },
            { open(MODE_STATUS) },
            { open(MODE_PASSWORD) },
            { open(MODE_MOBILE) },
            { open(MODE_OFFLINE) },
            { open(MODE_TONES) }
        ))
    }

    private fun statusScreen() {
        rows("Change Status", listOf("Status Message..", "Available", "Not Available", "Invisible"), listOf(
            { statusMessageDialog() },
            { setPresence(Presence.AVAILABLE) },
            { setPresence(Presence.NOT_AVAILABLE) },
            { setPresence(Presence.INVISIBLE) }
        ))
    }

    private fun setPresence(presence: Presence) {
        AppState.presence = presence
        AppState.syncProfileToSupabase()
        PresenceClient.heartbeat(presence != Presence.INVISIBLE)
        toast("Status: ${presence.label}")
    }

    private fun statusMessageDialog() {
        val input = EditText(this).apply {
            setText(AppState.statusMessage)
            hint = "Status message"
            textSize = 13f
            setTextColor(activeTheme.text)
            setHintTextColor(activeTheme.secondary)
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        J2mePopup.show(this, listOf(PopupItem("Save"), PopupItem("Cancel"))) { index ->
            if (index == 0) {
                AppState.statusMessage = input.text.toString()
                AppState.syncProfileToSupabase()
            }
        }
    }

    private fun passwordScreen() {
        prepareScreen("Change Password", "Save", "Back", onLeft = {
            val a = currentForm?.findViewWithTag<EditText>("new")
            val b = currentForm?.findViewWithTag<EditText>("confirm")
            val pa = a?.text?.toString().orEmpty()
            val pb = b?.text?.toString().orEmpty()
            when {
                pa.length < 6 -> toast("Password must be at least 6 characters")
                pa != pb -> toast("Passwords do not match")
                else -> updateAuthUser(JSONObject().put("password", pa))
            }
        })
        val root = container()
        section(root, "Change Password")
        val newPassword = editField("New password", "new", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val confirm = editField("Confirm password", "confirm", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        addRow(root, newPassword)
        addRow(root, confirm)
        currentForm = root
        showContent(scroll(root))
    }

    private fun mobileScreen() {
        prepareScreen("Change Mobile Number", "Save", "Back", onLeft = {
            val input = currentForm?.findViewWithTag<EditText>("mobile")
            val phone = input?.text?.toString()?.trim().orEmpty()
            if (phone.length < 7) toast("Enter a valid mobile number")
            else updateAuthUser(JSONObject().put("phone", phone))
        })
        val root = container()
        section(root, "Change Mobile Number")
        addRow(root, editField("Mobile number", "mobile", InputType.TYPE_CLASS_PHONE))
        currentForm = root
        showContent(scroll(root))
    }

    private fun editField(hintText: String, fieldTag: String, type: Int): EditText = EditText(this).apply {
        hint = hintText
        tag = fieldTag
        inputType = type
        textSize = 13f
        setTextColor(activeTheme.text)
        setHintTextColor(activeTheme.secondary)
        setPadding(dp(4), 0, dp(4), 0)
        setBackgroundColor(activeTheme.editorBackground)
        minHeight = dp(28)
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
        })
    }

    private fun offlineScreen() = toggleScreen("Offline Settings", listOf("Receive messages while offline", "Store offline messages", "Show offline status"), "offline")
    private fun tonesScreen() = toggleScreen("Chatroom Tones", listOf("Chatroom message tone", "Private message tone", "Buddy online tone", "Room invitation tone", "Vibrate"), "tone")

    private fun batteryScreen() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val enabled = prefs.getBoolean("battery_saving", false)
        rows("Battery Saving", listOf("Background activity", "What it does"), listOf(
            {
                prefs.edit().putBoolean("battery_saving", !enabled).apply()
                toast("Battery Saving: ${if (!enabled) "ON" else "OFF"}")
            },
            { toast("Reduces background polling and presence checks.") }
        ), listOf(if (enabled) "ON" else "OFF", "Reduces background polling and presence checks."))
    }

    private fun extendedMessagingScreen() = rows("Extended Messaging", listOf("Buddy List", "Add Buddy", "Groups", "Unread Messages"), listOf(
        { startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM)) },
        { startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM)) },
        { toast("Groups are available from the Buddy List") },
        { startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM)) }
    ))

    private fun instantMessagingScreen() = rows("Instant Messaging", listOf("Choose a buddy", "Back"), listOf(
        { startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM)) },
        { finish() }
    ))

    private fun privacyScreen() = toggleScreen("Privacy", listOf("Allow buddy invitations", "Allow room invitations", "Show my online presence"), "privacy")

    private fun subscriptionScreen() = rows("Subscription", listOf("Uzzap Subscription", "Back"), listOf(
        { toast("Classic carrier subscription is not required for this Supabase build.") },
        { finish() }
    ))

    private fun confirmDeleteAccount() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to permanently delete your account?")
            .setPositiveButton("Delete") { _, _ -> performAccountDeletion() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performAccountDeletion() {
        val progress = AlertDialog.Builder(this)
            .setTitle("Deleting Account")
            .setMessage("Please wait...")
            .setCancelable(false)
            .show()
        SupabaseClient.deleteAccount { success ->
            progress.dismiss()
            Toast.makeText(this, if (success) "Account deleted successfully." else "Local account session cleared.", Toast.LENGTH_SHORT).show()
            AppState.resetSession()
            SessionStore.clear()
            startActivity(Intent(this, SplashMenuActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun updateAuthUser(payload: JSONObject) {
        val token = SupabaseClient.userAccessToken
        if (token.isNullOrBlank()) { toast("You are not signed in"); return }
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/user")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .patch(payload.toString().toRequestBody(jsonType))
            .build()
        http.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = runOnUiThread { toast("Update failed") }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { runOnUiThread { toast(if (it.isSuccessful) "Updated successfully" else "Update rejected: HTTP ${it.code}") } }
            }
        })
    }

    private fun open(mode: String) = startActivity(intent(this, mode))
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showTopOptions() {
        J2mePopup.show(this, listOf(
            PopupItem("Settings"),
            PopupItem("Themes"),
            PopupItem("Notifications"),
            PopupItem("Back")
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
        fun intent(context: android.content.Context, mode: String) = Intent(context, LegacySettingsActivity::class.java).apply { putExtra(EXTRA_MODE, mode) }
    }
}
