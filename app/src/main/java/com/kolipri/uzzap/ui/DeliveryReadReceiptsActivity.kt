package com.kolipri.uzzap.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.kolipri.uzzap.data.DeliveryReceiptClient

class DeliveryReadReceiptsActivity : BaseChromeActivity() {
    override fun contentLayoutRes(): Int? = null
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("Message Receipts")
        showConnectedBall(true)
        setSoftkeys("Refresh", "Back", onLeft = { load() }, onRight = { finish() })
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(12)); setBackgroundColor(Color.rgb(247,248,250)) }
        root.addView(TextView(this).apply { text = "Delivery & Read Receipts"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(30,80,50)); setPadding(dp(6),dp(6),dp(6),dp(4)) })
        root.addView(TextView(this).apply { text = "✓ Sent  ·  ✓✓ Read"; textSize = 12f; setTextColor(Color.GRAY); setPadding(dp(6),0,dp(6),dp(10)) })
        val scroll = android.widget.ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        contentFrame.addView(root, android.widget.FrameLayout.LayoutParams(-1,-1))
        load()
    }

    private fun load() {
        list.removeAllViews()
        list.addView(TextView(this).apply { text = "Loading message receipts…"; gravity = Gravity.CENTER; setPadding(dp(16),dp(32),dp(16),dp(32)) })
        DeliveryReceiptClient.fetchRecent { receipts ->
            if (isFinishing) return@fetchRecent
            list.removeAllViews()
            if (receipts == null) { empty("Unable to load message receipts."); return@fetchRecent }
            if (receipts.isEmpty()) { empty("No sent private messages yet."); return@fetchRecent }
            receipts.forEach { r ->
                val status = if (r.isRead) "✓✓ Read" else "✓ Sent · Waiting to be read"
                list.addView(TextView(this).apply {
                    text = "$status\nTo: ${r.recipient}\n${r.body}\n${r.createdAt.take(19).replace('T',' ')}"
                    textSize = 13f; setTextColor(Color.rgb(35,38,42)); setPadding(dp(12),dp(11),dp(12),dp(11))
                    background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=dp(8).toFloat(); setStroke(dp(1),Color.rgb(225,228,232)) }
                    layoutParams = LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(7) }
                })
            }
        }
    }
    private fun empty(message:String){ list.addView(TextView(this).apply{text=message;gravity=Gravity.CENTER;textSize=15f;setTextColor(Color.GRAY);setPadding(dp(16),dp(48),dp(16),dp(48))}) }
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
}
