package com.kolipri.uzzap.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.SafetyClient
import com.kolipri.uzzap.model.ModerationAction
import com.kolipri.uzzap.model.UserReport
import com.kolipri.uzzap.ui.dialogs.J2mePopup
import com.kolipri.uzzap.ui.dialogs.PopupItem

class ModeratorActivity : BaseChromeActivity() {
    override fun contentLayoutRes(): Int? = null

    private var currentTab = TAB_REPORTS
    private var reportsList = listOf<UserReport>()
    private var sanctionsList = listOf<ModerationAction>()
    private lateinit var contentContainer: LinearLayout
    private lateinit var tabReportsButton: TextView
    private lateinit var tabSanctionsButton: TextView
    private lateinit var tabNewSanctionButton: TextView

    companion object {
        private const val TAB_REPORTS = 0
        private const val TAB_SANCTIONS = 1

        fun intent(context: Context): Intent = Intent(context, ModeratorActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppState.isModerator) {
            Toast.makeText(this, "Access denied: Moderator or Admin privileges required", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setScreenTitle("Moderator Console")
        showConnectedBall(true)
        setSoftkeys("Options", "Back", onLeft = { showOptionsMenu() }, onRight = { finish() })

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorDivider))
        }

        // Sub-header displaying current moderator name & role
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorPrimary))
        }
        header.addView(TextView(this).apply {
            text = "🛡️ ${AppState.nickname} (${AppState.role.uppercase()})"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        val reloadBtn = TextView(this).apply {
            text = "⟳ Refresh"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { refreshCurrentData() }
        }
        header.addView(reloadBtn)
        root.addView(header)

        // Tabs container
        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorSurface))
        }

        tabReportsButton = createTabButton("Reports Queue") { switchTab(TAB_REPORTS) }
        tabSanctionsButton = createTabButton("Active Sanctions") { switchTab(TAB_SANCTIONS) }
        tabNewSanctionButton = createTabButton("+ Sanction") { showApplySanctionDialog() }

        tabsRow.addView(tabReportsButton, LinearLayout.LayoutParams(0, dp(38), 1.2f).apply { marginEnd = dp(4) })
        tabsRow.addView(tabSanctionsButton, LinearLayout.LayoutParams(0, dp(38), 1.2f).apply { marginEnd = dp(4) })
        tabsRow.addView(tabNewSanctionButton, LinearLayout.LayoutParams(0, dp(38), 0.9f))
        root.addView(tabsRow)

        // Content area
        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(12))
        }
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(contentContainer, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))

        contentFrame.addView(root, FrameLayout.LayoutParams(-1, -1))

        switchTab(TAB_REPORTS)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun createTabButton(title: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = title
        gravity = Gravity.CENTER
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(4), dp(4), dp(4))
        setOnClickListener { onClick() }
    }

    private fun updateTabStyles() {
        val activeBg = GradientDrawable().apply {
            setColor(ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorPrimaryDark))
            cornerRadius = dp(6).toFloat()
        }
        val inactiveBg = GradientDrawable().apply {
            setColor(ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorDivider))
            cornerRadius = dp(6).toFloat()
        }

        if (currentTab == TAB_REPORTS) {
            tabReportsButton.background = activeBg
            tabReportsButton.setTextColor(Color.WHITE)
            tabSanctionsButton.background = inactiveBg
            tabSanctionsButton.setTextColor(ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorText))
        } else {
            tabSanctionsButton.background = activeBg
            tabSanctionsButton.setTextColor(Color.WHITE)
            tabReportsButton.background = inactiveBg
            tabReportsButton.setTextColor(ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorText))
        }
        val actionBg = GradientDrawable().apply {
            setColor(ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorPrimaryDark))
            cornerRadius = dp(6).toFloat()
        }
        tabNewSanctionButton.background = actionBg
        tabNewSanctionButton.setTextColor(Color.WHITE)
    }

    private fun switchTab(tab: Int) {
        currentTab = tab
        updateTabStyles()
        refreshCurrentData()
    }

    private fun refreshCurrentData() {
        contentContainer.removeAllViews()
        val loading = ProgressBar(this).apply {
            isIndeterminate = true
            setPadding(0, dp(32), 0, dp(32))
        }
        contentContainer.addView(loading)

        if (currentTab == TAB_REPORTS) {
            SafetyClient.fetchReports { reports ->
                reportsList = reports
                renderReportsView()
            }
        } else {
            SafetyClient.fetchActiveModerationActions { actions ->
                sanctionsList = actions
                renderSanctionsView()
            }
        }
    }

    private fun renderReportsView() {
        contentContainer.removeAllViews()
        tabReportsButton.text = "Reports (${reportsList.size})"

        if (reportsList.isEmpty()) {
            val empty = TextView(this).apply {
                text = "✓ No reports found in queue.\nChatrooms are peaceful!"
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorMuted))
                setPadding(dp(16), dp(48), dp(16), dp(48))
            }
            contentContainer.addView(empty)
            return
        }

        reportsList.forEach { report ->
            val card = createReportCard(report)
            contentContainer.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }
    }

    private fun createReportCard(report: UserReport): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorDivider))
            }
            isClickable = true
            isFocusable = true
        }

        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val target = TextView(this).apply {
            text = "User: ${report.reportedUsername}"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#B00020"))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val status = TextView(this).apply {
            text = report.status.uppercase()
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(2), dp(6), dp(2))
            val badgeColor = when (report.status.lowercase()) {
                "resolved" -> Color.parseColor("#198754")
                "dismissed" -> Color.parseColor("#6C757D")
                else -> Color.parseColor("#DC3545")
            }
            background = GradientDrawable().apply {
                setColor(badgeColor)
                cornerRadius = dp(4).toFloat()
            }
            setTextColor(Color.WHITE)
        }
        topRow.addView(target)
        topRow.addView(status)
        layout.addView(topRow)

        val reason = TextView(this).apply {
            text = "Reason: ${report.reason}"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorText))
            setPadding(0, dp(4), 0, 0)
        }
        layout.addView(reason)

        if (!report.details.isNullOrBlank()) {
            val details = TextView(this).apply {
                text = "\"${report.details}\""
                textSize = 12f
                setTextColor(Color.parseColor("#495057"))
                setPadding(0, dp(2), 0, 0)
            }
            layout.addView(details)
        }

        val footer = TextView(this).apply {
            text = "Date: ${report.createdAt.take(19).replace('T', ' ')}"
            textSize = 11f
            setTextColor(ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorMuted))
            setPadding(0, dp(4), 0, 0)
        }
        layout.addView(footer)

        layout.setOnClickListener { showReportOptionsDialog(report) }
        return layout
    }

    private fun showReportOptionsDialog(report: UserReport) {
        val options = arrayOf(
            "Mark as Resolved",
            "Dismiss Report",
            "Mute '${report.reportedUsername}'",
            "Warn '${report.reportedUsername}'",
            "Kick '${report.reportedUsername}'",
            "Ban '${report.reportedUsername}'"
        )
        AlertDialog.Builder(this)
            .setTitle("Report: ${report.reportedUsername}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> updateReport(report.id, "resolved")
                    1 -> updateReport(report.id, "dismissed")
                    2 -> promptDurationAndApply("mute", report.reportedUsername, report.reason)
                    3 -> promptReasonAndApply("warn", report.reportedUsername)
                    4 -> promptReasonAndApply("kick", report.reportedUsername)
                    5 -> promptDurationAndApply("ban", report.reportedUsername, report.reason)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateReport(reportId: Long, newStatus: String) {
        SafetyClient.updateReportStatus(reportId, newStatus) { success ->
            if (success) {
                Toast.makeText(this, "Report $newStatus", Toast.LENGTH_SHORT).show()
                refreshCurrentData()
            } else {
                Toast.makeText(this, "Failed to update report", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderSanctionsView() {
        contentContainer.removeAllViews()
        tabSanctionsButton.text = "Sanctions (${sanctionsList.size})"

        if (sanctionsList.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No active user sanctions found."
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(Color.parseColor("#5A6A60"))
                setPadding(dp(16), dp(48), dp(16), dp(48))
            }
            contentContainer.addView(empty)
            return
        }

        sanctionsList.forEach { sanction ->
            val card = createSanctionCard(sanction)
            contentContainer.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }
    }

    private fun createSanctionCard(action: ModerationAction): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#CFD8DC"))
            }
            isClickable = true
            isFocusable = true
        }

        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val target = TextView(this).apply {
            text = "${action.actionType.uppercase()}: ${action.targetUsername}"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            val actColor = when (action.actionType.lowercase()) {
                "ban" -> Color.parseColor("#721C24")
                "mute" -> Color.parseColor("#856404")
                "kick" -> Color.parseColor("#0C5460")
                else -> ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorMuted)
            }
            setTextColor(actColor)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val scope = TextView(this).apply {
            text = action.roomName ?: "GLOBAL"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                setColor(ThemeManager.resolve(this@ModeratorActivity, R.attr.uzzapColorDivider))
                cornerRadius = dp(4).toFloat()
            }
            setTextColor(Color.parseColor("#383D41"))
        }
        topRow.addView(target)
        topRow.addView(scope)
        layout.addView(topRow)

        val reason = TextView(this).apply {
            text = "Reason: ${action.reason}"
            textSize = 13f
            setTextColor(Color.parseColor("#212529"))
            setPadding(0, dp(4), 0, 0)
        }
        layout.addView(reason)

        val expiry = if (!action.expiresAt.isNullOrBlank()) {
            "Expires: ${action.expiresAt.take(19).replace('T', ' ')}"
        } else {
            "Expires: Permanent"
        }
        val footer = TextView(this).apply {
            text = expiry
            textSize = 11f
            setTextColor(Color.parseColor("#868E96"))
            setPadding(0, dp(4), 0, 0)
        }
        layout.addView(footer)

        layout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Revoke Sanction")
                .setMessage("Revoke ${action.actionType.uppercase()} for '${action.targetUsername}'?")
                .setPositiveButton("Revoke / Remove") { _, _ ->
                    SafetyClient.revokeModerationAction(action.id) { success ->
                        if (success) {
                            Toast.makeText(this, "Sanction revoked", Toast.LENGTH_SHORT).show()
                            refreshCurrentData()
                        } else {
                            Toast.makeText(this, "Failed to revoke sanction", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        return layout
    }

    private fun promptReasonAndApply(actionType: String, target: String) {
        val input = EditText(this).apply {
            hint = "Reason for $actionType"
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("$actionType User: $target")
            .setView(input)
            .setPositiveButton("Confirm") { _, _ ->
                val reason = input.text.toString().trim().ifBlank { "Moderator action: $actionType" }
                SafetyClient.applyModerationAction(target, actionType, null, reason, null) { success ->
                    if (success) {
                        Toast.makeText(this, "Applied $actionType to $target", Toast.LENGTH_SHORT).show()
                        refreshCurrentData()
                    } else {
                        Toast.makeText(this, "Failed to apply $actionType", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptDurationAndApply(actionType: String, target: String, defaultReason: String = "") {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val reasonInput = EditText(this).apply {
            hint = "Reason"
            if (defaultReason.isNotBlank()) setText(defaultReason)
        }
        view.addView(TextView(this).apply { text = "Reason:"; typeface = Typeface.DEFAULT_BOLD })
        view.addView(reasonInput)

        view.addView(TextView(this).apply {
            text = "Duration:"; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(10), 0, dp(4))
        })
        val durations = arrayOf("15 Minutes", "1 Hour", "24 Hours", "7 Days", "Permanent")
        val durationSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@ModeratorActivity, android.R.layout.simple_spinner_dropdown_item, durations)
        }
        view.addView(durationSpinner)

        AlertDialog.Builder(this)
            .setTitle("$actionType User: $target")
            .setView(view)
            .setPositiveButton("Apply") { _, _ ->
                val reason = reasonInput.text.toString().trim().ifBlank { "Moderator action: $actionType" }
                val minutes = when (durationSpinner.selectedItemPosition) {
                    0 -> 15
                    1 -> 60
                    2 -> 1440
                    3 -> 10080
                    else -> 0
                }
                SafetyClient.applyModerationAction(target, actionType, null, reason, if (minutes > 0) minutes else null) { success ->
                    if (success) {
                        Toast.makeText(this, "Applied $actionType to $target", Toast.LENGTH_SHORT).show()
                        refreshCurrentData()
                    } else {
                        Toast.makeText(this, "Failed to apply $actionType", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showApplySanctionDialog() {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        view.addView(TextView(this).apply { text = "Target Username:"; typeface = Typeface.DEFAULT_BOLD })
        val userEt = EditText(this).apply { hint = "e.g. bad_user" }
        view.addView(userEt)

        view.addView(TextView(this).apply { text = "Action Type:"; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(8), 0, dp(4)) })
        val actions = arrayOf("Mute", "Warn", "Kick", "Ban")
        val actionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@ModeratorActivity, android.R.layout.simple_spinner_dropdown_item, actions)
        }
        view.addView(actionSpinner)

        view.addView(TextView(this).apply { text = "Duration (for Mute/Ban):"; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(8), 0, dp(4)) })
        val durations = arrayOf("15 Minutes", "1 Hour", "24 Hours", "7 Days", "Permanent")
        val durationSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@ModeratorActivity, android.R.layout.simple_spinner_dropdown_item, durations)
        }
        view.addView(durationSpinner)

        view.addView(TextView(this).apply { text = "Room (leave blank for Global):"; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(8), 0, dp(4)) })
        val roomEt = EditText(this).apply { hint = "Optional province or chatroom" }
        view.addView(roomEt)

        view.addView(TextView(this).apply { text = "Reason:"; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(8), 0, dp(4)) })
        val reasonEt = EditText(this).apply { hint = "Reason for sanction" }
        view.addView(reasonEt)

        AlertDialog.Builder(this)
            .setTitle("Issue Manual Sanction")
            .setView(view)
            .setPositiveButton("Submit") { _, _ ->
                val target = userEt.text.toString().trim()
                if (target.isBlank()) {
                    Toast.makeText(this, "Target username cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val actType = actions[actionSpinner.selectedItemPosition].lowercase()
                val minutes = when (durationSpinner.selectedItemPosition) {
                    0 -> 15
                    1 -> 60
                    2 -> 1440
                    3 -> 10080
                    else -> 0
                }
                val room = roomEt.text.toString().trim().ifBlank { null }
                val reason = reasonEt.text.toString().trim().ifBlank { "Manual moderator sanction ($actType)" }

                SafetyClient.applyModerationAction(target, actType, room, reason, if (minutes > 0) minutes else null) { success ->
                    if (success) {
                        Toast.makeText(this, "Sanction issued to $target", Toast.LENGTH_SHORT).show()
                        if (currentTab != TAB_SANCTIONS) switchTab(TAB_SANCTIONS) else refreshCurrentData()
                    } else {
                        Toast.makeText(this, "Failed to issue sanction", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOptionsMenu() {
        val items = listOf(
            PopupItem("Refresh"),
            PopupItem("Reports Queue"),
            PopupItem("Active Sanctions"),
            PopupItem("Issue Sanction"),
            PopupItem("Back")
        )
        J2mePopup.show(this, items) { index ->
            when (index) {
                0 -> refreshCurrentData()
                1 -> switchTab(TAB_REPORTS)
                2 -> switchTab(TAB_SANCTIONS)
                3 -> showApplySanctionDialog()
                else -> finish()
            }
        }
    }
}
