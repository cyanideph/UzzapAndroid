package com.kolipri.uzzap.ui

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kolipri.uzzap.BuildConfig
import com.kolipri.uzzap.R

class AboutUzzapActivity : BaseChromeActivity() {
    private val featureGroups = listOf(
        "Community" to listOf(
            "Philippine region & province chat rooms", "Room joining and leaving",
            "Realtime room messaging", "Message history pagination",
            "Retry and reconnect recovery", "UzzapBot system messages"
        ),
        "Messaging" to listOf(
            "30 Uzzap color codes (c01-c30), multi-color messages",
            "Uzzap emoticon/emoji picker, numbered 11-20 and 47",
            "Legacy text emoticons (:) :D <3 …)", "Message deletion"
        ),
        "Buddies & private chat" to listOf(
            "Buddy requests, add/remove, groups", "Private one-to-one chat",
            "Delivery & read receipts", "Unread sync, room invitations",
            "Block and report users"
        ),
        "Notifications" to listOf(
            "FCM push, tap-to-chat", "Duplicate suppression", "Dead-token cleanup"
        ),
        "Moderation" to listOf(
            "Report queue, moderator-only room", "Warn, mute, kick, ban + expiry/revoke",
            "Audit log, role hierarchy safeguards", "Rate limiting"
        ),
        "Account & security" to listOf(
            "Login, signup, password recovery", "Encrypted sessions, token refresh",
            "Profiles, avatars, presence", "PostgreSQL/RLS + protected functions"
        )
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
        addText(root, "Beta 8 — Trust & polish\n• Nickname login without exposing emails\n• Truly private whispers (no more room broadcast)\n• Moderators can moderate their own messages\n• Encrypted sessions, no plaintext tokens\n• Notification permission asks at most twice\n• Fixed buddy lists and DMs for mixed-case names", 15, false)

        addHeading(root, "Version History")

        addText(root, "v1.0-beta.8 — Trust & Polish", 17, true)
        addText(root, "• Closed anon email enumeration via nickname-login RPC; rotated leaked keys\n• Fixed room/buddy leave after nickname changes\n• Whisper is now truly private\n• Unified encrypted session storage; startup blocks on missing/invalid key\n• Fixed FCM token ownership on multi-account devices", 15, false)

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
        featureGroups.forEach { (group, items) ->
            addText(root, group, 16, true)
            items.forEach { feature -> addText(root, "• $feature", 15, false) }
        }

        addHeading(root, "Support")
        addText(root, "Beta means rough edges — tell us.", 15, false)
        addLinkRow(root, "🐞  Report a bug", "https://github.com/cyanideph/UzzapAndroid/issues")
        addLinkRow(root, "🔒  Privacy policy", "https://cyanideph.github.io/uzzap-site/privacy.html")
        addLinkRow(root, "🗑  Delete my account", "https://cyanideph.github.io/uzzap-site/delete-account.html")

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

    private fun addLinkRow(parent: LinearLayout, label: String, url: String) {
        val view = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(UzzapDialogs.GREEN)
            setPadding(0, dp(6), 0, dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }.onFailure {
                    android.widget.Toast.makeText(this@AboutUzzapActivity, "Unable to open link", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        parent.addView(view)
    }

    private fun addText(parent: LinearLayout, text: String, size: Int, bold: Boolean) {
        val view = TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.resolve(this@AboutUzzapActivity, R.attr.uzzapColorText))
            setPadding(0, dp(3), 0, dp(7))
        }
        parent.addView(view)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
