package com.kolipri.uzzap.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.content.Intent
import android.net.Uri
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.kolipri.uzzap.BuildConfig
import coil.load
import com.kolipri.uzzap.R
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

    private fun row(title: String, subtitle: String? = null, iconRes: Int = 0, animAsset: String? = null, onClick: (() -> Unit)? = null): LinearLayout {
        val rowView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundResource(R.drawable.bg_menu_row_selector)
            tag = "j2me_row"
            contentDescription = title
            isClickable = onClick != null
            if (onClick != null) setOnClickListener { onClick.invoke() }
        }
        if (animAsset != null) {
            rowView.addView(ImageView(this).apply { loadAnimIcon(animAsset) }, LinearLayout.LayoutParams(dp(32), dp(32)))
        } else if (iconRes != 0) {
            rowView.addView(ImageView(this).apply { setImageResource(iconRes) }, LinearLayout.LayoutParams(dp(28), dp(28)))
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        textCol.addView(TextView(this).apply {
            text = title
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.resolve(this@LegacySettingsActivity, R.attr.uzzapColorText))
        })
        if (!subtitle.isNullOrBlank()) {
            textCol.addView(TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(ThemeManager.resolve(this@LegacySettingsActivity, R.attr.uzzapColorMuted))
                setPadding(0, dp(2), 0, 0)
            })
        }
        rowView.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
        rowView.addView(ImageView(this).apply { loadAnimIcon("anim-icons/arrow_row_end_t.gif") }, LinearLayout.LayoutParams(dp(16), dp(16)))
        return rowView
    }

    private fun addRow(root: LinearLayout, view: View) {
        root.addView(view, LinearLayout.LayoutParams(-1, -2))
        if (view.tag == "j2me_row") root.addView(hairline(dp(14)))
    }

    private fun ImageView.loadAnimIcon(asset: String) {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets, asset))
                setImageDrawable(d)
                (d as? AnimatedImageDrawable)?.start()
                return
            } catch (e: Exception) { }
        }
        load("file:///android_asset/$asset")
    }

    private fun hairline(marginStart: Int): View {
        return View(this).apply {
            setBackgroundColor(ThemeManager.resolve(this@LegacySettingsActivity, R.attr.uzzapColorDivider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { this.marginStart = marginStart }
        }
    }

    private fun section(root: LinearLayout, label: String) {
        root.addView(TextView(this).apply {
            text = label
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.resolve(this@LegacySettingsActivity, R.attr.uzzapColorHeader))
            setPadding(dp(12), dp(12), dp(12), dp(8))
        })
        root.addView(hairline(0))
    }

    private fun rows(title: String, values: List<String>, handlers: List<() -> Unit>, subtitles: List<String?> = emptyList(), icons: List<Int> = emptyList(), animAssets: List<String?> = emptyList()) {
        prepareScreen(title)
        val root = container()
        section(root, title)
        values.forEachIndexed { index, label -> addRow(root, row(label, subtitles.getOrNull(index), icons.getOrNull(index) ?: 0, animAssets.getOrNull(index), handlers[index])) }
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
        }, subtitles, listOf(
            R.drawable.d_edit, R.drawable.d_d003_status_small, R.drawable.d_settings,
            R.drawable.d_n001_abm_small, R.drawable.d_outbox, R.drawable.d_n008_ringtone_small,
            R.drawable.d_n007_batteryinfo_small, R.drawable.d_d000_em_small, R.drawable.d_d001_im_small,
            R.drawable.d_padlock, R.drawable.d_n005_themes_small, R.drawable.d_billing,
            R.drawable.d_minus, 0
        ), listOf(
            "anim-icons/edit_profile.gif", "anim-icons/status_user.gif", "anim-icons/password_lock.gif",
            "anim-icons/im_profile.gif", "anim-icons/upload_arrow.gif", "anim-icons/tones_spectrum.gif",
            "anim-icons/battery_animated.gif", "anim-icons/em_chat.gif", "anim-icons/im_chat2.gif",
            "anim-icons/privacy_lock.gif", "anim-icons/theme_palette.gif", "anim-icons/subscription_card.gif", "anim-icons/delete_trash.gif", null
        ))
    }

    private fun confirmDeleteAccount() {
        UzzapDialogs.show(this, iconAsset = "anim-icons/delete_trash.gif", title = "Delete Account",
            message = "Are you sure you want to permanently delete your account?\n\nThis will remove your profile, buddy list, and message history. This action cannot be undone.",
            positive = "Delete Permanently",
            onPositive = { performAccountDeletion() })
    }

    private fun performAccountDeletion() {
        val textColor = ThemeManager.resolve(this, R.attr.uzzapColorText)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tile = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = GradientDrawable().apply { setColor(0x2B8EEF04); cornerRadius = dp(14).toFloat() } }
        tile.addView(ImageView(this).apply { loadAnimIcon("anim-icons/delete_trash.gif") }, LinearLayout.LayoutParams(dp(30), dp(30)))
        row.addView(tile, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(TextView(this).apply { text = "Please wait while your account is deleted..."; textSize = 14f; setTextColor(textColor); setPadding(dp(12), 0, 0, 0) })
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(18), dp(26), dp(18)); background = GradientDrawable().apply { setColor(ThemeManager.resolve(this@LegacySettingsActivity, R.attr.uzzapColorSurface)); cornerRadius = dp(24).toFloat() } }
        card.addView(row)
        val root = FrameLayout(this).apply { setPadding(dp(36), dp(36), dp(36), dp(36)); addView(card, FrameLayout.LayoutParams(-1, -2)) }
        val progress = AlertDialog.Builder(this).setView(root).setCancelable(false).create()
        progress.setOnShowListener { progress.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }
        progress.show()

        SupabaseClient.deleteAccount { success ->
            progress.dismiss()
            if (success) {
                Toast.makeText(this, "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                AppState.resetSession()
                SessionStore.clear()
                val intent = Intent(this, SplashMenuActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Account deletion failed. Your account was not deleted.", Toast.LENGTH_LONG).show()
            }
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
        UzzapDialogs.show(this, iconAsset = "anim-icons/edit_write.gif", title = "Status Message",
            inputHint = "Status message", prefilled = AppState.statusMessage, positive = "Save",
            onPositive = { value -> AppState.statusMessage = value; AppState.syncProfileToSupabase() })
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

    private fun instantMessagingScreen() = rows("Instant Messaging", listOf("Choose a buddy", "Back"), listOf(
        { startActivity(InstantMessagingActivity.intent(this)) },
        { finish() }
    ), listOf("Open the private messaging list", "Return to Settings"))

    private fun privacyScreen() {
        rows(
            "Privacy",
            listOf("Privacy Policy", "Request Account Deletion", "Allow buddy invitations", "Allow room invitations", "Show my online presence"),
            listOf(
                { openWebPage("https://hrjgblvxhtbopuyzyasu.supabase.co/functions/v1/uzzap-privacy") },
                { openWebPage("https://hrjgblvxhtbopuyzyasu.supabase.co/functions/v1/uzzap-privacy?delete=1") },
                { togglePrivacyPreference("privacy_allow_buddy_invitations", "Allow buddy invitations") },
                { togglePrivacyPreference("privacy_allow_room_invitations", "Allow room invitations") },
                { togglePrivacyPreference("privacy_show_presence", "Show my online presence") }
            ),
            listOf(
                "How Uzzap collects, uses and deletes data",
                "Delete your account even if the app is unavailable",
                "Allow other users to send buddy invitations",
                "Allow room invitations",
                "Control your online presence"
            )
        )
    }

    private fun togglePrivacyPreference(key: String, label: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val next = !prefs.getBoolean(key, true)
        prefs.edit().putBoolean(key, next).apply()
        toast("$label: ${if (next) "ON" else "OFF"}")
    }

    private fun openWebPage(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            toast("Unable to open web page")
        }
    }

    private fun subscriptionScreen() {
        prepareScreen("Subscription", null, "Back", onLeft = null, onRight = { finish() })
        val root = container(); section(root, "ACCOUNT")
        addRow(root, row("Uzzap Subscription", "Classic carrier subscription is not required for this modern cloud build."))
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
