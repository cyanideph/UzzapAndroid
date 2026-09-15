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

/** Legacy Java/J2ME-style Settings screens. Java Uzzap is the visual and wording source of truth. */
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

    private fun settingsScreen() {
        rows("Settings", listOf("Edit My Profile", "Change Status", "Change Password", "Change Mobile Number", "Offline Settings", "Chatroom Tones"), listOf(
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
            singleLine = true
        }
        J2mePopup.show(this, listOf(PopupItem("Save"), PopupItem("Cancel"))) { index ->
            if (index == 0) {
                AppState.statusMessage = input.text.toString()
                AppState.syncProfileToSupabase()
                toast("Status message updated")
            }
        }
    }

    /** Mirrors co.java: old password, new password, verification; Update/Cancel. */
    private fun passwordScreen() {
        prepareScreen("* Change password", "Update", "Cancel", onLeft = {
            val old = currentForm?.findViewWithTag<EditText>("old")?.text?.toString().orEmpty()
            val new1 = currentForm?.findViewWithTag<EditText>("new1")?.text?.toString().orEmpty()
            val new2 = currentForm?.findViewWithTag<EditText>("new2")?.text?.toString().orEmpty()
            when {
                old.isBlank() || new1.isBlank() || new2.isBlank() -> legacyMessage("Please fill in all input fields.")
                new1.any { !it.isLetterOrDigit() } -> legacyMessage("Password must not contain special symbols")
                new1 != new2 -> legacyMessage("Passwords do not match.")
                new1.length < 6 -> legacyMessage("Password is too short")
                else -> changePassword(old, new1)
            }
        }, onRight = { finish() })
        val root = container()
        section(root, "Change password")
        addRow(root, editField("Old password", "old", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        addRow(root, editField("New password", "new1", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        addRow(root, editField("Re-type new password", "new2", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        currentForm = root
        showContent(scroll(root))
    }

    private fun changePassword(old: String, newPassword: String) {
        val token = SupabaseClient.userAccessToken
        if (token.isNullOrBlank()) { legacyMessage("You are not signed in."); return }
        // Supabase Auth does not accept an old password in the update-user endpoint.
        // Re-authentication is therefore performed first; only then is the password changed.
        val email = AppState.email
        if (email.isBlank()) { legacyMessage("No existing password (?)"); return }
        val body = JSONObject().put("email", email).put("password", old)
        val verify = Request.Builder().url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/token?grant_type=password")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY).header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType)).build()
        http.newCall(verify).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = runOnUiThread { legacyMessage("Failed to update your password. Please try again later.") }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) { runOnUiThread { legacyMessage("Existing password not correct.") }; return }
                    updateAuthUser(JSONObject().put("password", newPassword)) { success ->
                        runOnUiThread { legacyMessage(if (success) "Update complete. Your password has been changed." else "Failed to update your password. Please try again later.") }
                    }
                }
            }
        })
    }

    private fun mobileScreen() {
        prepareScreen("* Change mobile number", "Update", "Cancel", onLeft = {
            val phone = currentForm?.findViewWithTag<EditText>("mobile")?.text?.toString()?.trim().orEmpty()
            if (phone.length < 7) legacyMessage("Invalid format. Change cancelled.")
            else if (phone == SessionStore.phone()) legacyMessage("The number you entered is your current mobile number. No change was made.")
            else changeMobile(phone)
        })
        val root = container()
        section(root, "Change mobile number")
        addRow(root, editField("Enter new number", "mobile", InputType.TYPE_CLASS_PHONE))
        currentForm = root
        showContent(scroll(root))
    }

    private fun changeMobile(phone: String) {
        val token = SupabaseClient.userAccessToken
        if (token.isNullOrBlank()) { legacyMessage("Failed to update your mobile number. Please try again later."); return }
        updateAuthUser(JSONObject().put("phone", phone)) { success ->
            runOnUiThread { legacyMessage(if (success) "* Change mobile number\n\nPlease wait while updating your mobile number.." else "Failed to update your mobile number. Please try again later.") }
        }
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

    private fun offlineScreen() = rows("Offline Settings", listOf("Available SMS", "Email"), listOf(
        { togglePreference("offline_sms", "Available SMS") },
        { togglePreference("offline_email", "Email") }
    ), listOf("Receive messages via SMS when Uzzap is off.", "Forward messages to Email when Uzzap is off."))

    private fun tonesScreen() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val enabled = prefs.getBoolean("chatroom_alert", true)
        rows("* Chatroom Tones", listOf("Chatroom Tones"), listOf {
            prefs.edit().putBoolean("chatroom_alert", !enabled).apply()
            toast("Chatroom Tones: ${if (!enabled) "ON" else "OFF"}")
        }, listOf(if (enabled) "ON" else "OFF"))
    }

    private fun togglePreference(key: String, label: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val next = !prefs.getBoolean(key, false)
        prefs.edit().putBoolean(key, next).apply()
        toast("$label: ${if (next) "ON" else "OFF"}")
    }

    private fun updateAuthUser(payload: JSONObject, onResult: (Boolean) -> Unit = {}) {
        val token = SupabaseClient.userAccessToken
        if (token.isNullOrBlank()) { onResult(false); return }
        val request = Request.Builder().url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/user")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY).header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json").patch(payload.toString().toRequestBody(jsonType)).build()
        http.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = onResult(false)
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.use { onResult(it.isSuccessful) } }
        })
    }

    private fun legacyMessage(message: String) {
        J2mePopup.showMessage(this, "", message)
    }

    private fun open(mode: String) = startActivity(intent(this, mode))
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showTopOptions() {
        J2mePopup.show(this, listOf(PopupItem("Settings"), PopupItem("Themes"), PopupItem("Notifications"), PopupItem("Back"))) { index ->
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
        fun intent(context: android.content.Context, mode: String) = Intent(context, LegacySettingsActivity::class.java).apply { putExtra(EXTRA_MODE, mode) }
    }
}
