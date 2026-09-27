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
        findViewById<ImageView>(R.id.icon_addbuddy).setOnClickListener {
            footer.text = "Add or Invite Buddies"
            J2mePopup.show(this, listOf(
                PopupItem("Invite Friends to Uzzap"),
                PopupItem("Add Buddy by Mobile"),
                PopupItem("Add Buddy by User ID"),
                PopupItem("Add Other Contact")
            )) { _ ->
                startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM))
            }
        }
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
        // The legacy fourth destination is Change Status; profile remains available from the modern profile flow.
        findViewById<ImageView>(R.id.icon_profile).setOnClickListener { footer.text = "Change Status"; startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_STATUS)) }
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
        // Exact legacy Main Menu Options from com.kolipri.kalypte.an:
        // Lock Keypad, Log Off, Intro Help Screen, About Uzzap, Exit Application.
        val items = listOf(
            PopupItem("Lock Keypad"),
            PopupItem("Log Off"),
            PopupItem("Intro Help Screen"),
            PopupItem("About Uzzap"),
            PopupItem("Exit Application")
        )
        J2mePopup.show(this, items) { index ->
            when (index) {
                0 -> showLockKeypad()
                1 -> confirmLogout()
                2 -> openWelcome()
                3 -> showAbout()
                4 -> showExitMenu()
            }
        }
    }

    private fun showLockKeypad() { UzzapDialogs.show(this, iconAsset = "anim-icons/privacy_lock.gif", title = "Lock Keypad", message = "Keypad lock is active while this dialog is open.", positive = "Unlock") }
    private fun showAbout() { startActivity(Intent(this, AboutUzzapActivity::class.java)) }
    private fun showExitMenu() { AlertDialog.Builder(this).setTitle("Exit Application").setMessage("Log off and close Uzzap?").setPositiveButton("Exit") { _, _ -> finishAffinity() }.setNegativeButton("Cancel", null).show() }
    private fun confirmLogout() { UzzapDialogs.show(this, iconAsset = "anim-icons/menu_switch.gif", title = "Log Off", message = getString(R.string.logout_confirm), positive = "Log Off", onPositive = { getSharedPreferences("uzzap_prefs", MODE_PRIVATE).edit().putBoolean("session_active", false).remove("auth_token").apply(); AppState.resetSession(); startActivity(Intent(this, SplashMenuActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }); finish() }) }
    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null) }
}
