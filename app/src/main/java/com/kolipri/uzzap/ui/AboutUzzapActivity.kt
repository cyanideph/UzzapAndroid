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
        "Login", "Signup", "Logout", "Password recovery", "Profile creation", "Profile editing", "Avatar", "Online/offline presence",
        "Philippine region/category selection", "Province chat rooms", "Room joining", "Room leaving", "Room messages", "Realtime message refresh", "Message deletion",
        "Buddies", "Add buddy", "Remove buddy", "Block user", "Private chat", "Delivery receipts", "Read receipts", "Room invitations", "User reports",
        "Moderator report queue", "Moderator assignment", "Moderator removal", "Moderator-only room", "Warn", "Mute", "Kick", "Ban", "Sanction expiry",
        "Sanction revoke", "Moderator audit log", "Rate limiting", "Notifications", "Error handling", "Session persistence", "RLS/security enforcement"
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
        addText(root, "Current release: Beta", 14, false)

        addHeading(root, "What's New")
        addText(root, "Version ${BuildConfig.VERSION_NAME}", 16, true)
        addText(root, "• Philippine region → province chatroom structure\n• Buddy and private messaging experience\n• Delivery and read receipts\n• Moderator console and moderator-only room\n• Reports, warnings, mutes, kicks, bans and sanctions\n• Room invitations and Uzzap bot system messages\n• Supabase-backed realtime data and security enforcement\n• Improved session recovery and error handling", 15, false)

        addHeading(root, "Features")
        features.forEachIndexed { index, feature ->
            addText(root, "${index + 1}. $feature", 15, false)
        }

        addHeading(root, "Version History")
        addText(root, "v1.0-beta.5", 16, true)
        addText(root, "September 2026\n• Expanded moderation, invitations and message receipt features\n• Added moderator audit history\n• Added production security and rate-limit hardening\n• Added Philippine region/province room structure", 15, false)
        addText(root, "v1.0-beta.4", 16, true)
        addText(root, "September 2026\n• Added buddy/private chat workflows\n• Added reporting and moderation UI\n• Improved Supabase integration", 15, false)
        addText(root, "v1.0-beta.3", 16, true)
        addText(root, "September 2026\n• Android/Kotlin Uzzap modernization\n• Initial chatroom and profile flows", 15, false)
        addText(root, "Older history", 16, true)
        addText(root, "The current Android app is a Kotlin modernization inspired by the classic Smart Uzzap mobile client. This history records the Android project's releases; original and third-party materials are documented separately in the repository NOTICE and About documentation.", 15, false)

        addHeading(root, "Build Information")
        addText(root, "Application ID: ${BuildConfig.APPLICATION_ID}\nBuild type: ${BuildConfig.BUILD_TYPE}\nSupabase backend: Connected through the app's configured client", 14, false)

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
