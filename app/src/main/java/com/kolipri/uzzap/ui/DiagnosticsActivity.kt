package com.kolipri.uzzap.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
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

/** Moderator/admin-only developer diagnostics and function health monitor. */
class DiagnosticsActivity : BaseChromeActivity() {
    private lateinit var summaryView: TextView
    private lateinit var healthView: TextView
    private lateinit var logView: TextView
    private lateinit var filterView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var runButton: Button
    private lateinit var autoButton: Button
    private lateinit var overviewPanel: View
    private lateinit var functionsPanel: View
    private lateinit var logsPanel: View
    private val handler = Handler(Looper.getMainLooper())
    private var autoRefresh = false
    private var filter = "ALL"

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!autoRefresh || isFinishing) return
            render()
            handler.postDelayed(this, 3_000L)
        }
    }

    override fun contentLayoutRes(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppState.role = "user"
        ModeratorRoleClient.refresh { role ->
            if (role == "moderator" || role == "admin") showDiagnostics()
            else {
                Toast.makeText(this, "App Diagnostics is for moderators only.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        this.text = label
        textSize = 12f
        setAllCaps(false)
        minHeight = dp(44)
        setOnClickListener { action() }
    }

    private fun showDiagnostics() {
        setScreenTitle("App Diagnostics")
        showConnectedBall(SupabaseClient.isOnline)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(14))
        }
        root.addView(text("UZZAP • APP DIAGNOSTICS", 20f, true).apply {
            setPadding(dp(4), dp(4), dp(4), dp(2))
        })
        root.addView(text("Health • functions • performance • crash logs", 12f).apply {
            setTextColor(Color.LTGRAY); setPadding(dp(4), 0, dp(4), dp(8))
        })

        summaryView = text("Checking app state…", 13f, true).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(35, 42, 50))
        }
        root.addView(summaryView, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        runButton = button("Run checks") { runChecks() }
        actions.addView(runButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(3) })
        addButton(actions, "Refresh") { render() }
        autoButton = button("Auto: OFF") { toggleAutoRefresh() }
        actions.addView(autoButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(3) })
        root.addView(actions)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        addButton(tabs, "Overview") { showPanel("overview") }
        addButton(tabs, "Functions") { showPanel("functions") }
        addButton(tabs, "Logs") { showPanel("logs") }
        root.addView(tabs)

        overviewPanel = buildOverviewPanel()
        functionsPanel = buildFunctionsPanel()
        logsPanel = buildLogsPanel()
        root.addView(overviewPanel, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(functionsPanel, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(logsPanel, LinearLayout.LayoutParams(-1, 0, 1f))

        contentFrame.addView(root, ViewGroup.LayoutParams(-1, -1))
        AppDiagnostics.activity(this, "DiagnosticsActivity", "opened")
        showPanel("overview")
        render()
    }

    private fun buildOverviewPanel(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text("SYSTEM OVERVIEW", 12f, true).apply { setPadding(dp(4), dp(8), dp(4), dp(6)) })
        val state = text("Loading…", 14f).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(30, 36, 43))
        }
        box.addView(state, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        box.tag = state

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(tools, "Copy report") { copyLogs() }
        addButton(tools, "Clear logs") {
            AppDiagnostics.clear(this); render()
            Toast.makeText(this, "Diagnostic logs cleared", Toast.LENGTH_SHORT).show()
        }
        addButton(tools, "Clear crash") {
            CrashDiagnostics.clear(this); render()
            Toast.makeText(this, "Crash report cleared", Toast.LENGTH_SHORT).show()
        }
        box.addView(tools)
        return box
    }

    private fun buildFunctionsPanel(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text("FUNCTION HEALTH • LATEST RESULT", 12f, true).apply {
            setPadding(dp(4), dp(8), dp(4), dp(6))
        })
        healthView = text("", 12f).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), dp(12), dp(12), dp(16))
            setBackgroundColor(Color.rgb(24, 29, 35))
            setLineSpacing(0f, 1.08f)
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(healthView, ViewGroup.LayoutParams(-1, -2)) }
        box.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return box
    }

    private fun buildLogsPanel(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        filterView = text("EVENT LOG • ALL", 12f, true)
        row.addView(filterView, LinearLayout.LayoutParams(0, dp(42), 1f))
        addButton(row, "ALL") { setFilter("ALL") }
        addButton(row, "OK") { setFilter("OK") }
        addButton(row, "ERR") { setFilter("ERROR") }
        addButton(row, "RUN") { setFilter("STARTED") }
        box.addView(row)

        logView = text("", 11f).apply {
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.rgb(17, 21, 25))
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(24))
            setLineSpacing(0f, 1.12f)
        }
        logScroll = ScrollView(this).apply {
            isFillViewport = true; isVerticalScrollBarEnabled = true
            setBackgroundColor(Color.rgb(17, 21, 25))
            addView(logView, ViewGroup.LayoutParams(-1, -2))
        }
        box.addView(logScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return box
    }

    private fun showPanel(name: String) {
        if (!::overviewPanel.isInitialized) return
        overviewPanel.visibility = if (name == "overview") View.VISIBLE else View.GONE
        functionsPanel.visibility = if (name == "functions") View.VISIBLE else View.GONE
        logsPanel.visibility = if (name == "logs") View.VISIBLE else View.GONE
    }

    private fun addButton(row: LinearLayout, label: String, action: () -> Unit) {
        row.addView(button(label, action), LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            leftMargin = dp(2); rightMargin = dp(2)
        })
    }

    private fun setFilter(value: String) {
        filter = value; filterView.text = "LOG FILTER: $filter"; render()
    }

    private fun toggleAutoRefresh() {
        autoRefresh = !autoRefresh
        if (::autoButton.isInitialized) autoButton.text = if (autoRefresh) "Auto: ON" else "Auto: OFF"
        if (autoRefresh) handler.post(refreshRunnable) else handler.removeCallbacks(refreshRunnable)
        render()
    }

    private fun copyLogs() {
        val crash = CrashDiagnostics.read(this)
        val content = buildString {
            append("===== FUNCTION HEALTH =====\n")
            append(AppDiagnostics.healthSummary(this@DiagnosticsActivity))
            append("\n\n===== EVENT LOG =====\n")
            append(filteredEvents())
            if (crash != null) append("\n\n===== LAST CRASH REPORT =====\n").append(crash)
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Uzzap Diagnostics", content))
        Toast.makeText(this, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun filteredEvents(): String {
        val events = AppDiagnostics.read(this).ifBlank { "No diagnostic events yet." }
        if (filter == "ALL") return events
        return events.lineSequence().filter { line ->
            when (filter) {
                "OK" -> line.contains("|OK|")
                "ERROR" -> line.contains("|ERROR|") || line.contains("|FAILED|")
                "STARTED" -> line.contains("|STARTED|")
                else -> true
            }
        }.joinToString("\n").ifBlank { "No $filter events." }
    }

    private fun render() {
        if (!::summaryView.isInitialized || !::healthView.isInitialized || !::logView.isInitialized) return
        val crash = CrashDiagnostics.read(this)
        val authenticated = !SupabaseClient.userAccessToken.isNullOrBlank()
        summaryView.text = buildString {
            append(if (SupabaseClient.isOnline) "● ONLINE" else "● OFFLINE")
            append("   •   ")
            append(if (authenticated) "SESSION OK" else "SESSION MISSING")
            if (crash != null) append("   •   ⚠ CRASH")
        }
        val state = (overviewPanel as? LinearLayout)?.tag as? TextView
        state?.text = buildString {
            append("ACCESS\nModerator / Admin\n\n")
            append("SESSION\n").append(if (authenticated) "Authenticated" else "Not authenticated").append("\n\n")
            append("SERVER\n").append(if (SupabaseClient.isOnline) "Online" else "Offline / unknown").append("\n\n")
            append("USER\n").append(AppState.username.ifBlank { "<none>" }).append("\n\n")
            append("AUTO REFRESH\n").append(if (autoRefresh) "Every 3 seconds" else "Off")
            if (crash != null) append("\n\nCRASH REPORT\nAvailable — open Logs to inspect")
        }
        healthView.text = AppDiagnostics.healthSummary(this).ifBlank { "No function results yet.\nTap Run checks." }
        val events = filteredEvents()
        logView.text = if (crash == null) events else "$events\n\n===== LAST CRASH REPORT =====\n$crash"
        autoButton.text = if (autoRefresh) "Auto: ON" else "Auto: OFF"
        showConnectedBall(SupabaseClient.isOnline)
        logView.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun runChecks() {
        runButton.isEnabled = false
        runButton.text = "Running…"
        AppDiagnostics.started(this, "HealthCheck.run")
        summaryView.text = "RUNNING FUNCTION SMOKE TESTS…\nRead-only Supabase probes are running."
        SupabaseClient.checkHealth { online, tables, status ->
            if (online) AppDiagnostics.success(this, "HealthCheck.Supabase", status) else AppDiagnostics.failed(this, "HealthCheck.Supabase", status)
            if (tables) AppDiagnostics.success(this, "HealthCheck.Database", "room_messages endpoint reachable") else AppDiagnostics.failed(this, "HealthCheck.Database", "room_messages endpoint not confirmed")
            if (SupabaseClient.userAccessToken.isNullOrBlank()) AppDiagnostics.warn(this, "HealthCheck.Auth", "No active session") else AppDiagnostics.success(this, "HealthCheck.Auth", "Session token present")
            if (!online || !tables || SupabaseClient.userAccessToken.isNullOrBlank()) {
                AppDiagnostics.warn(this, "HealthCheck.run", "Smoke tests skipped because base health/session check failed")
                runOnUiThread { runButton.isEnabled = true; runButton.text = "Run checks"; render() }
                return@checkHealth
            }
            AppDiagnostics.started(this, "Diagnostics.smokeTests")
            SupabaseClient.runReadOnlyDiagnostics { probes ->
                probes.forEach { probe ->
                    if (probe.success) AppDiagnostics.success(this, probe.name, probe.detail + " • " + probe.elapsedMs + "ms")
                    else AppDiagnostics.failed(this, probe.name, probe.detail + " • " + probe.elapsedMs + "ms")
                }
                AppDiagnostics.success(this, "Diagnostics.smokeTests", "Completed " + probes.size + " read-only probes")
                AppDiagnostics.success(this, "HealthCheck.run", "Completed")
                runOnUiThread { runButton.isEnabled = true; runButton.text = "Run checks"; render() }
            }
        }
    }
    override fun onDestroy() {
        autoRefresh = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}