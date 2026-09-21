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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.kolipri.uzzap.data.AppDiagnostics
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.CrashDiagnostics
import com.kolipri.uzzap.data.ModeratorRoleClient
import com.kolipri.uzzap.data.SupabaseClient
import java.util.Locale

/** Moderator/admin-only diagnostics console. */
class DiagnosticsActivity : BaseChromeActivity() {
    private lateinit var summaryView: TextView
    private lateinit var healthContainer: LinearLayout
    private lateinit var performanceView: TextView
    private lateinit var logView: TextView
    private lateinit var searchView: EditText
    private lateinit var contentScroll: ScrollView
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float = 12f, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun action(label: String, run: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        minHeight = dp(42)
        setPadding(dp(5), 0, dp(5), 0)
        setOnClickListener { run() }
    }

    private fun row(parent: LinearLayout, weight: Float, label: String, run: () -> Unit) {
        parent.addView(action(label, run), LinearLayout.LayoutParams(0, dp(44), weight).apply {
            leftMargin = dp(2); rightMargin = dp(2); bottomMargin = dp(2)
        })
    }

    private fun header(title: String, open: Boolean = true) = label(if (open) "▼  $title" else "▶  $title", 13f, true).apply {
        setBackgroundColor(Color.rgb(35, 42, 50))
        setPadding(dp(10), dp(10), dp(10), dp(10))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(9), dp(10), dp(9))
        setBackgroundColor(Color.rgb(28, 34, 40))
    }

    private fun showDiagnostics() {
        setScreenTitle("App Diagnostics")
        showConnectedBall(SupabaseClient.isOnline)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(12))
        }
        outer.addView(label("UZZAP • APP DIAGNOSTICS", 19f, true).apply { setPadding(dp(4), dp(4), dp(4), 0) })
        outer.addView(label("Live function health • network • session • performance • crash evidence", 11f).apply {
            setTextColor(Color.LTGRAY); setPadding(dp(4), 0, dp(4), dp(8))
        })

        contentScroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contentScroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        outer.addView(contentScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        summaryView = label("Loading…", 12f, true).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.rgb(35, 42, 50))
        }
        root.addView(summaryView, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })

        val main = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row(main, 1.3f, "Run checks") { runChecks() }
        row(main, 1f, "Refresh") { render() }
        row(main, 1f, if (autoRefresh) "Auto ON" else "Auto OFF") { toggleAutoRefresh() }
        root.addView(main)

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row(tools, 1f, "Copy report") { copyReport() }
        row(tools, 1f, "Clear logs") { AppDiagnostics.clear(this); render(); Toast.makeText(this, "Diagnostic logs cleared", Toast.LENGTH_SHORT).show() }
        row(tools, 1f, "Clear crash") { CrashDiagnostics.clear(this); render(); Toast.makeText(this, "Crash report cleared", Toast.LENGTH_SHORT).show() }
        root.addView(tools, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2); bottomMargin = dp(7) })

        val healthHeader = header("FUNCTION HEALTH")
        root.addView(healthHeader)
        healthContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(healthContainer)
        healthHeader.setOnClickListener { toggleSection(healthHeader, healthContainer, "FUNCTION HEALTH") }

        val perfHeader = header("PERFORMANCE", false)
        root.addView(perfHeader)
        performanceView = label("", 11f).apply {
            typeface = Typeface.MONOSPACE; setTextColor(Color.LTGRAY); setPadding(dp(10), dp(10), dp(10), dp(10)); setBackgroundColor(Color.rgb(28,34,40))
            visibility = View.GONE
        }
        root.addView(performanceView)
        perfHeader.setOnClickListener { toggleSection(perfHeader, performanceView, "PERFORMANCE") }

        val logHeader = header("EVENT LOG")
        root.addView(logHeader)
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        searchView = EditText(this).apply {
            hint = "Search functions or details"; textSize = 11f; singleLine = true; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setPadding(dp(8), 0, dp(8), 0)
            setBackgroundColor(Color.rgb(35,42,50))
            setOnEditorActionListener { _, _, _ -> render(); false }
        }
        filterRow.addView(searchView, LinearLayout.LayoutParams(0, dp(42), 1f))
        row(filterRow, .55f, "ALL") { setFilter("ALL") }
        row(filterRow, .55f, "OK") { setFilter("OK") }
        row(filterRow, .55f, "ERR") { setFilter("ERROR") }
        row(filterRow, .7f, "START") { setFilter("STARTED") }
        root.addView(filterRow)
        logView = label("", 10.5f).apply {
            typeface = Typeface.MONOSPACE; setBackgroundColor(Color.rgb(18,22,27)); setTextIsSelectable(true); setPadding(dp(10), dp(10), dp(10), dp(18)); setLineSpacing(0f,1.12f)
        }
        root.addView(logView)
        logHeader.setOnClickListener {
            val open = logView.visibility != View.VISIBLE
            logView.visibility = if (open) View.VISIBLE else View.GONE
            filterRow.visibility = if (open) View.VISIBLE else View.GONE
            logHeader.text = if (open) "▼  EVENT LOG" else "▶  EVENT LOG"
        }

        contentFrame.addView(outer, ViewGroup.LayoutParams(-1, -1))
        AppDiagnostics.activity(this, "DiagnosticsActivity", "opened")
        render()
    }

    private fun toggleSection(h: TextView, body: View, title: String) {
        val open = body.visibility != View.VISIBLE
        body.visibility = if (open) View.VISIBLE else View.GONE
        h.text = if (open) "▼  $title" else "▶  $title"
    }

    private fun setFilter(value: String) { filter = value; render() }

    private fun toggleAutoRefresh() {
        autoRefresh = !autoRefresh
        if (autoRefresh) handler.post(refreshRunnable) else handler.removeCallbacks(refreshRunnable)
        render()
    }

    private fun statusColor(status: AppDiagnostics.Status): Int = when (status) {
        AppDiagnostics.Status.PASS -> Color.rgb(60, 190, 105)
        AppDiagnostics.Status.FAIL -> Color.rgb(235, 80, 80)
        AppDiagnostics.Status.WARN -> Color.rgb(235, 175, 65)
        AppDiagnostics.Status.RUNNING -> Color.rgb(70, 155, 235)
        AppDiagnostics.Status.SKIPPED -> Color.rgb(160, 160, 160)
        AppDiagnostics.Status.IDLE -> Color.rgb(125, 135, 145)
    }

    private fun statusIcon(status: AppDiagnostics.Status) = when (status) {
        AppDiagnostics.Status.PASS -> "✓"
        AppDiagnostics.Status.FAIL -> "✕"
        AppDiagnostics.Status.WARN -> "!"
        AppDiagnostics.Status.RUNNING -> "•"
        AppDiagnostics.Status.SKIPPED -> "–"
        AppDiagnostics.Status.IDLE -> "○"
    }

    private fun renderHealth() {
        healthContainer.removeAllViews()
        val map = AppDiagnostics.health.value
        if (map.isEmpty()) {
            healthContainer.addView(label("No tracked functions yet.", 11f).apply { setPadding(dp(10), dp(10), dp(10), dp(10)) })
            return
        }
        val query = searchView.text.toString().trim().lowercase(Locale.US)
        val groups = map.values.filter { query.isBlank() || it.function.lowercase(Locale.US).contains(query) || it.details.lowercase(Locale.US).contains(query) }
            .groupBy { it.function.substringBefore('.') }
            .toSortedMap()
        groups.forEach { (group, functions) ->
            val title = label("$group  •  ${functions.size}", 12f, true).apply {
                setTextColor(Color.LTGRAY); setPadding(dp(10), dp(8), dp(10), dp(5))
            }
            healthContainer.addView(title)
            functions.sortedBy { it.function }.forEach { h ->
                val item = card()
                val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val badge = label("${statusIcon(h.status)} ${h.status.name}", 10f, true).apply { setTextColor(statusColor(h.status)); minWidth = dp(78) }
                top.addView(badge)
                top.addView(label(h.function, 11.5f, true), LinearLayout.LayoutParams(0, -2, 1f))
                h.durationMs?.let { top.addView(label("${it}ms", 10f).apply { setTextColor(Color.LTGRAY) }) }
                item.addView(top)
                item.addView(label(h.details.ifBlank { "No details" }, 10f).apply { setTextColor(Color.LTGRAY); setPadding(dp(78), dp(2), 0, 0) })
                healthContainer.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
            }
        }
    }

    private fun renderPerformance() {
        val values = AppDiagnostics.health.value.values.filter { it.durationMs != null }
        if (values.isEmpty()) {
            performanceView.text = "No timed operations recorded yet.\nRun checks or exercise app functions to collect timings."
            return
        }
        val fastest = values.minByOrNull { it.durationMs ?: Long.MAX_VALUE }
        val slowest = values.maxByOrNull { it.durationMs ?: 0L }
        val average = values.mapNotNull { it.durationMs }.average()
        performanceView.text = buildString {
            append("TIMED FUNCTIONS  •  ${values.size}\n")
            append("AVERAGE          •  ${"%.1f".format(Locale.US, average)}ms\n")
            append("FASTEST          •  ${fastest?.function ?: "-"}  ${fastest?.durationMs ?: 0}ms\n")
            append("SLOWEST          •  ${slowest?.function ?: "-"}  ${slowest?.durationMs ?: 0}ms\n")
            append("\nLatest measured response times are shown beside each function above.")
        }
    }

    private fun filteredEvents(): String {
        var lines = AppDiagnostics.read(this).ifBlank { "No diagnostic events yet." }.lineSequence()
        if (filter != "ALL") lines = lines.filter { line -> when (filter) {
            "OK" -> line.contains("|OK|")
            "ERROR" -> line.contains("|ERROR|") || line.contains("|FAILED|")
            "STARTED" -> line.contains("|STARTED|")
            else -> true
        } }
        val query = if (::searchView.isInitialized) searchView.text.toString().trim().lowercase(Locale.US) else ""
        if (query.isNotBlank()) lines = lines.filter { it.lowercase(Locale.US).contains(query) }
        return lines.joinToString("\n").ifBlank { "No matching events." }
    }

    private fun copyReport() {
        val crash = CrashDiagnostics.read(this)
        val content = buildString {
            append("===== UZZAP DIAGNOSTICS =====\n")
            append(AppDiagnostics.deviceSummary()).append("\n\n")
            append(AppDiagnostics.healthSummary(this@DiagnosticsActivity))
            append("\n\n===== EVENT LOG =====\n").append(filteredEvents())
            if (crash != null) append("\n\n===== LAST CRASH REPORT =====\n").append(crash)
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Uzzap Diagnostics", content))
        Toast.makeText(this, "Diagnostics report copied", Toast.LENGTH_SHORT).show()
    }

    private fun render() {
        if (!::summaryView.isInitialized) return
        val health = AppDiagnostics.health.value.values
        val pass = health.count { it.status == AppDiagnostics.Status.PASS }
        val fail = health.count { it.status == AppDiagnostics.Status.FAIL }
        val warn = health.count { it.status == AppDiagnostics.Status.WARN }
        val running = health.count { it.status == AppDiagnostics.Status.RUNNING }
        val idle = health.count { it.status == AppDiagnostics.Status.IDLE || it.status == AppDiagnostics.Status.SKIPPED }
        val crash = CrashDiagnostics.read(this)
        val authenticated = !SupabaseClient.userAccessToken.isNullOrBlank()
        summaryView.text = buildString {
            append("HEALTH  $pass PASS  •  $warn WARN  •  $fail FAIL  •  $idle NOT TESTED")
            if (running > 0) append("  •  $running RUNNING")
            append("\nSERVER  ${if (SupabaseClient.isOnline) "ONLINE" else "OFFLINE / UNKNOWN"}   •   SESSION  ${if (authenticated) "AUTHENTICATED" else "NOT AUTHENTICATED"}")
            append("\nUSER    ${AppState.username.ifBlank { "<none>" }}   •   AUTO  ${if (autoRefresh) "3s" else "OFF"}")
            if (crash != null) append("   •   CRASH REPORT AVAILABLE")
        }
        renderHealth()
        renderPerformance()
        logView.text = filteredEvents()
        showConnectedBall(SupabaseClient.isOnline)
    }

    private fun runChecks() {
        AppDiagnostics.started(this, "HealthCheck.run")
        summaryView.text = "RUNNING HEALTH CHECK…\nChecking network, database and authentication."
        SupabaseClient.checkHealth { online, tables, status ->
            if (online) AppDiagnostics.success(this, "HealthCheck.Supabase", status) else AppDiagnostics.failed(this, "HealthCheck.Supabase", status)
            if (tables) AppDiagnostics.success(this, "HealthCheck.Database", "room_messages endpoint reachable") else AppDiagnostics.failed(this, "HealthCheck.Database", "room_messages endpoint not confirmed")
            if (SupabaseClient.userAccessToken.isNullOrBlank()) AppDiagnostics.warn(this, "HealthCheck.Auth", "No active session") else AppDiagnostics.success(this, "HealthCheck.Auth", "Session token present")
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
