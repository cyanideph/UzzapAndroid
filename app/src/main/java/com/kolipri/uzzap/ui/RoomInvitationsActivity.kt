package com.kolipri.uzzap.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.RoomInvitation
import com.kolipri.uzzap.data.RoomInvitationClient

class RoomInvitationsActivity : BaseChromeActivity() {
    override fun contentLayoutRes(): Int? = null

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("Room Invitations")
        showConnectedBall(true)
        setSoftkeys("Refresh", "Back", onLeft = { loadInvitations() }, onRight = { finish() })

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(16))
        }
        val header = TextView(this).apply {
            text = "Invitations to Philippine chatrooms"
            textSize = 16f
            setTextColor(ThemeManager.resolve(this, R.attr.uzzapColorMuted))
            setPadding(dp(8), dp(8), dp(8), dp(12))
        }
        root.addView(header)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = android.widget.ScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        contentFrame.addView(root, android.widget.FrameLayout.LayoutParams(-1, -1))
        loadInvitations()
    }

    private fun loadInvitations() {
        list.removeAllViews()
        val loading = TextView(this).apply {
            text = "Loading invitations…"
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(32), dp(16), dp(32))
        }
        list.addView(loading)
        RoomInvitationClient.fetchPending(AppState.username) { invitations ->
            if (isFinishing) return@fetchPending
            list.removeAllViews()
            if (invitations == null) {
                emptyState("Could not load invitations. Check your connection and try again.")
                return@fetchPending
            }
            if (invitations.isEmpty()) {
                emptyState("No pending room invitations.")
                return@fetchPending
            }
            invitations.forEach { invitation -> list.addView(invitationCard(invitation)) }
        }
    }

    private fun invitationCard(invitation: RoomInvitation): TextView = TextView(this).apply {
        text = "🛡️ ${invitation.roomName}\nInvited by: ${invitation.inviterUsername}\n${invitation.createdAt.take(19).replace('T', ' ')}\n\nTap to accept or decline"
        textSize = 14f
        setTextColor(Color.rgb(30, 35, 32))
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), Color.rgb(210, 218, 212))
        }
        setOnClickListener { respondTo(invitation) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    }

    private fun respondTo(invitation: RoomInvitation) {
        AlertDialog.Builder(this)
            .setTitle("${invitation.roomName} invitation")
            .setMessage("${invitation.inviterUsername} invited you to join this room.")
            .setPositiveButton("Accept") { _, _ ->
                RoomInvitationClient.respond(invitation.id, true) { ok ->
                    Toast.makeText(this, if (ok) "Invitation accepted. You can now open ${invitation.roomName}." else "Could not accept invitation.", Toast.LENGTH_SHORT).show()
                    if (ok) loadInvitations()
                }
            }
            .setNegativeButton("Decline") { _, _ ->
                RoomInvitationClient.respond(invitation.id, false) { ok ->
                    Toast.makeText(this, if (ok) "Invitation declined." else "Could not decline invitation.", Toast.LENGTH_SHORT).show()
                    if (ok) loadInvitations()
                }
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun emptyState(message: String) {
        list.addView(TextView(this).apply {
            text = message
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(Color.GRAY)
            setPadding(dp(16), dp(48), dp(16), dp(48))
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
