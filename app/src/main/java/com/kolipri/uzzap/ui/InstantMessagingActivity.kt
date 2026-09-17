package com.kolipri.uzzap.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState

/**
 * Dedicated Instant Messaging entry point.
 *
 * EM owns the buddy/group management surface. IM owns the direct-message
 * entry point and opens an actual PrivateChatActivity only after a buddy is
 * selected. This prevents EM and IM from both navigating to the same buddy
 * list screen.
 */
class InstantMessagingActivity : BaseChromeActivity() {
    override fun contentLayoutRes(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("Instant Messaging")
        showConnectedBall(true)
        setSoftkeys("Options", "Back", onLeft = { finish() }, onRight = { finish() })
        renderBuddies()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) renderBuddies()
    }

    private fun renderBuddies() {
        contentFrame.removeAllViews()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val buddies = AppState.buddyGroups
            .flatMap { it.buddies }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(AppState.username, true) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

        // J2ME-style list header, e.g. "Regions (1/1)"
        root.addView(TextView(this).apply {
            text = "Private Messages (${buddies.size})"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.resolve(this@InstantMessagingActivity, R.attr.uzzapColorHeader))
            setPadding(dp(12), dp(12), dp(12), dp(8))
        })
        root.addView(hairline(0))

        if (buddies.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No buddies available.\nAdd a buddy from Extended Messaging."
                textSize = 16f
                setTextColor(Color.rgb(80, 84, 90))
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(40), dp(20), dp(40))
            }, LinearLayout.LayoutParams(-1, -2))
        } else {
            buddies.forEachIndexed { index, buddy ->
                root.addView(buddyRow(buddy), LinearLayout.LayoutParams(-1, -2))
                if (index < buddies.size - 1) root.addView(hairline(dp(14)))
            }
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root, LinearLayout.LayoutParams(-1, -2))
        }
        contentFrame.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))
    }

    private fun buddyRow(buddy: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundResource(R.drawable.bg_menu_row_selector)
            contentDescription = "Message $buddy"
            isClickable = true
            setOnClickListener {
                startActivity(PrivateChatActivity.intent(this@InstantMessagingActivity, buddy))
            }
            addView(ImageView(this@InstantMessagingActivity).apply {
                setImageResource(R.drawable.d_buddylist_icon)
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(TextView(this@InstantMessagingActivity).apply {
                text = buddy
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ThemeManager.resolve(this@InstantMessagingActivity, R.attr.uzzapColorText))
                setPadding(dp(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ImageView(this@InstantMessagingActivity).apply {
                setImageResource(R.drawable.d_small_arrow)
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(14), dp(14)))
        }
    }

    private fun hairline(marginStart: Int): View {
        return View(this).apply {
            setBackgroundColor(ThemeManager.resolve(this@InstantMessagingActivity, R.attr.uzzapColorDivider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply {
                this.marginStart = marginStart
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    companion object {
        fun intent(context: android.content.Context) =
            android.content.Intent(context, InstantMessagingActivity::class.java)
    }
}
