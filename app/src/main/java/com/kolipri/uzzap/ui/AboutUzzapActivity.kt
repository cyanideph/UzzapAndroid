package com.kolipri.uzzap.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.R

class AboutUzzapActivity : BaseChromeActivity() {
    private val features = listOf(
        "Login and signup", "Logout and password recovery", "Profile creation and editing", "Avatar support", "Online/offline presence",
        "Philippine region/category selection", "Province chat rooms", "Room joining and leaving", "Realtime room messaging", "Message history pagination",
        "Message retry and reconnect recovery", "Message deletion", "30 Uzzap message color codes (c01-c30)", "Color-coded messages with multiple colors", "Uzzap emoticon/emoji picker",
        "Numbered Uzzap emoticons 11-20 and 47", "Legacy text emoticon rendering", "Buddies", "Buddy requests", "Add/remove buddies", "Block and report users",
        "Private chat", "Delivery receipts", "Read receipts", "Unread synchronization", "Room invitations", "Uzzap bot system messages",
        "FCM push notifications", "Notification tap-to-chat", "Duplicate notification suppression", "Moderator report queue", "Moderator assignment/removal",
        "Moderator-only room", "Warn", "Mute", "Kick", "Ban", "Sanction expiry", "Sanction revoke", "Moderator audit log",
        "Role-aware message deletion", "Moderation hierarchy safeguards", "Rate limiting", "Session persistence", "Token refresh and recovery",
        "Secure cloud auth", "PostgreSQL/RLS security", "Protected database functions", "Secure push-token handling", "CI APK builds", "Diagnostics and crash handling"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("About Uzzap")
        showConnectedBall(true)
        setSoftkeys(null, getString(R.string.softkey_close), onRight = { finish() })
        contentFrame.addView(
            buildContent(),
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun buildContent(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
        }

        ImageView(this).apply {
            setImageResource(R.drawable.d_logo_large)
            contentDescription = getString(R.string.app_name)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }.also { root.addView(it) }

        addHeading(root, "Uzzap")
        addText(root, "Modern Android Uzzap client • Created by CY", 16, true)
        addText(root, "Version ${BuildConfig.VERSION_NAME}  •  Build ${BuildConfig.VERSION_CODE}", 15, false)
        addText(root, "Release channel: Beta", 14, false)
        addText(root, "Android/Kotlin modernization of the classic Uzzap chat experience with modern security and realtime messaging.", 15, false)

        addHeading(root, "What's New in ${BuildConfig.VERSION_NAME}")
        addText(root, "Beta 7 — Uzzap identity, colors and emoticons\n• Added all 30 Uzzap message color codes: c01-c30\n• Messages support color markup and multiple color segments\n• Nicknames remain text-only and keep the normal Uzzap nickname color\n• Added Uzzap emoticon/emoji picker\n• Added numbered emoticons 11-20 and 47\n• Numbered emoticon markers render as their actual Uzzap images\n• Added legacy text emoticon rendering such as :), :D, :(, ;), :P and <3\n• Color codes and emoticons can be used together in messages\n• Added pure centered UzzapBot system welcome messages\n• Hardened nickname sanitization and room sender identity\n• Added secure moderator/admin room message deletion\n• Fixed password recovery HTTP 405 update failure", 15, false)

        addHeading(root, "Version History")

        addText(root, "v1.0-beta.1 — Foundation", 17, true)
        addText(root, "• Started the Android/Kotlin modernization of the Uzzap experience\n• Established the Android project structure and application identity\n• Built the first navigation, screen and shared UI foundations\n• Established the initial login, signup and profile direction\n• Began replacing the legacy client architecture with a maintainable Android codebase", 15, false)

        addText(root, "v1.0-beta.2 — Core Chat", 17, true)
        addText(root, "• Added the first working chatroom experience\n• Added room joining and leaving\n• Added realtime room messaging and message history\n• Added basic reconnect and message-retry handling\n• Connected the application to its backend data model", 15, false)

        addText(root, "v1.0-beta.3 — Philippine Community", 17, true)
        addText(root, "• Organized the community around Philippine regions and provinces\n• Added region/category selection\n• Added province-based chat rooms\n• Expanded profile and presence handling\n• Improved room navigation and synchronization\n• Established the foundation for the modern Uzzap community structure", 15, false)

        addText(root, "v1.0-beta.4 — Social & Private Chat", 17, true)
        addText(root, "• Added buddies and buddy requests\n• Added private one-to-one chat\n• Added delivery and read receipt handling\n• Added unread synchronization\n• Added blocking and reporting workflows\n• Added room invitations and related social flows", 15, false)

        addText(root, "v1.0-beta.5 — Notifications & Moderation", 17, true)
        addText(root, "• Added FCM push notification delivery\n• Added notification tap-to-chat navigation\n• Added duplicate-notification suppression and push-token cleanup\n• Added moderator report queue and audit history\n• Added moderator assignment and role-aware controls\n• Added warn, mute, kick, ban, expiry and revoke workflows\n• Added moderator-only room support", 15, false)

        addText(root, "v1.0-beta.6 — Reliability & Security", 17, true)
        addText(root, "• Hardened authentication and persistent sessions\n• Added token refresh and expired-session recovery\n• Strengthened database ownership and authorization checks\n• Added protected server-side moderation functions\n• Added message rate limiting and safer synchronization\n• Improved realtime reconnect and duplicate-message protection\n• Added notification cleanup and delivery safeguards\n• Added Android CI APK build and diagnostic workflows", 15, false)

        addText(root, "v1.0-beta.7 — Uzzap Identity & Messaging", 17, true)
        addText(root, "• Added 30 Uzzap message color codes: c01-c30\n• Added multi-color message rendering\n• Kept nickname editing text-only; nickname colors are not user-selectable\n• Added Uzzap emoticon/emoji picker\n• Added numbered emoticons 11-20 and 47\n• Added legacy text emoticon rendering\n• Added color + emoticon combinations in messages\n• Added pure system-style UzzapBot welcome messages\n• Sanitized nickname markup at the database boundary\n• Added secure moderator/admin room message deletion\n• Fixed password recovery HTTP 405 update failure\n• Updated the Android application to version code 7", 15, false)

        addHeading(root, "Current Feature Set")
        features.forEachIndexed { index, feature ->
            addText(root, "${index + 1}. $feature", 15, false)
        }

        addHeading(root, "Build Information")
        addText(root, "Application ID: ${BuildConfig.APPLICATION_ID}\nBuild type: ${BuildConfig.BUILD_TYPE}\nTarget SDK: 36\nPush: Firebase Cloud Messaging\nRelease channel: Beta", 14, false)

        addText(root, "Uzzap • Created by CY", 14, true)
        return ScrollView(this).apply {
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun addHeading(parent: LinearLayout, text: String) {
        val view = TextView(this).apply {
            this.text = text
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UzzapDialogs.GREEN)
            setPadding(0, dp(22), 0, dp(8))
        }
        parent.addView(view)
    }

    private fun addText(parent: LinearLayout, text: String, size: Int, bold: Boolean) {
        val view = TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.resolve(this, R.attr.uzzapColorText))
            setPadding(0, dp(3), 0, dp(7))
        }
        parent.addView(view)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
