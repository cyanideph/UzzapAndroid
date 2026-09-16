package com.kolipri.uzzap.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.kolipri.uzzap.data.AppState

/** Primary profile entry point. Status remains available from profile/settings. */
class ProfileActivity : BaseChromeActivity() {
    override fun contentLayoutRes(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("Profile")
        showConnectedBall(true)
        setSoftkeys("Options", "Back", onLeft = { }, onRight = { finish() })
        render()
    }

    private fun render() {
        contentFrame.removeAllViews()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }
        root.addView(TextView(this).apply {
            text = "MY PROFILE"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(13, 12, 175))
        })
        root.addView(TextView(this).apply {
            text = AppState.username.ifBlank { "User" }
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(25, 25, 25))
            setPadding(0, dp(12), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Manage your account and online status"
            textSize = 14f
            setTextColor(Color.rgb(90, 90, 90))
            setPadding(0, 0, 0, dp(18))
        })
        addAction(root, "Account Settings", "Manage your Uzzap account") {
            startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_SETTINGS))
        }
        addAction(root, "Change Status", "Set your availability and status message") {
            startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_STATUS))
        }
        contentFrame.addView(root)
    }

    private fun addAction(parent: LinearLayout, title: String, subtitle: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.WHITE)
            isClickable = true
            setOnClickListener { action() }
            elevation = dp(2).toFloat()
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(25, 25, 25))
        })
        row.addView(TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(Color.rgb(95, 95, 95))
            setPadding(0, dp(3), 0, 0)
        })
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(9) })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
