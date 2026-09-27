package com.kolipri.uzzap.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.PrivateMessageStateClient
import com.kolipri.uzzap.data.RoomInvitationClient
import com.kolipri.uzzap.data.UzzapNotificationManager

/** Unified notification inbox for private messages, room invitations and app alerts. */
class NotificationCenterActivity : BaseChromeActivity() {
    override fun contentLayoutRes(): Int? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("Notifications"); showConnectedBall(true)
        setSoftkeys("Options", "Back", onLeft = { showOptions() }, onRight = { finish() }); render()
    }
    override fun onResume(){super.onResume();if(!isFinishing)render()}
    private fun render(){
        contentFrame.removeAllViews()
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(20))}
        root.addView(TextView(this).apply{text="Notifications";textSize=20f;setTextColor(ThemeManager.resolve(this@NotificationCenterActivity,R.attr.uzzapColorHeader));setPadding(0,0,0,dp(10))})
        addRow(root,"Private Messages","Loading…")
        addRow(root,"Room Invitations","Loading…")
        addRow(root,"Notification Preferences","Sounds, vibration and notification behavior"){startActivity(LegacySettingsActivity.intent(this,LegacySettingsActivity.MODE_TONES))}
        contentFrame.addView(root)
        PrivateMessageStateClient.fetchUnread(AppState.username){state->
            if(isFinishing||isDestroyed)return@fetchUnread
            updateRow(root,1,"Private Messages",if(state?.count?:0>0) state!!.count.toString()+" unread message(s)" else "No unread private messages")
        }
        RoomInvitationClient.fetchPending(AppState.username){items->
            if(isFinishing||isDestroyed)return@fetchPending
            updateRow(root,2,"Room Invitations",if(items.isNullOrEmpty())"No pending invitations" else items.size.toString()+" pending invitation(s)")
        }
    }
    private fun addRow(root:LinearLayout,title:String,subtitle:String,onClick:(()->Unit)?=null){
        root.addView(TextView(this).apply{text=title+"\n"+subtitle;textSize=16f;setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(14),dp(13),dp(14),dp(13));setBackgroundResource(R.drawable.bg_menu_row_selector);if(onClick!=null){isClickable=true;setOnClickListener{onClick()}}})
    }
    private fun updateRow(root:LinearLayout,index:Int,title:String,subtitle:String){
        val view=root.getChildAt(index) as? TextView ?: return
        view.text=title+"\n"+subtitle
    }
    private fun showOptions(){AlertDialog.Builder(this).setItems(arrayOf("Notification Settings","Room Invitations","Private Messages","Close")){_,which->
        when(which){0->startActivity(LegacySettingsActivity.intent(this,LegacySettingsActivity.MODE_TONES));1->startActivity(RoomInvitationsActivity.intent(this));2->startActivity(InstantMessagingActivity.intent(this))}
    }.show()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt()
}