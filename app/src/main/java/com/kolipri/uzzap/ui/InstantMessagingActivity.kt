package com.kolipri.uzzap.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
            setPadding(dp(12), dp(10), dp(12), dp(18))
        }

        root.addView(TextView(this).apply {
            text = "PRIVATE MESSAGES"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(13, 12, 175))
            setPadding(dp(4), dp(8), dp(4), dp(8))
        })

        val buddies = AppState.buddyGroups
            .flatMap { it.buddies }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(AppState.username, true) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

        if (buddies.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No buddies available.\nAdd a buddy from Extended Messaging."
                textSize = 16f
                setTextColor(Color.rgb(80, 84, 90))
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(40), dp(20), dp(40))
            }, LinearLayout.LayoutParams(-1, -2))
        } else {
            buddies.forEach { buddy ->
                root.addView(TextView(this).apply {
                    text = buddy
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.rgb(25, 25, 25))
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    minHeight = dp(56)
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(14).toFloat()
                    }
                    elevation = dp(2).toFloat()
                    contentDescription = "Message $buddy"
                    isClickable = true
                    setOnClickListener {
                        startActivity(PrivateChatActivity.intent(this@InstantMessagingActivity, buddy))
                    }
                }, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                })
            }
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root, LinearLayout.LayoutParams(-1, -2))
        }
        contentFrame.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    companion object {
        fun intent(context: android.content.Context) =
            android.content.Intent(context, InstantMessagingActivity::class.java)
    }
}
