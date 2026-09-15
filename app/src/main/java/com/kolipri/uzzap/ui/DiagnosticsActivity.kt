package com.kolipri.uzzap.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.kolipri.uzzap.data.AppDiagnostics
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.CrashDiagnostics
import com.kolipri.uzzap.data.ModeratorRoleClient
import com.kolipri.uzzap.data.SupabaseClient

/** Developer-facing local diagnostics page. Moderator/admin access only. */
class DiagnosticsActivity : BaseChromeActivity() {
    private lateinit var logView: TextView
    private lateinit var statusView: TextView

    override fun contentLayoutRes(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never trust a stale process-local role when opening a privileged screen.
        AppState.role = "user"
        ModeratorRoleClient.refresh { role ->
            if (role == "moderator" || role == "admin") {
                showDiagnostics()
            } else {
                Toast.makeText(this, "App Diagnostics is for moderators only.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showDiagnostics() {
        setScreenTitle("App Diagnostics")
        showConnectedBall(SupabaseClient.isOnline)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 12, 18, 18) }
        statusView = TextView(this).apply { setTypeface(typeface, Typeface.BOLD); textSize = 15f }
        root.addView(statusView, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { action() } }
        buttons.addView(button("Run checks") { runChecks() }, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(button("Refresh") { render() }, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(button("Clear") { AppDiagnostics.clear(this); render() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(button("Clear last crash") { CrashDiagnostics.clear(this); Toast.makeText(this, "Crash report cleared", Toast.LENGTH_SHORT).show(); render() })

        val scroll = ScrollView(this)
        logView = TextView(this).apply { textSize = 12f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(6, 12, 6, 24) }
        scroll.addView(logView, ViewGroup.LayoutParams(-1, -1))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        contentFrame.addView(root, ViewGroup.LayoutParams(-1, -1))
        AppDiagnostics.activity(this, "DiagnosticsActivity", "opened")
        render()
    }

    private fun render() {
        if (!::statusView.isInitialized || !::logView.isInitialized) return
        val crash = CrashDiagnostics.read(this)
        statusView.text = buildString {
            append("Access: MODERATOR/ADMIN")
            append("\nSession: ").append(if (SupabaseClient.userAccessToken.isNullOrBlank()) "NOT AUTHENTICATED" else "AUTHENTICATED")
            append("  •  Supabase: ").append(if (SupabaseClient.isOnline) "ONLINE" else "UNKNOWN/OFFLINE")
            append("\nUser: ").append(AppState.username.ifBlank { "<none>" })
            append("\n\n===== FUNCTION HEALTH =====\n")
            append(AppDiagnostics.healthSummary(this@DiagnosticsActivity).ifBlank { "No tracked functions yet." })
            if (crash != null) append("\n\n⚠️ LAST CRASH CAPTURED")
        }
        val events = AppDiagnostics.read(this).ifBlank { "No diagnostic events yet." }
        logView.text = if (crash == null) events else events + "\n\n===== LAST CRASH REPORT =====\n" + crash
    }

    private fun runChecks() {
        AppDiagnostics.started(this, "HealthCheck.run")
        statusView.text = "Running Supabase health check…"
        SupabaseClient.checkHealth { online, tables, status ->
            if (online) AppDiagnostics.success(this, "HealthCheck.Supabase", status) else AppDiagnostics.failed(this, "HealthCheck.Supabase", status)
            if (tables) AppDiagnostics.success(this, "HealthCheck.Database", "room_messages endpoint reachable")
            else AppDiagnostics.failed(this, "HealthCheck.Database", "room_messages endpoint not confirmed")
            if (SupabaseClient.userAccessToken.isNullOrBlank()) AppDiagnostics.warn(this, "HealthCheck.Auth", "No active session")
            else AppDiagnostics.success(this, "HealthCheck.Auth", "Session token present")
            AppDiagnostics.success(this, "HealthCheck.run", "Completed")
            runOnUiThread { showConnectedBall(online); render() }
        }
    }
}
