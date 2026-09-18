package com.kolipri.uzzap.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import coil.load
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.CrashDiagnostics
import com.kolipri.uzzap.data.NicknameResolver
import com.kolipri.uzzap.data.PasswordRecoveryClient
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.data.signInWithEmail
import com.kolipri.uzzap.data.signOut
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NOTIFICATION_PERMISSION_REQUEST
                    )
                }
            }
        }
    }

    private fun showCrashReportIfPresent() {
        val report = CrashDiagnostics.read(this) ?: return
        val text = TextView(this).apply { text = report; textSize = 12f; setTextIsSelectable(true); movementMethod = ScrollingMovementMethod(); setPadding(20, 10, 20, 10) }
        AlertDialog.Builder(this).setTitle("Previous Crash Captured").setMessage("Uzzap saved the last crash so it can be diagnosed without a computer. You can copy the report below and send it to me.").setView(text).setPositiveButton("Keep Report", null).setNeutralButton("Clear") { _, _ -> CrashDiagnostics.clear(this) }.setNegativeButton("Exit") { _, _ -> finishAffinity() }.show()
    }

    override fun onResume() { super.onResume(); loadSession(); renderMenu() }
    private fun loadSession() { val p = getSharedPreferences("uzzap_prefs", MODE_PRIVATE); val nick = p.getString("nickname", "cy") ?: "cy"; AppState.nickname = nick; AppState.username = nick; AppState.email = p.getString("email", "") ?: ""; val token = p.getString("auth_token", "") ?: ""; if (p.getBoolean("session_active", false) && token.isNotBlank()) { AppState.authToken = token; SupabaseClient.setAccessTokenForLegacy(token) } }
    private fun sessionActive(): Boolean = getSharedPreferences("uzzap_prefs", MODE_PRIVATE).getBoolean("session_active", false)
    private fun verifySession() { val p = getSharedPreferences("uzzap_prefs", MODE_PRIVATE); val token = p.getString("auth_token", "") ?: return; if (!p.getBoolean("session_active", false) || token.isBlank()) return; SupabaseClient.verifySession(token) { valid, email, nick -> if (isFinishing || isDestroyed) return@verifySession; if (!valid) { p.edit().putBoolean("session_active", false).remove("auth_token").apply(); AppState.resetSession() } else { if (!email.isNullOrBlank()) AppState.email = email; if (!nick.isNullOrBlank()) { AppState.nickname = nick; AppState.username = nick }; p.edit().putString("email", AppState.email).putString("nickname", AppState.nickname).apply() }; renderMenu() } }
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
    private fun signOut() { AlertDialog.Builder(this).setTitle("Sign Out").setMessage("Are you sure you want to sign out of '${AppState.nickname}'?").setPositiveButton("Sign Out") { _, _ -> getSharedPreferences("uzzap_prefs", MODE_PRIVATE).edit().putBoolean("session_active", false).remove("auth_token").apply(); SupabaseClient.signOut(); AppState.resetSession(); renderMenu() }.setNegativeButton(getString(R.string.softkey_cancel), null).show() }
    private fun showLoading(message: String) { loading?.dismiss(); val layout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(50, 40, 50, 40) }; layout.addView(ProgressBar(this)); layout.addView(TextView(this).apply { text = "  $message"; textSize = 14f }); loading = AlertDialog.Builder(this).setView(layout).setCancelable(false).create(); loading?.show() }
    private fun hideLoading() { loading?.dismiss(); loading = null }
    private fun validEmail(value: String) = value.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(value).matches()
    private fun saveSession(email: String, nick: String, token: String) { AppState.email = email; AppState.nickname = nick; AppState.username = nick.lowercase().replace(" ", "_"); AppState.authToken = token; SupabaseClient.setAccessTokenForLegacy(token); getSharedPreferences("uzzap_prefs", MODE_PRIVATE).edit().putBoolean("session_active", true).putString("email", email).putString("nickname", nick).putString("auth_token", token).apply() }
    private fun onLogin() { val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 10) }; val id = EditText(this).apply { hint = "Email or Nickname"; inputType = InputType.TYPE_CLASS_TEXT; setText(if (AppState.email.isNotBlank()) AppState.email else AppState.nickname) }; val password = EditText(this).apply { hint = "Password (required)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }; box.addView(id); box.addView(password); val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.menu_login)).setMessage("Log in to your Uzzap account:").setView(box).setPositiveButton("Connect", null).setNegativeButton(getString(R.string.softkey_cancel), null).create(); dialog.show(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val identifier = id.text.toString().trim(); val pass = password.text.toString(); if (identifier.isBlank()) { id.error = "Email or nickname is required"; return@setOnClickListener }; if (pass.length < 6) { password.error = "Password must be at least 6 characters"; return@setOnClickListener }; dialog.dismiss(); showLoading("Authenticating..."); fun finishLogin(email: String) { SupabaseClient.signInWithEmail(email, pass) { success, nick, token, error -> hideLoading(); if (success && !token.isNullOrBlank()) { saveSession(email, nick?.ifBlank { identifier } ?: identifier, token); Toast.makeText(this, "Welcome back, ${AppState.nickname}!", Toast.LENGTH_SHORT).show(); startActivity(Intent(this, ConnectingActivity::class.java)) } else AlertDialog.Builder(this).setTitle("Login Failed").setMessage(error ?: "Invalid nickname or password.").setPositiveButton("Try Again") { _, _ -> onLogin() }.setNeutralButton("Forgot Password?") { _, _ -> onForgotPassword() }.setNegativeButton(getString(R.string.softkey_cancel), null).show() } }; if (identifier.contains("@")) { if (!validEmail(identifier)) { hideLoading(); id.error = "Please enter a valid email address"; id.requestFocus(); return@setOnClickListener }; finishLogin(identifier) } else { NicknameResolver.resolve(identifier) { email, error -> runOnUiThread { if (email.isNullOrBlank()) { hideLoading(); AlertDialog.Builder(this).setTitle("Login Failed").setMessage(error ?: "Nickname not found").setPositiveButton("Try Again") { _, _ -> onLogin() }.setNegativeButton(getString(R.string.softkey_cancel), null).show() } else finishLogin(email) } } } } }
    private fun onRegister() { val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 10) }; val email = EditText(this).apply { hint = "Email"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }; val nick = EditText(this).apply { hint = "Chat Nickname (2-24 chars)" }; val pass = EditText(this).apply { hint = "Password (min 6 characters)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }; val confirm = EditText(this).apply { hint = "Confirm Password"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }; box.addView(email); box.addView(nick); box.addView(pass); box.addView(confirm); val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.menu_register)).setMessage("Create your Uzzap cloud account:").setView(box).setPositiveButton("Register", null).setNegativeButton(getString(R.string.softkey_cancel), null).create(); dialog.show(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val e = email.text.toString().trim(); val n = nick.text.toString().trim(); val p = pass.text.toString(); val c = confirm.text.toString(); if (!validEmail(e)) { email.error = "Valid email required"; return@setOnClickListener }; if (n.length !in 2..24) { nick.error = "Nickname must be 2-24 characters"; return@setOnClickListener }; if (p.length < 6) { pass.error = "Password must be at least 6 characters"; return@setOnClickListener }; if (p != c) { confirm.error = "Passwords do not match"; return@setOnClickListener }; dialog.dismiss(); showLoading("Creating account..."); SupabaseClient.signUpWithEmail(e, p, n) { success, token, error -> runOnUiThread { hideLoading(); if (success && !token.isNullOrBlank()) { saveSession(e, n, token); startActivity(Intent(this, ConnectingActivity::class.java)) } else AlertDialog.Builder(this).setTitle("Registration Failed").setMessage(error ?: "Unable to create account").setPositiveButton("OK", null).show() } } } }
    private fun onForgotPassword() { val input = EditText(this).apply { hint = "Registered Email Address"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS; setText(AppState.email) }; AlertDialog.Builder(this).setTitle(getString(R.string.menu_forgot)).setMessage("Enter your registered email to receive a password reset link. The link will open Uzzap directly.").setView(input).setPositiveButton("Send Reset Link") { _, _ -> val email = input.text.toString().trim(); if (!validEmail(email)) { Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show(); return@setPositiveButton }; showLoading("Sending reset link..."); PasswordRecoveryClient.sendResetEmail(email) { success, error -> runOnUiThread { hideLoading(); AlertDialog.Builder(this).setTitle(if (success) "Reset Link Sent" else "Password Reset Failed").setMessage(if (success) "Check your email and tap Reset Password. Uzzap will open automatically so you can choose a new password." else (error ?: "Password reset failed")).setPositiveButton("OK", null).show() } } }.setNegativeButton(getString(R.string.softkey_cancel), null).show() }
    private fun showBackendInfo() {
        val status = SupabaseClient.connectionStatus
        val online = status.contains("Online", ignoreCase = true)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        root.addView(ImageView(this).apply {
            loadAnimIcon(if (online) "anim-icons/backend_grid.gif" else "anim-icons/backend_nodes.gif")
            contentDescription = "Network status"
        }, LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(TextView(this).apply {
            text = "Servers: $status\n\nSession: ${if (sessionActive()) "Authenticated as ${AppState.nickname}" else "Signed out"}"
            textSize = 15f
            setTextColor(ThemeManager.resolve(this@SplashMenuActivity, R.attr.uzzapColorText))
            setPadding(0, dp(12), 0, 0)
        })
        AlertDialog.Builder(this).setTitle("Uzzap Network").setView(root).setPositiveButton("OK", null).show()
    }
    private fun openHelp() = startActivity(SimpleTextActivity.intentForAsset(this, "Help", "help.txt"))
    private fun openAbout() {
        val red = resources.getColor(R.color.uzzap_special_red, theme)
        val text = resources.getColor(R.color.uzzap_text, theme)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(10))
        }

        ImageView(this).apply {
            setImageResource(R.drawable.d_logo_large)
            contentDescription = getString(R.string.app_name)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }.also { root.addView(it) }

        addAboutHeading(root, "UZZAP", red)
        addAboutText(root, "Modern Recreation by CY", text, 16, true)
        addAboutText(root, "A modern Android/Kotlin recreation of the classic Nokia/J2ME Uzzap mobile chat experience.", text, 15, false)
        addAboutText(root, "Developed by CY using Android/Kotlin and a modern cloud backend.", text, 15, false)
        addAboutText(root, "This is an independent, unofficial recreation. The original Nokia/J2ME Uzzap application, its decompiled source, historical assets and trademarks are separate materials and are not claimed as original CY work. See NOTICE.md for attribution and licensing details.", text, 15, false)
        addAboutText(root, "Animated icons courtesy of Flaticon, Lordicon and IconScout.", text, 13, false)
        addAboutText(root, "2026 CY", text, 14, true)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        AlertDialog.Builder(this)
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun addAboutHeading(parent: LinearLayout, value: String, color: Int) {
        parent.addView(TextView(this).apply {
            text = value
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, dp(10))
        })
    }

    private fun addAboutText(parent: LinearLayout, value: String, color: Int, size: Int, bold: Boolean) {
        parent.addView(TextView(this).apply {
            text = value
            textSize = size.toFloat()
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setTextColor(color)
            setPadding(0, dp(3), 0, dp(9))
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() { hideLoading(); super.onDestroy() }
    companion object { private const val NOTIFICATION_PERMISSION_REQUEST = 4107 }
}
