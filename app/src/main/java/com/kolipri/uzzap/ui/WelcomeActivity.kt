package com.kolipri.uzzap.ui

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState

class WelcomeActivity : BaseChromeActivity() {

    override fun contentLayoutRes() = R.layout.content_welcome

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("")
        showConnectedBall(true)
        setSoftkeys(null, getString(R.string.softkey_close), onRight = { finish() })

        findViewById<TextView>(R.id.tv_welcome_title).text =
            "Welcome to Uzzap, ${AppState.username}!"

        bindTip(
            R.id.tip_im,
            R.drawable.d_d001_im_large,
            "Chat privately with your buddies. Send messages, follow delivery and read status, and keep track of unread conversations."
        )
        bindTip(
            R.id.tip_add,
            R.drawable.d_n002_buddies_large,
            "Build your Buddy List. Add or remove buddies, check presence, start private chats, block users, and report problems when needed."
        )
        bindTip(
            R.id.tip_chat,
            R.drawable.d_d002_chat_large,
            "Explore Philippine chatrooms by region and province. Join a room, meet other users, send messages, invite buddies, and leave anytime."
        )
        bindTip(
            R.id.tip_matching,
            R.drawable.d_n001_abm_large,
            "Stay safe and connected. Use Report and Block when necessary. Authorized moderators can review reports and manage room safety."
        )
    }

    private fun bindTip(containerId: Int, iconRes: Int, text: String) {
        val container = findViewById<android.view.View>(containerId)
        container.findViewById<ImageView>(R.id.iv_tip_icon).setImageResource(iconRes)
        container.findViewById<TextView>(R.id.tv_tip_text).text = text
    }
}
