package com.kolipri.uzzap.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.ModeratorRoleClient
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.ui.dialogs.J2mePopup
import com.kolipri.uzzap.ui.dialogs.PopupItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppGridActivity : BaseChromeActivity() {
    override fun contentLayoutRes() = R.layout.content_app_grid
    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("h:mm a", Locale.US)
    private val tickRunnable = object : Runnable { override fun run() { updateTitle(); handler.postDelayed(this, 1000) } }
    private val onlineRunnable = object : Runnable { override fun run() { refreshOnlineCount(); handler.postDelayed(this, 30_000L) } }
    private var onlineCount = -1
    private fun updateTitle() { val online = if (onlineCount >= 0) "  ·  ● $onlineCount online" else ""; setScreenTitle(clockFormat.format(Date()) + online) }
    private fun refreshOnlineCount() = SupabaseClient.fetchOnlineCount { count -> if (count >= 0 && count != onlineCount) { onlineCount = count; updateTitle() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showConnectedBall(true)
        setSoftkeys(getString(R.string.softkey_options), getString(R.string.softkey_exit), onLeft = { showOptionsMenu() }, onRight = { showExitMenu() })
        val footer = findViewById<TextView>(R.id.tv_footer_feature_name)
        val settingsIcon = findViewById<ImageView>(R.id.icon_settings)
        findViewById<ImageView>(R.id.icon_u).setOnClickListener { footer.text = "Subscription"; startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_SUBSCRIPTION)) }
        findViewById<ImageView>(R.id.icon_matching).setOnClickListener { footer.text = "Buddy Matching"; toast("Automatic Buddy Matching is not supported on this device.") }
        findViewById<ImageView>(R.id.icon_addbuddy).setOnClickListener { footer.text = "Add / Invite Buddies"; startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM)) }
        settingsIcon.setOnClickListener { footer.text = "Settings"; startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_SETTINGS)) }
        settingsIcon.setOnLongClickListener { if (AppState.role == "admin") { footer.text = "Moderator Management"; startActivity(ModeratorManagementActivity.intent(this)); true } else if (AppState.isModerator) { footer.text = "Moderator Console"; startActivity(ModeratorActivity.intent(this)); true } else false }
        findViewById<ImageView>(R.id.icon_ringtone).setOnClickListener { footer.text = "Silent Mode"; toggleSilentMode() }
        findViewById<ImageView>(R.id.icon_themes).setOnClickListener { footer.text = "Themes"; openParity(UzzapParityActivity.MODE_THEMES) }
        findViewById<ImageView>(R.id.icon_help).setOnClickListener { footer.text = "Help"; startActivity(SimpleTextActivity.intentForAsset(this, "Help", "help.txt")) }
        findViewById<ImageView>(R.id.icon_battery).setOnClickListener { footer.text = "Battery Saving"; startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_BATTERY)) }
        // EM is the communication/request center; IM is dedicated private Buddy Chat.
        findViewById<ImageView>(R.id.icon_em).setOnClickListener { footer.text = "Extended Messaging"; startActivity(Intent(this, CommunicationCenterActivity::class.java)) }
        findViewById<ImageView>(R.id.icon_im).setOnClickListener { footer.text = "Buddy Chat"; startActivity(InstantMessagingActivity.intent(this)) }
        findViewById<ImageView>(R.id.icon_im).setOnLongClickListener { footer.text = "Message Receipts"; startActivity(Intent(this, DeliveryReadReceiptsActivity::class.java)); true }
        findViewById<ImageView>(R.id.icon_chat).setOnClickListener { footer.text = "Chat Rooms"; startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_CHAT)) }
        // Profile is the fourth primary destination; status remains inside Profile/Settings.
        findViewById<ImageView>(R.id.icon_profile).setOnClickListener { footer.text = "Profile"; startActivity(Intent(this, ProfileActivity::class.java)) }
        AppState.role = "user"
        ModeratorRoleClient.refresh { role -> if (role == "moderator" || role == "admin") footer.text = if (role == "admin") "Moderator Management" else "Moderator Console" }
        if (!AppState.firstLoginShown) { AppState.firstLoginShown = true; handler.postDelayed({ openWelcome() }, 300) }
    }

    override fun onResume() { super.onResume(); handler.post(tickRunnable); refreshOnlineCount(); handler.post(onlineRunnable) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tickRunnable); handler.removeCallbacks(onlineRunnable) }
    private fun openWelcome() = startActivity(Intent(this, WelcomeActivity::class.java))
    private fun openParity(mode: String, room: String? = null, buddy: String? = null) = startActivity(UzzapParityActivity.intent(this, mode, room, buddy))
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun toggleSilentMode() { val prefs = getSharedPreferences("uzzap_prefs", MODE_PRIVATE); val enabled = !prefs.getBoolean("silent_mode", false); prefs.edit().putBoolean("silent_mode", enabled).apply(); toast("Silent Mode: ${if (enabled) "ON" else "OFF"}") }
    private fun showOptionsMenu() {
        val isMod = AppState.isModerator
        val isAdmin = AppState.role == "admin"
        val items = mutableListOf<PopupItem>()
        if (isMod) { items.add(PopupItem("🛡️ Moderator Console")); items.add(PopupItem("📋 Moderation Audit Log")) }
        if (isAdmin) items.add(PopupItem("👑 Moderator Management"))
        if (isMod) items.add(PopupItem("🧪 App Diagnostics"))
        items.addAll(listOf(PopupItem("✉️ Room Invitations"), PopupItem("Lock Keypad"), PopupItem("Log Off"), PopupItem("Intro Help Screen"), PopupItem("About Uzzap"), PopupItem("Exit Application")))
        J2mePopup.show(this, items) { index ->
            var i = index
            if (isMod) {
                if (i == 0) { startActivity(ModeratorActivity.intent(this)); return@show }
                if (i == 1) { startActivity(ModerationAuditActivity.intent(this)); return@show }
                i -= 2
                if (i == 0) { startActivity(Intent(this, DiagnosticsActivity::class.java)); return@show }
                i--
            }
            if (isAdmin) { if (i == 0) { startActivity(ModeratorManagementActivity.intent(this)); return@show }; i-- }
            when (i) { 0 -> startActivity(Intent(this, RoomInvitationsActivity::class.java)); 1 -> showLockKeypad(); 2 -> confirmLogout(); 3 -> openWelcome(); 4 -> showAbout(); 5 -> showExitMenu() }
        }
    }
    private fun showLockKeypad() { AlertDialog.Builder(this).setTitle("Lock Keypad").setMessage("Keypad lock is active while this dialog is open.").setPositiveButton("Unlock", null).show() }
    private fun showAbout() { startActivity(Intent(this, AboutUzzapActivity::class.java)) }
    private fun showExitMenu() { AlertDialog.Builder(this).setTitle("Exit Application").setMessage("Log off and close Uzzap?").setPositiveButton("Exit") { _, _ -> finishAffinity() }.setNegativeButton("Cancel", null).show() }
    private fun confirmLogout() { AlertDialog.Builder(this).setTitle("Log Off").setMessage(getString(R.string.logout_confirm)).setPositiveButton(getString(R.string.softkey_yes)) { _, _ -> getSharedPreferences("uzzap_prefs", MODE_PRIVATE).edit().putBoolean("session_active", false).remove("auth_token").apply(); AppState.resetSession(); startActivity(Intent(this, SplashMenuActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }); finish() }.setNegativeButton(getString(R.string.softkey_no), null).show() }
    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null) }
}
