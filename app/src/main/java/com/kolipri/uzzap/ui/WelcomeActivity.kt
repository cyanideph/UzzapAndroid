package com.kolipri.uzzap.ui

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.SupabaseClient

class WelcomeActivity : BaseChromeActivity() {

    override fun contentLayoutRes() = R.layout.content_welcome

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("Welcome & Help")
        showConnectedBall(true)
        setSoftkeys(null, getString(R.string.softkey_close), onRight = { finish() })

        updateWelcomeTitle()

        // Refresh identity from the authenticated Supabase profile so an old locally
        // cached nickname can never replace the canonical account username.
        val token = SupabaseClient.userAccessToken
        if (!token.isNullOrBlank()) {
            SupabaseClient.verifySession(token) { ok, _, _ ->
                if (ok && !isFinishing) updateWelcomeTitle()
            }
        }

        bindTip(
            R.id.tip_im,
            R.drawable.d_d001_im_large,
            "Private chats",
            "Message buddies and see read status."
        )
        bindTip(
            R.id.tip_add,
            R.drawable.d_n002_buddies_large,
            "Your buddies",
            "Build your Buddy List and manage contacts."
        )
        bindTip(
            R.id.tip_chat,
            R.drawable.d_d002_chat_large,
            "Chat rooms",
            "Explore Philippine rooms and join conversations."
        )
        bindTip(
            R.id.tip_matching,
            R.drawable.d_n001_abm_large,
            "Stay safe",
            "Report or block when needed."
        )
    }

    private fun updateWelcomeTitle() {
        val accountName = AppState.username.ifBlank { AppState.nickname }.ifBlank { "Uzzap user" }
        findViewById<TextView>(R.id.tv_welcome_title).text =
            "Welcome to Uzzap, $accountName"
    }

    private fun bindTip(containerId: Int, iconRes: Int, title: String, text: String) {
        val container = findViewById<android.view.View>(containerId)
        container.findViewById<ImageView>(R.id.iv_tip_icon).setImageResource(iconRes)
        container.findViewById<TextView>(R.id.tv_tip_title).text = title
        container.findViewById<TextView>(R.id.tv_tip_text).text = text
    }
}
