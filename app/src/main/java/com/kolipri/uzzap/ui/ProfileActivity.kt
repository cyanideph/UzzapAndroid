package com.kolipri.uzzap.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
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

        val bg = ThemeManager.resolve(this, R.attr.uzzapColorBg, Color.WHITE)
        val surface = ThemeManager.resolve(this, R.attr.uzzapColorSurface, Color.WHITE)
        val textColor = ThemeManager.resolve(this, R.attr.uzzapColorText)
        val muted = ThemeManager.resolve(this, R.attr.uzzapColorMuted)
        val accent = ThemeManager.resolve(this, R.attr.uzzapColorAccent)
        val header = ThemeManager.resolve(this, R.attr.uzzapColorHeader)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            clipToPadding = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
        }
        scroll.addView(root, LinearLayout.LayoutParams(-1, -2))

        val identityCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(20), dp(18), dp(18))
            background = rounded(surface, 20f)
        }

        val avatarWrap = FrameLayoutCompat(this).apply {
            background = rounded(accent, 50f)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(surface, 47f)
            contentDescription = "Profile avatar"
            load(AvatarManager.url(AppState.avatar, AppState.username))
        }
        avatarWrap.addView(avatar, android.widget.FrameLayout.LayoutParams(dp(96), dp(96)))
        identityCard.addView(avatarWrap, LinearLayout.LayoutParams(dp(102), dp(102)))

        identityCard.addView(TextView(this).apply {
            text = AppState.nickname.ifBlank { AppState.username }
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setPadding(0, dp(12), 0, 0)
        })

        identityCard.addView(TextView(this).apply {
            text = "@" + AppState.username
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(2), 0, dp(8))
        })

        val presenceRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(blend(accent, surface, 0.18f), 18f)
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        presenceRow.addView(TextView(this).apply {
            text = "●"
            textSize = 12f
            setTextColor(accent)
        }, LinearLayout.LayoutParams(dp(18), -2))
        presenceRow.addView(TextView(this).apply {
            text = AppState.presence.label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
        })
        identityCard.addView(presenceRow, LinearLayout.LayoutParams(-2, -2))

        root.addView(identityCard, LinearLayout.LayoutParams(-1, -2))

        root.addView(sectionTitle("About", header))
        root.addView(infoCard(
            body = AppState.statusMessage.ifBlank { "No status message yet." },
            surface = surface,
            textColor = textColor
        ))

        root.addView(sectionTitle("Your profile", header))
        root.addView(actionRow(
            title = "Edit profile",
            subtitle = "Avatar, nickname, presence and bio",
            surface = surface,
            textColor = textColor,
            muted = muted,
            accent = accent
        ) {
            startActivity(UzzapParityActivity.intent(this, UzzapParityActivity.MODE_PROFILE))
        })
        root.addView(actionRow(
            title = "Account settings",
            subtitle = "Password, mobile, privacy and device",
            surface = surface,
            textColor = textColor,
            muted = muted,
            accent = accent
        ) {
            startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_SETTINGS))
        })

        root.addView(sectionTitle("Public profile", header))
        val publicProfile = infoCard(
            body = "Loading profile information…",
            surface = surface,
            textColor = textColor
        )
        publicProfile.tag = "public_profile"
        root.addView(publicProfile)

        UserProfileClient.fetch(AppState.username) { profile ->
            if (isFinishing || isDestroyed) return@fetch
            val result = profile ?: return@fetch
            val details = buildString {
                append("Username\n")
                append(result.username)
                append("\n\nNickname\n")
                append(result.nickname.ifBlank { "—" })
                append("\n\nPresence\n")
                append(if (result.isOnline) "Online" else "Offline")
                append("\n\nStatus\n")
                append(result.status.ifBlank { "—" })
                append("\n\nAbout Me\n")
                append(result.statusMessage.ifBlank { "—" })
                if (!result.isOnline && result.lastSeen.isNotBlank()) {
                    append("\n\nLast seen\n")
                    append(result.lastSeen)
                }
            }
            publicProfile.text = details
        }

        contentFrame.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))
    }

    private fun sectionTitle(title: String, color: Int): TextView = TextView(this).apply {
        text = title
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color)
        setPadding(dp(4), dp(20), dp(4), dp(8))
    }

    private fun infoCard(
        body: String,
        surface: Int,
        textColor: Int
    ): TextView = TextView(this).apply {
        text = body
        textSize = 15f
        setTextColor(textColor)
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(surface, 16f)
    }

    private fun actionRow(
        title: String,
        subtitle: String,
        surface: Int,
        textColor: Int,
        muted: Int,
        accent: Int,
        action: () -> Unit
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(13), dp(14), dp(13))
        background = actionBackground(surface, accent)
        isClickable = true
        isFocusable = true
        contentDescription = title + ". " + subtitle
        setOnClickListener { action() }

        val copy = LinearLayout(this@ProfileActivity).apply {
            orientation = LinearLayout.VERTICAL
        }
        copy.addView(TextView(this@ProfileActivity).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
        })
        copy.addView(TextView(this@ProfileActivity).apply {
            text = subtitle
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        })
        addView(copy, LinearLayout.LayoutParams(0, -2, 1f))

        addView(TextView(this@ProfileActivity).apply {
            text = "›"
            textSize = 28f
            setTextColor(accent)
            gravity = Gravity.CENTER
            contentDescription = "Open " + title
        }, LinearLayout.LayoutParams(dp(32), dp(48)))
    }

    private fun showOptions() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Edit Profile", "Settings", "Change Status", "Close")) { _, which ->
                when (which) {
                    0 -> startActivity(UzzapParityActivity.intent(this, UzzapParityActivity.MODE_PROFILE))
                    1 -> startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_SETTINGS))
                    2 -> startActivity(LegacySettingsActivity.intent(this, LegacySettingsActivity.MODE_STATUS))
                }
            }
            .show()
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun actionBackground(surface: Int, accent: Int): StateListDrawable =
        StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                rounded(blend(accent, surface, 0.14f), 16f)
            )
            addState(
                intArrayOf(android.R.attr.state_focused),
                rounded(blend(accent, surface, 0.10f), 16f)
            )
            addState(
                intArrayOf(),
                rounded(surface, 16f)
            )
        }

    private fun blend(foreground: Int, background: Int, foregroundWeight: Float): Int {
        val w = foregroundWeight.coerceIn(0f, 1f)
        val iw = 1f - w
        val r = (Color.red(foreground) * w + Color.red(background) * iw).toInt()
        val g = (Color.green(foreground) * w + Color.green(background) * iw).toInt()
        val b = (Color.blue(foreground) * w + Color.blue(background) * iw).toInt()
        return Color.rgb(r, g, b)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private class FrameLayoutCompat(context: android.content.Context) :
        android.widget.FrameLayout(context)
}
