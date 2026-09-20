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
    private lateinit var contentScroll: ScrollView
    private lateinit var healthContainer: View
    private lateinit var logContainer: View
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
        minHeight = dp(42)
        setPadding(dp(4), 0, dp(4), 0)
        setOnClickListener { action() }
    }

    private fun sectionHeader(title: String, initiallyOpen: Boolean = true): TextView =
        text(if (initiallyOpen) "▼  $title" else "▶  $title", 13f, true).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(35, 42, 50))
            setPadding(dp(10), dp(9), dp(10), dp(9))
        }

    private fun addButton(row: LinearLayout, label: String, weight: Float = 1f, action: () -> Unit) {
        row.addView(button(label, action), LinearLayout.LayoutParams(0, dp(44), weight).apply {
            leftMargin = dp(2)
            rightMargin = dp(2)
            bottomMargin = dp(2)
        })
    }

    private fun showDiagnostics() {
        setScreenTitle("App Diagnostics")
        showConnectedBall(SupabaseClient.isOnline)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(12))
        }

        outer.addView(text("UZZAP • DIAGNOSTICS", 19f, true).apply {
            setPadding(dp(4), dp(3), dp(4), dp(1))
        })
        outer.addView(text("Moderator console • health, performance and crash troubleshooting", 11f).apply {
            setTextColor(Color.LTGRAY)
            setPadding(dp(4), 0, dp(4), dp(7))
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
        }
        contentScroll = scroll

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        summaryView = text("Loading status…", 12f, true).apply {
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setBackgroundColor(Color.rgb(35, 42, 50))
        }
        root.addView(summaryView, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6)
        })

        val mainActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addButton(mainActions, "Run checks", 1.2f) { runChecks() }
        addButton(mainActions, "Refresh") { render() }
        addButton(mainActions, if (autoRefresh) "Auto ON" else "Auto OFF") { toggleAutoRefresh() }
        root.addView(mainActions)

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addButton(tools, "Copy report") { copyLogs() }
        addButton(tools, "Clear logs") {
            AppDiagnostics.clear(this)
            render()
            Toast.makeText(this, "Diagnostic logs cleared", Toast.LENGTH_SHORT).show()
        }
        addButton(tools, "Clear crash") {
            CrashDiagnostics.clear(this)
            render()
            Toast.makeText(this, "Crash report cleared", Toast.LENGTH_SHORT).show()
        }
        root.addView(tools, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2)
            bottomMargin = dp(6)
        })

        val healthHeader = sectionHeader("FUNCTION HEALTH", true)
        root.addView(healthHeader)
        healthView = text("", 11f).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(28, 34, 40))
            setLineSpacing(0f, 1.08f)
            setTextIsSelectable(true)
        }
        healthContainer = healthView
        root.addView(healthView)
        healthHeader.setOnClickListener {
            val open = healthContainer.visibility != View.VISIBLE
            healthContainer.visibility = if (open) View.VISIBLE else View.GONE
            healthHeader.text = if (open) "▼  FUNCTION HEALTH" else "▶  FUNCTION HEALTH"
        }

        val performanceHeader = sectionHeader("PERFORMANCE", false)
        root.addView(performanceHeader)
        val performanceInfo = text(
            "Performance tests will appear here: screen startup, network latency, database/message loading, realtime and UzzapBot response time.",
            11f
        ).apply {
            setTextColor(Color.LTGRAY)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(28, 34, 40))
        }
        root.addView(performanceInfo)
        performanceInfo.visibility = View.GONE
        performanceHeader.setOnClickListener {
            val open = performanceInfo.visibility != View.VISIBLE
            performanceInfo.visibility = if (open) View.VISIBLE else View.GONE
            performanceHeader.text = if (open) "▼  PERFORMANCE" else "▶  PERFORMANCE"
        }

        val logHeader = sectionHeader("EVENT LOG", true)
        root.addView(logHeader)

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        filterView = text("FILTER: ALL", 11f, true).apply {
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        filterRow.addView(filterView, LinearLayout.LayoutParams(0, dp(40), 1f))
        addButton(filterRow, "ALL", 0.7f) { setFilter("ALL") }
        addButton(filterRow, "OK", 0.7f) { setFilter("OK") }
        addButton(filterRow, "ERR", 0.7f) { setFilter("ERROR") }
        addButton(filterRow, "START", 0.9f) { setFilter("STARTED") }
        root.addView(filterRow)

        logView = text("", 10.5f).apply {
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(18, 22, 27))
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(18))
            setLineSpacing(0f, 1.12f)
        }
        logContainer = logView
        root.addView(logView)
        logHeader.setOnClickListener {
            val open = logContainer.visibility != View.VISIBLE
            logContainer.visibility = if (open) View.VISIBLE else View.GONE
            filterRow.visibility = if (open) View.VISIBLE else View.GONE
            logHeader.text = if (open) "▼  EVENT LOG" else "▶  EVENT LOG"
        }

        contentFrame.addView(outer, ViewGroup.LayoutParams(-1, -1))
        AppDiagnostics.activity(this, "DiagnosticsActivity", "opened")
        render()
    }

    private fun setFilter(value: String) {
        filter = value
        filterView.text = "FILTER: $filter"
        render()
    }

    private fun toggleAutoRefresh() {
        autoRefresh = !autoRefresh
        if (autoRefresh) handler.post(refreshRunnable) else handler.removeCallbacks(refreshRunnable)
        render()
    }

    private fun copyLogs() {
        val crash = CrashDiagnostics.read(this)
        val content = buildString {
            append("===== UZZAP DIAGNOSTICS =====\n")
            append(AppDiagnostics.healthSummary(this@DiagnosticsActivity))
            append("\n\n===== EVENT LOG =====\n")
            append(filteredEvents())
            if (crash != null) append("\n\n===== LAST CRASH REPORT =====\n").append(crash)
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Uzzap Diagnostics", content))
        Toast.makeText(this, "Diagnostics report copied", Toast.LENGTH_SHORT).show()
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
            append("ACCESS  •  MODERATOR / ADMIN\n")
            append("SESSION •  ").append(if (authenticated) "AUTHENTICATED" else "NOT AUTHENTICATED")
            append("\nSERVER  •  ").append(if (SupabaseClient.isOnline) "ONLINE" else "OFFLINE / UNKNOWN")
            append("\nUSER    •  ").append(AppState.username.ifBlank { "<none>" })
            append("\nAUTO    •  ").append(if (autoRefresh) "REFRESH 3s" else "OFF")
            if (crash != null) append("\nCRASH   •  ⚠ REPORT AVAILABLE")
        }

        healthView.text = AppDiagnostics.healthSummary(this).ifBlank { "No tracked functions yet." }
        val events = filteredEvents()
        logView.text = if (crash == null) events else "$events\n\n===== LAST CRASH REPORT =====\n$crash"
        showConnectedBall(SupabaseClient.isOnline)
    }

    private fun runChecks() {
        AppDiagnostics.started(this, "HealthCheck.run")
        summaryView.text = "RUNNING HEALTH CHECK…\nChecking network, database and authentication."

        SupabaseClient.checkHealth { online, tables, status ->
            if (online) AppDiagnostics.success(this, "HealthCheck.Supabase", status)
            else AppDiagnostics.failed(this, "HealthCheck.Supabase", status)
            if (tables) AppDiagnostics.success(this, "HealthCheck.Database", "room_messages endpoint reachable")
            else AppDiagnostics.failed(this, "HealthCheck.Database", "room_messages endpoint not confirmed")
            if (SupabaseClient.userAccessToken.isNullOrBlank()) {
                AppDiagnostics.warn(this, "HealthCheck.Auth", "No active session")
            } else {
                AppDiagnostics.success(this, "HealthCheck.Auth", "Session token present")
            }
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
