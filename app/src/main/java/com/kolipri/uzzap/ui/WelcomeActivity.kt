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
            getString(R.string.welcome_title, AppState.username)

        bindTip(R.id.tip_im, R.drawable.d_d001_im_large,
            "Connect to your favorite IM \u2013 wait a few seconds and watch your familiar YM or MSN buddies appear.")
        bindTip(R.id.tip_add, R.drawable.d_n002_buddies_large,
            "Go to the address book on your phone and Invite Friends to join your personal network, or Add Other Contacts by selecting their details.")
        bindTip(R.id.tip_chat, R.drawable.d_d002_chat_large,
            "Join a Chatroom and meet new friends \u2013 you can even use an alias to try it out.")
        bindTip(R.id.tip_matching, R.drawable.d_n001_abm_large,
            "Activate Buddy Matching and watch your buddy list grow automatically day by day.")
    }

    private fun bindTip(containerId: Int, iconRes: Int, text: String) {
        val container = findViewById<android.view.View>(containerId)
        container.findViewById<ImageView>(R.id.iv_tip_icon).setImageResource(iconRes)
        container.findViewById<TextView>(R.id.tv_tip_text).text = text
    }
}
