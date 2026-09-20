package com.kolipri.uzzap.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import com.kolipri.uzzap.R
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
            setPadding(dp(12), dp(12), dp(12), dp(18))
            setBackgroundColor(ThemeManager.resolve(this@ProfileActivity, R.attr.uzzapColorBg))
        }

        val items = listOf(
            EmItem(R.drawable.ic_uzzap_settings, "Account Settings", "Manage your Uzzap account") {
                startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_SETTINGS))
            },
            EmItem(R.drawable.ic_uzzap_profile, "Change Status", "Set your availability and status message") {
                startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_STATUS))
            }
        )

        // J2ME-style list header, e.g. "Regions (1/1)"
        root.addView(TextView(this).apply {
            text = "My Profile (${items.size})"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.resolve(this@ProfileActivity, R.attr.uzzapColorHeader))
            setPadding(dp(12), dp(12), dp(12), dp(8))
        })
        root.addView(divider(0))
        root.addView(TextView(this).apply {
            text = "Logged in as " + AppState.username.ifBlank { "User" }
            textSize = 13f
            setTextColor(ThemeManager.resolve(this@ProfileActivity, R.attr.uzzapColorMuted))
            setPadding(dp(12), dp(10), dp(12), 0)
        })
        items.forEachIndexed { index, item -> addEmRow(root, item, showDivider = index < items.size - 1) }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        contentFrame.addView(scroll)
    }

    private data class EmItem(val iconRes: Int, val title: String, val subtitle: String, val action: () -> Unit)

    private fun addEmRow(parent: LinearLayout, item: EmItem, showDivider: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundResource(R.drawable.bg_menu_row_selector)
            isClickable = true
            setOnClickListener { item.action() }
        }
        row.addView(ImageView(this).apply {
            setImageResource(item.iconRes)
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(28), dp(28)))
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        textCol.addView(TextView(this).apply {
            text = item.title
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.resolve(this@ProfileActivity, R.attr.uzzapColorText))
        })
        textCol.addView(TextView(this).apply {
            text = item.subtitle
            textSize = 12f
            setTextColor(ThemeManager.resolve(this@ProfileActivity, R.attr.uzzapColorMuted))
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.d_small_arrow)
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(14), dp(14)))
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
        if (showDivider) parent.addView(divider(dp(14)))
    }

    private fun divider(marginStart: Int): View {
        return View(this).apply {
            setBackgroundColor(ThemeManager.resolve(this@ProfileActivity, R.attr.uzzapColorDivider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply {
                this.marginStart = marginStart
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
