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
            setPadding(dp(12), dp(8), dp(12), dp(16))
        }
        root.addView(text("UZZAP • FUNCTION HEALTH", 20f, true).apply { setPadding(dp(4), dp(4), dp(4), dp(2)) })
        root.addView(text("Moderator diagnostic console • live local logs", 12f).apply {
            setTextColor(Color.LTGRAY); setPadding(dp(4), 0, dp(4), dp(10))
        })
        summaryView = text("Loading status…", 13f, true).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(Color.rgb(35, 42, 50))
        }
        root.addView(summaryView, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })

        val primary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        addButton(primary, "Run checks") { runChecks() }
        addButton(primary, "Refresh") { render() }
        addButton(primary, if (autoRefresh) "Auto: ON" else "Auto: OFF") { toggleAutoRefresh() }
        root.addView(primary)

        val secondary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        addButton(secondary, "Copy logs") { copyLogs() }
        addButton(secondary, "Clear logs") {
            AppDiagnostics.clear(this); render()
            Toast.makeText(this, "Diagnostic logs cleared", Toast.LENGTH_SHORT).show()
        }
        addButton(secondary, "Clear crash") {
            CrashDiagnostics.clear(this); render()
            Toast.makeText(this, "Crash report cleared", Toast.LENGTH_SHORT).show()
        }
        root.addView(secondary)

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(6))
        }
        filterView = text("LOG FILTER: ALL", 12f, true)
        filterRow.addView(filterView, LinearLayout.LayoutParams(0, dp(42), 1f))
        addButton(filterRow, "ALL") { setFilter("ALL") }
        addButton(filterRow, "OK") { setFilter("OK") }
        addButton(filterRow, "ERROR") { setFilter("ERROR") }
        addButton(filterRow, "START") { setFilter("STARTED") }
        root.addView(filterRow)

        root.addView(text("EVENT LOG • LIVE / SCROLLABLE", 12f, true).apply {
            setPadding(dp(4), dp(8), dp(4), dp(5))
        })
        logView = text("", 11f).apply {
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(18, 22, 27))
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(24))
            setLineSpacing(0f, 1.15f)
        }
        logScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            setBackgroundColor(Color.rgb(18, 22, 27))
            addView(logView, ViewGroup.LayoutParams(-1, -2))
        }
        root.addView(logScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(text("FUNCTION STATUS • LATEST RESULT", 12f, true).apply {
            setPadding(dp(4), dp(8), dp(4), dp(5))
        })
        healthView = text("", 13f).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(28, 34, 40))
            setLineSpacing(0f, 1.1f)
        }
        val healthScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(healthView, ViewGroup.LayoutParams(-1, -2))
        }
        root.addView(healthScroll, LinearLayout.LayoutParams(-1, dp(145)))

        contentFrame.addView(root, ViewGroup.LayoutParams(-1, -1))
        AppDiagnostics.activity(this, "DiagnosticsActivity", "opened")
        render()
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
        if (!::summaryView.isInitialized || !::healthView.isInitialized || !::logView.isInitialized || !::logScroll.isInitialized) return
        val crash = CrashDiagnostics.read(this)
        val authenticated = !SupabaseClient.userAccessToken.isNullOrBlank()
        summaryView.text = buildString {
            append("ACCESS  •  MODERATOR / ADMIN\n")
            append("SESSION •  ").append(if (authenticated) "AUTHENTICATED" else "NOT AUTHENTICATED")
            append("\nSERVER  •  ").append(if (SupabaseClient.isOnline) "ONLINE" else "OFFLINE / UNKNOWN")
            append("\nUSER    •  ").append(AppState.username.ifBlank { "<none>" })
            append("\nAUTO    •  ").append(if (autoRefresh) "REFRESHING EVERY 3s" else "OFF")
            if (crash != null) append("\nCRASH   •  ⚠ REPORT AVAILABLE")
        }
        healthView.text = AppDiagnostics.healthSummary(this).ifBlank { "No tracked functions yet." }
        val events = filteredEvents()
        logView.text = if (crash == null) events else "$events\n\n===== LAST CRASH REPORT =====\n$crash"
        logView.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        showConnectedBall(SupabaseClient.isOnline)
    }

    private fun runChecks() {
        AppDiagnostics.started(this, "HealthCheck.run")
        summaryView.text = "RUNNING HEALTH CHECK…\nChecking Supabase, database and authentication."
        SupabaseClient.checkHealth { online, tables, status ->
            if (online) AppDiagnostics.success(this, "HealthCheck.Supabase", status)
            else AppDiagnostics.failed(this, "HealthCheck.Supabase", status)
            if (tables) AppDiagnostics.success(this, "HealthCheck.Database", "room_messages endpoint reachable")
            else AppDiagnostics.failed(this, "HealthCheck.Database", "room_messages endpoint not confirmed")
            if (SupabaseClient.userAccessToken.isNullOrBlank()) AppDiagnostics.warn(this, "HealthCheck.Auth", "No active session")
            else AppDiagnostics.success(this, "HealthCheck.Auth", "Session token present")
            AppDiagnostics.success(this, "HealthCheck.run", "Completed")
            runOnUiThread { render() }
        }
    }

    override fun onDestroy() {
        autoRefresh = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}