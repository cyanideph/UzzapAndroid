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
        "Supabase Auth", "PostgreSQL/RLS security", "Protected database functions", "Secure push-token handling", "CI APK builds", "Diagnostics and crash handling"
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
        addText(root, "Android/Kotlin modernization of the classic Uzzap chat experience, with a Supabase-backed realtime service and modern security controls.", 15, false)

        addHeading(root, "What's New in ${BuildConfig.VERSION_NAME}")
        addText(root, "Beta 7 messaging and emoticons\n• Added 30 Uzzap message color codes: c01-c30\n• Messages support color markup and multiple color segments\n• Nicknames remain text-only; color codes are not used for nicknames\n• Added Uzzap emoticon/emoji picker\n• Added numbered emoticons 11-20 and 47\n• Numbered emoticon markers render as their actual Uzzap images\n• Added legacy text emoticon rendering such as :), :D, :(, ;), :P and <3\n• Color codes and emoticons can be used together in messages", 15, false)
        addText(root, "Core chat reliability\n• Cursor-based private-message history and pagination\n• Retry-safe message sending and failed-message recovery\n• Realtime reconnect synchronization and duplicate-message protection\n• Room DELETE events and polling fallback", 15, false)
        addText(root, "Authentication and sync\n• Persistent Supabase sessions\n• Token refresh and expired-session recovery\n• Canonical username handling\n• Safer logout/session cleanup\n• Hardened RLS policies and authenticated ownership checks", 15, false)
        addText(root, "Notifications\n• FCM data-only notification flow\n• Private-message and buddy-request notifications\n• Room-invitation notifications\n• Tap-to-chat navigation\n• Duplicate-notification suppression\n• Automatic cleanup of invalid push tokens", 15, false)
        addText(root, "Moderation\n• Authenticated-only reporting and moderation operations\n• Moderator-only room\n• Moderator assignment/removal\n• Report queue and moderation audit history\n• Warn, mute, kick, ban, expiry and revoke controls\n• Role hierarchy and self-sanction safeguards\n• Protected server-side sanction materialization\n• Role-aware room message deletion", 15, false)
        addText(root, "Messaging and community\n• Philippine regions as chat categories\n• Provinces as chat rooms\n• Buddies and private chat\n• Delivery/read receipts\n• Room invitations\n• Uzzap bot welcome/system messages\n• Welcome messages render as pure centered system text\n• Block and report controls", 15, false)
        addText(root, "Profile and security\n• Nicknames are editable as text only\n• Nickname color markup is sanitized at the database boundary\n• Password recovery update path fixed for HTTP 405 failures\n• Supabase PostgreSQL/RLS backend\n• Protected SECURITY DEFINER database paths\n• Push-token RLS hardening\n• Message rate limiting", 15, false)
        addText(root, "Build and security\n• Android CI debug/release build workflows\n• Supabase publishable/anon key only in the Android client\n• No service-role/secret key in the Android client", 15, false)

        addHeading(root, "Features")
        features.forEachIndexed { index, feature ->
            addText(root, "${index + 1}. $feature", 15, false)
        }

        addHeading(root, "Version History")
        addText(root, "v1.0-beta.7", 16, true)
        addText(root, "September 2026\n• Added 30 Uzzap message color codes (c01-c30)\n• Added Uzzap emoticon/emoji picker and numbered emoticons 11-20 and 47\n• Added legacy text emoticon rendering\n• Preserved color markup for messages while keeping nicknames text-only\n• Added pure system-style UzzapBot welcome messages\n• Hardened nickname sanitization and room sender identity\n• Added secure moderator/admin room message deletion\n• Fixed password recovery HTTP 405 update failure\n• Updated version code to 7", 15, false)
        addText(root, "v1.0-beta.6", 16, true)
        addText(root, "September 2026\n• Consolidates Phase 1 core chat reliability\n• Consolidates Phase 2 authentication, sync and security hardening\n• Consolidates Phase 3 notification delivery and push cleanup\n• Consolidates Phase 4 moderation hardening\n• Expands the About screen to document the current feature set\n• Updates the Android build to versionCode 6", 15, false)
        addText(root, "v1.0-beta.5", 16, true)
        addText(root, "September 2026\n• Expanded moderation, invitations and message receipt features\n• Added moderator audit history\n• Added production security and rate-limit hardening\n• Added Philippine region/province room structure", 15, false)
        addText(root, "v1.0-beta.4", 16, true)
        addText(root, "September 2026\n• Added buddy/private chat workflows\n• Added reporting and moderation UI\n• Improved Supabase integration", 15, false)
        addText(root, "v1.0-beta.3", 16, true)
        addText(root, "September 2026\n• Android/Kotlin Uzzap modernization\n• Initial chatroom and profile flows", 15, false)
        addText(root, "Older history", 16, true)
        addText(root, "The current Android app is a Kotlin modernization inspired by the classic Smart Uzzap mobile client. Original and third-party materials are documented separately in the repository NOTICE and licensing documentation.", 15, false)

        addHeading(root, "Build Information")
        addText(root, "Application ID: ${BuildConfig.APPLICATION_ID}\nBuild type: ${BuildConfig.BUILD_TYPE}\nTarget SDK: 36\nBackend: Supabase Auth + PostgreSQL/RLS\nRelease channel: Beta", 14, false)

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
            setTextColor(resources.getColor(R.color.uzzap_special_red, theme))
            setPadding(0, dp(22), 0, dp(8))
        }
        parent.addView(view)
    }

    private fun addText(parent: LinearLayout, text: String, size: Int, bold: Boolean) {
        val view = TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setTextColor(resources.getColor(R.color.uzzap_text, theme))
            setPadding(0, dp(3), 0, dp(7))
        }
        parent.addView(view)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
