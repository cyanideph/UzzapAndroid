package com.kolipri.uzzap.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.kolipri.uzzap.data.AppState

/** Extended Messaging: communication activity and requests, separate from private chat. */
class CommunicationCenterActivity : BaseChromeActivity() {
    override fun contentLayoutRes(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("Extended Messaging")
        showConnectedBall(true)
        setSoftkeys("Options", "Back", onLeft = { }, onRight = { finish() })
        render()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) render()
    }

    private fun render() {
        contentFrame.removeAllViews()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(18))
        }
        root.addView(TextView(this).apply {
            text = "COMMUNICATION CENTER"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(13, 12, 175))
            setPadding(dp(4), dp(4), dp(4), dp(12))
        })
        addAction(root, "Buddy Requests & Management", "Add, accept or manage buddies") {
            startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM))
        }
        addAction(root, "Room Invitations", "View invitations to chat rooms") {
            startActivity(Intent(this, RoomInvitationsActivity::class.java))
        }
        addAction(root, "Message Receipts", "View delivery and read receipt status") {
            startActivity(Intent(this, DeliveryReadReceiptsActivity::class.java))
        }
        if (AppState.isModerator || AppState.role == "admin") {
            addAction(root, "Moderation & System Notices", "Moderator activity and reports") {
                startActivity(ModeratorActivity.intent(this))
            }
        }
        root.addView(TextView(this).apply {
            text = "Requests, invitations and communication activity are kept separate from private Buddy Chat."
            textSize = 13f
            setTextColor(Color.rgb(90, 90, 90))
            setPadding(dp(6), dp(18), dp(6), 0)
        })
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
