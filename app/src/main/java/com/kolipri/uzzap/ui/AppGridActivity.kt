package com.kolipri.uzzap.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.ModeratorRoleClient
import com.kolipri.uzzap.ui.dialogs.J2mePopup
import com.kolipri.uzzap.ui.dialogs.PopupItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppGridActivity : BaseChromeActivity() {
    override fun contentLayoutRes() = R.layout.content_app_grid
    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("h:mm a", Locale.US)
    private val tickRunnable = object : Runnable { override fun run() { setScreenTitle(clockFormat.format(Date())); handler.postDelayed(this, 1000) } }

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
        findViewById<ImageView>(R.id.icon_ringtone).setOnClickListener { footer.text = "Silent Mode"; toggleSilentMode() }
        findViewById<ImageView>(R.id.icon_themes).setOnClickListener { footer.text = "Themes"; openParity(UzzapParityActivity.MODE_THEMES) }
        findViewById<ImageView>(R.id.icon_help).setOnClickListener { footer.text = "Help"; startActivity(SimpleTextActivity.intentForAsset(this, "Help", "help.txt")) }
        findViewById<ImageView>(R.id.icon_battery).setOnClickListener { footer.text = "Battery Saving"; startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_BATTERY)) }
        findViewById<ImageView>(R.id.icon_em).setOnClickListener { footer.text = "Extended Messaging"; startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM)) }
        findViewById<ImageView>(R.id.icon_im).setOnClickListener { footer.text = "Instant Messaging"; startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_IM)) }
        findViewById<ImageView>(R.id.icon_chat).setOnClickListener { footer.text = "Chat Rooms"; startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_CHAT)) }
        findViewById<ImageView>(R.id.icon_profile).setOnClickListener { footer.text = "Change Status"; startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_STATUS)) }
        ModeratorRoleClient.refresh { role -> if (role == "moderator" || role == "admin") { settingsIcon.setOnLongClickListener { startActivity(ModeratorActivity.intent(this)); true } } }
        if (!AppState.firstLoginShown) { AppState.firstLoginShown = true; handler.postDelayed({ openWelcome() }, 300) }
    }
    override fun onResume() { super.onResume(); handler.post(tickRunnable) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tickRunnable) }
    private fun openWelcome() = startActivity(Intent(this, WelcomeActivity::class.java))
    private fun openParity(mode: String, room: String? = null, buddy: String? = null) = startActivity(UzzapParityActivity.intent(this, mode, room, buddy))
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun toggleSilentMode() { val prefs = getSharedPreferences("uzzap_prefs", MODE_PRIVATE); val enabled = !prefs.getBoolean("silent_mode", false); prefs.edit().putBoolean("silent_mode", enabled).apply(); toast("Silent Mode: ${if (enabled) "ON" else "OFF"}") }
    private fun showOptionsMenu() {
        val items = mutableListOf(PopupItem("Lock Keypad"), PopupItem("Log Off"), PopupItem("Intro Help Screen"), PopupItem("About Uzzap"), PopupItem("Exit Application"))
        if (AppState.isModerator) items.add(0, PopupItem("Moderator Console"))
        J2mePopup.show(this, items) { index ->
            val adjusted = if (AppState.isModerator) { if (index == 0) { startActivity(ModeratorActivity.intent(this)); return@show }; index - 1 } else index
            when (adjusted) { 0 -> toast("Keypad lock is not required on Android"); 1 -> confirmLogout(); 2 -> openWelcome(); 3 -> showAbout(); 4 -> showExitMenu() }
        }
    }
    private fun showAbout() { J2mePopup.showMessage(this, "About Uzzap", "Uzzap BETA\nCreated by CY\nAndroid/Kotlin modernization of the legacy Uzzap client.") }
    private fun showExitMenu() { J2mePopup.show(this, listOf(PopupItem("Exit Application"), PopupItem("Cancel"))) { if (it == 0) finishAffinity() } }
    private fun confirmLogout() { J2mePopup.show(this, listOf(PopupItem("Log Off"), PopupItem("Cancel"))) { if (it == 0) { getSharedPreferences("uzzap_prefs", MODE_PRIVATE).edit().putBoolean("session_active", false).remove("auth_token").apply(); AppState.resetSession(); startActivity(Intent(this, SplashMenuActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }); finish() } } }
    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null) }
}
