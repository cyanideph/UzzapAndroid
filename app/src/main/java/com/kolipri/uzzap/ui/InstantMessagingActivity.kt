package com.kolipri.uzzap.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import coil.load
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.AvatarManager
import com.kolipri.uzzap.data.DirectMessageClient
import com.kolipri.uzzap.data.PrivateMessageStateClient
import com.kolipri.uzzap.data.UserProfileClient

/** Full private-message inbox: recent conversations first, then available buddies. */
class InstantMessagingActivity : BaseChromeActivity() {
    override fun contentLayoutRes(): Int? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("Instant Messaging"); showConnectedBall(true)
        setSoftkeys("Options", "Back", onLeft = { showOptions() }, onRight = { finish() }); renderInbox()
    }
    override fun onResume() { super.onResume(); if (!isFinishing) renderInbox() }

    private fun renderInbox() {
        contentFrame.removeAllViews()
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), 0, dp(4), dp(18)) }
        scroll.addView(root, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "Private Messages"; textSize = 19f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.resolve(this@InstantMessagingActivity, R.attr.uzzapColorHeader))
            setPadding(dp(12), dp(12), dp(12), dp(8))
        })
        root.addView(hairline(0))
        val buddies = AppState.buddyGroups.flatMap { it.buddies }.map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(AppState.username, true) }
            .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        var unreadCounts: Map<String, Int>? = null
        var recentPartners: List<String>? = null
        fun renderWhenReady() {
            if (isFinishing || isDestroyed || unreadCounts == null || recentPartners == null) return
            val recent = recentPartners!!.distinctBy { it.lowercase() }
            val recentKeys = recent.map { it.lowercase() }.toSet()
            val ordered = recent + buddies.filterNot { recentKeys.contains(it.lowercase()) }
            renderRows(root, ordered, unreadCounts!!)
        }
        PrivateMessageStateClient.fetchUnread(AppState.username) { state ->
            unreadCounts = state?.countsBySender ?: emptyMap()
            renderWhenReady()
        }
        DirectMessageClient.fetchRecentConversationPartners { recent ->
            recentPartners = recent
            renderWhenReady()
        }
        contentFrame.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))
    }

    private fun renderRows(root: LinearLayout, names: List<String>, unreadCounts: Map<String, Int>, clearExisting: Boolean = true) {
        if (clearExisting) while (root.childCount > 2) root.removeViewAt(2)
        if (names.isEmpty()) { if (root.childCount <= 2) root.addView(emptyRow()); return }
        names.distinctBy { it.lowercase() }.forEach { buddy -> root.addView(buddyRow(buddy, unreadCounts[buddy.lowercase()] ?: 0)) }
    }

    private fun emptyRow(): View = TextView(this).apply {
        text = "No conversations yet.\nAdd a buddy from Extended Messaging."
        textSize = 16f; gravity = Gravity.CENTER
        setTextColor(ThemeManager.resolve(this@InstantMessagingActivity, R.attr.uzzapColorMuted))
        setPadding(dp(20), dp(40), dp(20), dp(40))
    }

    private fun buddyRow(buddy: String, unread: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9)); setBackgroundResource(R.drawable.bg_menu_row_selector)
            contentDescription = "Message " + buddy; isClickable = true
            setOnClickListener { startActivity(PrivateChatActivity.intent(this@InstantMessagingActivity, buddy)) }
        }
        val avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; background = rounded(Color.WHITE, 24)
            load(AvatarManager.url(null, buddy)); contentDescription = buddy + " avatar"
        }
        row.addView(avatar, LinearLayout.LayoutParams(dp(48), dp(48)))
        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(6), 0) }
        textCol.addView(TextView(this@InstantMessagingActivity).apply {
            text = buddy; textSize = 17f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.resolve(this@InstantMessagingActivity, R.attr.uzzapColorText))
        })
        textCol.addView(TextView(this@InstantMessagingActivity).apply {
            text = if (unread > 0) unread.toString() + " unread message" + if (unread == 1) "" else "s" else "Private conversation"
            textSize = 12f; setTextColor(ThemeManager.resolve(this@InstantMessagingActivity, R.attr.uzzapColorMuted))
        })
        row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
        if (unread > 0) row.addView(TextView(this).apply {
            text = if (unread > 99) "99+" else unread.toString(); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); background = rounded(ThemeManager.resolve(this@InstantMessagingActivity, R.attr.uzzapColorHeader), 16)
            minWidth = dp(30); setPadding(dp(7), dp(4), dp(7), dp(4))
        })
        UserProfileClient.fetch(buddy) { profile ->
            if (!isFinishing && !isDestroyed) avatar.load(AvatarManager.url(profile?.avatar, buddy))
        }
        return row
    }

    private fun showOptions() {
        AlertDialog.Builder(this).setItems(arrayOf("Buddy List", "Extended Messaging", "Notification Settings", "Close")) { _, which ->
            when (which) {
                0 -> startActivity(ChatHostActivity.intent(this, ChatHostActivity.MODE_EM))
                1 -> startActivity(CommunicationCenterActivity.intent(this))
                2 -> startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_TONES))
            }
        }.show()
    }
    private fun hairline(marginStart: Int): View = View(this).apply {
        setBackgroundColor(ThemeManager.resolve(this@InstantMessagingActivity, R.attr.uzzapColorDivider))
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { this.marginStart = marginStart }
    }
    private fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    companion object { fun intent(context: android.content.Context) = android.content.Intent(context, InstantMessagingActivity::class.java) }
}