package com.kolipri.uzzap.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.ModerationAuditClient
import com.kolipri.uzzap.model.ModerationAction

class ModerationAuditActivity : BaseChromeActivity() {
    override fun contentLayoutRes(): Int? = null
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppState.isModerator) {
            Toast.makeText(this, "Moderator or Admin privileges required", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        setScreenTitle("Moderation Audit Log")
        showConnectedBall(true)
        setSoftkeys("Refresh", "Back", onLeft = { load() }, onRight = { finish() })
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(12)); setBackgroundColor(Color.rgb(234,239,234)) }
        root.addView(TextView(this).apply {
            text = "Complete moderation history"
            textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(15,81,50)); setPadding(dp(6), dp(6), dp(6), dp(10))
        })
        val scroll = android.widget.ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        contentFrame.addView(root, android.widget.FrameLayout.LayoutParams(-1, -1))
        load()
    }

    private fun load() {
        list.removeAllViews()
        list.addView(TextView(this).apply { text = "Loading audit history…"; gravity = Gravity.CENTER; setPadding(dp(12), dp(32), dp(12), dp(32)) })
        ModerationAuditClient.fetch { actions ->
            if (isFinishing) return@fetch
            list.removeAllViews()
            if (actions == null) { empty("Unable to load audit history."); return@fetch }
            if (actions.isEmpty()) { empty("No moderation actions recorded."); return@fetch }
            actions.forEach { list.addView(card(it)) }
        }
    }

    private fun card(action: ModerationAction): TextView = TextView(this).apply {
        val scope = action.roomName ?: "GLOBAL"
        val expiry = action.expiresAt?.take(19)?.replace('T', ' ') ?: "Permanent / n/a"
        text = "${action.actionType.uppercase()} · ${action.targetUsername}\nScope: $scope\nReason: ${action.reason}\nTime: ${action.createdAt.take(19).replace('T', ' ')}\nExpiry: $expiry"
        textSize = 13f; setTextColor(Color.rgb(35,39,37)); setPadding(dp(12), dp(11), dp(12), dp(11))
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(8).toFloat(); setStroke(dp(1), Color.rgb(207,216,211)) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) }
    }

    private fun empty(message: String) { list.addView(TextView(this).apply { text = message; gravity = Gravity.CENTER; textSize = 15f; setTextColor(Color.GRAY); setPadding(dp(16), dp(48), dp(16), dp(48)) }) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    companion object { fun intent(context: Context): Intent = Intent(context, ModerationAuditActivity::class.java) }
}
