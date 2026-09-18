package com.kolipri.uzzap.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.kolipri.uzzap.R
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
        }

        val items = buildList {
            add(EmItem(R.drawable.d_n002_buddies_small, "Buddy Requests & Management", "Add, accept or manage buddies") {
                startActivity(ChatHostActivity.intent(this@CommunicationCenterActivity, ChatHostActivity.MODE_EM))
            })
            add(EmItem(R.drawable.d_d002_chat_small, "Room Invitations", "View invitations to chat rooms") {
                startActivity(Intent(this@CommunicationCenterActivity, RoomInvitationsActivity::class.java))
            })
            add(EmItem(R.drawable.d_d001_im_small, "Message Receipts", "View delivery and read receipt status") {
                startActivity(Intent(this@CommunicationCenterActivity, DeliveryReadReceiptsActivity::class.java))
            })
            if (AppState.isModerator || AppState.role == "admin") {
                add(EmItem(R.drawable.d_n003_settings_small, "Moderation & System Notices", "Moderator activity and reports") {
                    startActivity(ModeratorActivity.intent(this@CommunicationCenterActivity))
                })
            }
        }

        // J2ME-style list header, e.g. "Regions (1/1)"
        root.addView(TextView(this).apply {
            text = "Communication Center (${items.size})"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.resolve(this@CommunicationCenterActivity, R.attr.uzzapColorHeader))
            setPadding(dp(12), dp(12), dp(12), dp(8))
        })
        root.addView(divider(0))

        items.forEachIndexed { index, item -> addEmRow(root, item, showDivider = index < items.size - 1) }

        root.addView(TextView(this).apply {
            text = "Requests, invitations and communication activity are kept separate from private Buddy Chat."
            textSize = 12f
            setTextColor(ThemeManager.resolve(this@CommunicationCenterActivity, R.attr.uzzapColorMuted))
            setPadding(dp(12), dp(16), dp(12), 0)
        })
        contentFrame.addView(root)
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
        row.addView(android.widget.ImageView(this).apply {
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
            setTextColor(ThemeManager.resolve(this@CommunicationCenterActivity, R.attr.uzzapColorText))
        })
        textCol.addView(TextView(this).apply {
            text = item.subtitle
            textSize = 12f
            setTextColor(ThemeManager.resolve(this@CommunicationCenterActivity, R.attr.uzzapColorMuted))
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.d_small_arrow)
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(14), dp(14)))
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
        if (showDivider) parent.addView(divider(dp(14)))
    }

    private fun divider(marginStart: Int): View {
        return View(this).apply {
            setBackgroundColor(ThemeManager.resolve(this@CommunicationCenterActivity, R.attr.uzzapColorDivider))
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply {
                this.marginStart = marginStart
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
