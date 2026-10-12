package com.kolipri.uzzap.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.util.Patterns
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.kolipri.uzzap.data.canonicalizeUsername
import com.kolipri.uzzap.data.NotificationPermission
import coil.load
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.CrashDiagnostics
import com.kolipri.uzzap.data.FcmClient
import com.kolipri.uzzap.data.UzzapNotificationManager
import com.kolipri.uzzap.data.NicknameResolver
import com.kolipri.uzzap.data.PasswordRecoveryClient
import com.kolipri.uzzap.data.SessionStore
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.data.signInWithEmail
import com.kolipri.uzzap.data.signUpWithEmail
import com.kolipri.uzzap.data.verifySession

class SplashMenuActivity : BaseChromeActivity() {
    override fun contentLayoutRes() = R.layout.content_splash_menu
    private var loading: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("")
        showConnectedBall(false)
        setSoftkeys(null, getString(R.string.softkey_exit), onRight = { finishAffinity() })
        requestNotificationPermissionAtStartup()
        loadSession()
        renderMenu()
        verifySession()
        showCrashReportIfPresent()
    }

    private fun requestNotificationPermissionAtStartup() {
        if (!NotificationPermission.shouldRequest(this)) return
        window.decorView.post {
            if (!isFinishing && !isDestroyed && NotificationPermission.shouldRequest(this)) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return
        val index = permissions.indexOf(Manifest.permission.POST_NOTIFICATIONS)
        if (index < 0) return
        val granted = grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
        // Read AFTER the result: false means "don't ask again" / double-denied.
        val canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.POST_NOTIFICATIONS
        )
        NotificationPermission.onResult(this, granted, canAskAgain)
    }

    private fun showCrashReportIfPresent() {
        val report = CrashDiagnostics.read(this) ?: return
        val text = TextView(this).apply { text = report; textSize = 12f; setTextIsSelectable(true); movementMethod = ScrollingMovementMethod(); setPadding(20, 10, 20, 10) }
        AlertDialog.Builder(this).setTitle("Previous Crash Captured").setMessage("Uzzap saved the last crash so it can be diagnosed without a computer. You can copy the report below and send it to me.").setView(text).setPositiveButton("Keep Report", null).setNeutralButton("Clear") { _, _ -> CrashDiagnostics.clear(this) }.setNegativeButton("Exit") { _, _ -> finishAffinity() }.show()
    }

    override fun onResume() { super.onResume(); loadSession(); renderMenu() }
    private fun loadSession() { val p = getSharedPreferences("uzzap_prefs", MODE_PRIVATE); val nick = SessionStore.nickname().ifBlank { p.getString("nickname", "cy") ?: "cy" }; AppState.nickname = nick; AppState.username = SessionStore.username().ifBlank { nick }; AppState.email = SessionStore.email().ifBlank { p.getString("email", "") ?: "" }; AppState.role = SessionStore.role().ifBlank { "user" }; var token = SessionStore.accessToken() ?: ""; if (token.isBlank() && p.getBoolean("session_active", false)) { val legacy = p.getString("auth_token", "") ?: ""; if (legacy.isNotBlank()) { token = legacy; p.edit().remove("auth_token").apply() } }; if (p.getBoolean("session_active", false) && token.isNotBlank()) { AppState.authToken = token; SupabaseClient.setAccessTokenForLegacy(token) } else if (p.getBoolean("session_active", false)) { p.edit().putBoolean("session_active", false).remove("auth_token").apply(); AppState.resetSession() } }
    private fun sessionActive(): Boolean = getSharedPreferences("uzzap_prefs", MODE_PRIVATE).getBoolean("session_active", false)
    private fun verifySession() { val p = getSharedPreferences("uzzap_prefs", MODE_PRIVATE); val token = SessionStore.accessToken() ?: return; if (!p.getBoolean("session_active", false) || token.isBlank()) return; SupabaseClient.verifySession(token) { valid, email, nick -> if (isFinishing || isDestroyed) return@verifySession; if (!valid) { p.edit().putBoolean("session_active", false).remove("auth_token").apply(); AppState.resetSession() } else { if (!email.isNullOrBlank()) AppState.email = email; if (!nick.isNullOrBlank()) { AppState.nickname = nick; AppState.username = nick }; p.edit().putString("email", AppState.email).putString("nickname", AppState.nickname).apply() }; renderMenu() } }
    private fun renderMenu() {
        val container = findViewById<LinearLayout>(R.id.menu_container) ?: return
        container.removeAllViews()
        if (sessionActive()) {
            val banner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 20); setBackgroundColor(ThemeManager.resolve(this@SplashMenuActivity, R.attr.uzzapColorSurface)) }
            banner.addView(TextView(this).apply { text = "● Logged in as: ${AppState.nickname}"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.resolve(this@SplashMenuActivity, R.attr.uzzapColorText)) })
            banner.addView(TextView(this).apply { text = AppState.email.ifBlank { "Authenticated Cloud Session" }; textSize = 12f; setTextColor(ThemeManager.resolve(this@SplashMenuActivity, R.attr.uzzapColorMuted)) })
            container.addView(banner)
        }
        val entries = mutableListOf<Triple<String, String, () -> Unit>>()
        if (sessionActive()) {
            entries += Triple("Connect as ${AppState.nickname}", "anim-icons/menu_connect.gif") { startActivity(Intent(this, ConnectingActivity::class.java)) }
            entries += Triple("Switch Account / Sign Out", "anim-icons/menu_switch.gif") { signOut() }
        }
        entries += Triple(getString(R.string.menu_login), "anim-icons/menu_login.gif") { onLogin() }
        entries += Triple(getString(R.string.menu_register), "anim-icons/menu_register.gif") { onRegister() }
        entries += Triple(getString(R.string.menu_forgot), "anim-icons/menu_forgot.gif") { onForgotPassword() }
        entries += Triple("Backend Info (Network)", "anim-icons/menu_backend.gif") { showBackendInfo() }
        entries += Triple(getString(R.string.menu_help), "anim-icons/menu_help.gif") { openHelp() }
        entries += Triple(getString(R.string.menu_about), "anim-icons/menu_about.gif") { openAbout() }
        entries += Triple(getString(R.string.menu_exit), "anim-icons/menu_exit.gif") { finishAffinity() }
        entries.forEach { (label, asset, action) ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_menu_row, container, false)
            row.findViewById<TextView>(R.id.tv_label).text = label
            row.findViewById<TextView>(R.id.tv_bullet).visibility = View.GONE
            row.findViewById<ImageView>(R.id.iv_icon).apply {
                visibility = View.VISIBLE
                layoutParams.width = dp(28)
                layoutParams.height = dp(28)
                loadAnimIcon(asset)
            }
            row.setOnClickListener { action() }
            container.addView(row)
        }
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
    private fun signOut() {
        UzzapDialogs.show(this, iconAsset = "anim-icons/menu_switch.gif", title = "Sign Out", message = "Are you sure you want to sign out of '${AppState.nickname}'?", positive = "Sign Out", onPositive = {
            getSharedPreferences("uzzap_prefs", MODE_PRIVATE).edit().putBoolean("session_active", false).remove("auth_token").apply()
            UzzapNotificationManager.clearAllNotifications(this)
            AppState.resetSession()
            renderMenu()
        })
    }
    private fun showLoading(message: String) { loading?.dismiss(); val text = ThemeManager.resolve(this, R.attr.uzzapColorText); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }; val tile = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = GradientDrawable().apply { setColor(0x2BAF0C0D); cornerRadius = dp(14).toFloat() } }; tile.addView(ImageView(this).apply { loadAnimIcon("anim-icons/dialog_default.gif") }, LinearLayout.LayoutParams(dp(30), dp(30))); row.addView(tile, LinearLayout.LayoutParams(dp(48), dp(48))); row.addView(TextView(this).apply { this.text = message; textSize = 14f; setTextColor(text); setPadding(dp(12), 0, 0, 0) }); val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(18), dp(26), dp(18)); background = GradientDrawable().apply { setColor(ThemeManager.resolve(this@SplashMenuActivity, R.attr.uzzapColorSurface)); cornerRadius = dp(24).toFloat() } }; card.addView(row); val root = FrameLayout(this).apply { setPadding(dp(36), dp(36), dp(36), dp(36)); addView(card, FrameLayout.LayoutParams(-1, -2)) }; loading = AlertDialog.Builder(this).setView(root).setCancelable(false).create(); loading?.setOnShowListener { loading?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }; loading?.show() }
    private fun hideLoading() { loading?.dismiss(); loading = null }
    private fun validEmail(value: String) = value.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(value).matches()
    private fun saveSession(email: String, nick: String, token: String) { AppState.email = email; AppState.nickname = nick; AppState.username = SessionStore.username().ifBlank { canonicalizeUsername(nick) }; AppState.authToken = token; SupabaseClient.setAccessTokenForLegacy(token); getSharedPreferences("uzzap_prefs", MODE_PRIVATE).edit().putBoolean("session_active", true).putString("email", email).putString("nickname", nick).apply() }
    private fun onLogin() {
        val fields = listOf(
            UzzapDialogs.FieldSpec("Email or Nickname", if (AppState.email.isNotBlank()) AppState.email else AppState.nickname),
            UzzapDialogs.FieldSpec("Password (required)", password = true)
        )
        UzzapDialogs.form(this, iconAsset = "anim-icons/menu_login.gif", title = getString(R.string.menu_login), message = "Log in to your Uzzap account:", fields = fields, positive = "Connect", onPositive = { f ->
            val id = f[0]; val password = f[1]
            val identifier = id.text.toString().trim(); val pass = password.text.toString()
            if (identifier.isBlank()) { id.error = "Email or nickname is required"; return@form false }
            if (pass.length < 6) { password.error = "Password must be at least 6 characters"; return@form false }
            showLoading("Authenticating...")
            fun finishLogin(email: String) { SupabaseClient.signInWithEmail(email, pass) { success, nick, token, error -> hideLoading(); if (success && !token.isNullOrBlank()) { saveSession(email, nick?.ifBlank { identifier } ?: identifier, token); FcmClient.initializeAndSync(this); Toast.makeText(this, "Welcome back, ${AppState.nickname}!", Toast.LENGTH_SHORT).show(); startActivity(Intent(this, ConnectingActivity::class.java)) } else UzzapDialogs.show(this, iconAsset = "anim-icons/menu_login.gif", title = "Login Failed", message = error ?: "Invalid nickname or password.", positive = "Try Again", onPositive = { onLogin() }, negative = "Forgot Password?", onNegative = { onForgotPassword() }) } }
            if (identifier.contains("@")) { if (!validEmail(identifier)) { hideLoading(); id.error = "Please enter a valid email address"; return@form false }; finishLogin(identifier) } else { NicknameResolver.resolve(identifier) { email, error -> runOnUiThread { if (email.isNullOrBlank()) { hideLoading(); UzzapDialogs.show(this, iconAsset = "anim-icons/menu_login.gif", title = "Login Failed", message = error ?: "Nickname not found", positive = "Try Again", onPositive = { onLogin() }) } else finishLogin(email) } } }
            true
        })
    }
    private fun onRegister() {
        val fields = listOf(
            UzzapDialogs.FieldSpec("Email", email = true),
            UzzapDialogs.FieldSpec("Chat Nickname (2-24 chars)"),
            UzzapDialogs.FieldSpec("Password (min 6 characters)", password = true),
            UzzapDialogs.FieldSpec("Confirm Password", password = true)
        )
        UzzapDialogs.form(this, iconAsset = "anim-icons/menu_register.gif", title = getString(R.string.menu_register), message = "Create your Uzzap cloud account:", fields = fields, positive = "Register", onPositive = { f ->
            val email = f[0]; val nick = f[1]; val pass = f[2]; val confirm = f[3]
            val e = email.text.toString().trim(); val n = nick.text.toString().trim(); val p = pass.text.toString(); val c = confirm.text.toString()
            if (!validEmail(e)) { email.error = "Valid email required"; return@form false }
            if (n.length !in 2..24) { nick.error = "Nickname must be 2-24 characters"; return@form false }
            if (p.length < 6) { pass.error = "Password must be at least 6 characters"; return@form false }
            if (p != c) { confirm.error = "Passwords do not match"; return@form false }
            showLoading("Creating account...")
            SupabaseClient.signUpWithEmail(e, p, n) { success, token, error -> runOnUiThread { hideLoading(); if (success && !token.isNullOrBlank()) { saveSession(e, n, token); FcmClient.initializeAndSync(this); startActivity(Intent(this, ConnectingActivity::class.java)) } else UzzapDialogs.show(this, iconAsset = "anim-icons/menu_register.gif", title = "Registration Failed", message = error ?: "Unable to create account", positive = "OK") } }
            true
        })
    }
    private fun onForgotPassword() {
        UzzapDialogs.form(this, iconAsset = "anim-icons/menu_forgot.gif", title = getString(R.string.menu_forgot), message = "Enter your registered email to receive a password reset link. The link will open Uzzap directly.", fields = listOf(UzzapDialogs.FieldSpec("Registered Email Address", AppState.email, email = true)), positive = "Send Reset Link", onPositive = { f ->
            val email = f[0].text.toString().trim()
            if (!validEmail(email)) { f[0].error = "Please enter a valid email address"; return@form false }
            showLoading("Sending reset link...")
            PasswordRecoveryClient.sendResetEmail(email) { success, error -> runOnUiThread { hideLoading(); UzzapDialogs.show(this, iconAsset = "anim-icons/menu_forgot.gif", title = if (success) "Reset Link Sent" else "Password Reset Failed", message = if (success) "Check your email and tap Reset Password. Uzzap will open automatically so you can choose a new password." else (error ?: "Password reset failed"), positive = "OK") } }
            true
        })
    }
    private fun showBackendInfo() {
        val status = SupabaseClient.connectionStatus
        val online = status.contains("Online", ignoreCase = true)
        UzzapDialogs.show(this, iconAsset = if (online) "anim-icons/backend_grid.gif" else "anim-icons/backend_nodes.gif",
            title = "Uzzap Network",
            message = "Servers: $status\n\nSession: ${if (sessionActive()) "Authenticated as ${AppState.nickname}" else "Signed out"}",
            positive = "OK")
    }
    private fun openHelp() = startActivity(SimpleTextActivity.intentForAsset(this, "Help", "help.txt"))
    // Single About surface: the splash menu used to host a second,
    // version-less credits dialog that drifted from AboutUzzapActivity.
    private fun openAbout() = startActivity(Intent(this, AboutUzzapActivity::class.java))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() { hideLoading(); super.onDestroy() }
    companion object { private const val NOTIFICATION_PERMISSION_REQUEST = 4107 }
}
