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
import com.kolipri.uzzap.data.UserProfileClient

/** Public profile hub with the same identity data used by room/DM surfaces. */
class ProfileActivity : BaseChromeActivity() {
    override fun contentLayoutRes(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("Profile")
        showConnectedBall(true)
        setSoftkeys("Options", "Back", onLeft = { showOptions() }, onRight = { finish() })
        render()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) render()
    }

    private fun render() {
        contentFrame.removeAllViews()
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(24))
        }
        scroll.addView(root, LinearLayout.LayoutParams(-1, -2))

        val avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.WHITE, 48f)
            contentDescription = "Profile avatar"
            load(AvatarManager.url(AppState.avatar, AppState.username))
        }
        root.addView(avatar, LinearLayout.LayoutParams(dp(92), dp(92)).apply { gravity = Gravity.CENTER_HORIZONTAL })

        root.addView(TextView(this).apply {
            text = AppState.nickname.ifBlank { AppState.username }
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.resolve(this@ProfileActivity, R.attr.uzzapColorText))
            setPadding(0, dp(8), 0, 0)
        })
        root.addView(TextView(this).apply {
            text = "@${AppState.username}"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.resolve(this@ProfileActivity, R.attr.uzzapColorMuted))
            setPadding(0, dp(2), 0, dp(12))
        })

        addCard(root, "PRESENCE", "${AppState.presence.label}\n${AppState.statusMessage.ifBlank { "No status message" }}")
        root.addView(actionRow("Edit My Profile", "Nickname, avatar, presence and About Me") {
            startActivity(UzzapParityActivity.intent(this, UzzapParityActivity.MODE_PROFILE))
        })
        root.addView(actionRow("Account Settings", "Password, mobile, privacy and device settings") {
            startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_SETTINGS))
        })

        addCard(root, "PUBLIC PROFILE", "Loading profile information…")
        UserProfileClient.fetch(AppState.username) { profile ->
            if (isFinishing || isDestroyed) return@fetch
            val public = profile ?: return@fetch
            val text = buildString {
                append("Username\n${public.username}\n\n")
                append("Nickname\n${public.nickname}\n\n")
                append("Presence\n${if (public.isOnline) "Online" else "Offline"}\n\n")
                append("Status\n${public.status}\n\n")
                append("About Me\n${public.statusMessage.ifBlank { "—" }}")
                if (!public.isOnline && public.lastSeen.isNotBlank()) append("\n\nLast seen\n${public.lastSeen}")
            }
            val index = root.indexOfLast { it.tag == "public_profile" }
            if (index >= 0) (root.getChildAt(index) as? TextView)?.text = text
        }

        contentFrame.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))
    }

    private fun addCard(root: LinearLayout, title: String, body: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.resolve(this@ProfileActivity, R.attr.uzzapColorHeader))
            setPadding(dp(4), dp(12), dp(4), dp(6))
        })
        root.addView(TextView(this).apply {
            text = body
            textSize = 15f
            setTextColor(ThemeManager.resolve(this@ProfileActivity, R.attr.uzzapColorText))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.WHITE, 14f)
            tag = if (title == "PUBLIC PROFILE") "public_profile" else null
        })
    }

    private fun actionRow(title: String, subtitle: String, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundResource(R.drawable.bg_menu_row_selector)
            isClickable = true
            setOnClickListener { action() }
            addView(TextView(this@ProfileActivity).apply {
                text = title
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ThemeManager.resolve(this@ProfileActivity, R.attr.uzzapColorText))
            })
            addView(TextView(this@ProfileActivity).apply {
                text = subtitle
                textSize = 12f
                setTextColor(ThemeManager.resolve(this@ProfileActivity, R.attr.uzzapColorMuted))
                setPadding(0, dp(2), 0, 0)
            })
        }

    private fun showOptions() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Edit Profile", "Settings", "Change Status", "Close")) { _, which ->
                when (which) {
                    0 -> startActivity(UzzapParityActivity.intent(this, UzzapParityActivity.MODE_PROFILE))
                    1 -> startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_SETTINGS))
                    2 -> startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_STATUS))
                }
            }.show()
    }

    private fun rounded(color: Int, radius: Float) =
        android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
