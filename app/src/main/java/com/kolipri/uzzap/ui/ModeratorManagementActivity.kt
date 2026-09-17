package com.kolipri.uzzap.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.ModeratorRoomClient

class ModeratorManagementActivity : BaseChromeActivity() {
    override fun contentLayoutRes(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppState.role != "admin") { Toast.makeText(this, "Admin privileges required", Toast.LENGTH_SHORT).show(); finish(); return }
        setScreenTitle("Moderator Management")
        showConnectedBall(true)
        setSoftkeys("Actions", "Back", onLeft = { showActions() }, onRight = { finish() })
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(18), dp(20), dp(18), dp(20)); setBackgroundColor(Color.parseColor("#EAEFEA")) }
        root.addView(TextView(this).apply { text = "🛡️ Moderator Management"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#0F5132")); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        root.addView(TextView(this).apply { text = "Assign or remove moderators by account email.\nAuthorization is enforced by the server."; textSize = 13f; setTextColor(Color.parseColor("#495057")); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(24) })
        root.addView(actionButton("➕ Add Moderator") { promptModeratorChange(true) }, buttonParams())
        root.addView(actionButton("➖ Remove Moderator") { promptModeratorChange(false) }, buttonParams())
        root.addView(actionButton("🛡️ Open Moderator Console") { startActivity(ModeratorActivity.intent(this)) }, buttonParams())
        root.addView(actionButton("📋 Moderation Audit Log") { startActivity(ModerationAuditActivity.intent(this)) }, buttonParams())
        root.addView(TextView(this).apply { text = "Moderator-only room access updates automatically after role changes."; textSize = 12f; setTextColor(Color.parseColor("#6C757D")); gravity = Gravity.CENTER; setPadding(dp(8), dp(24), dp(8), dp(8)) })
        contentFrame.addView(root, android.widget.FrameLayout.LayoutParams(-1, -1))
    }

    private fun promptModeratorChange(makeModerator: Boolean) {
        val input = EditText(this).apply { hint = "user@example.com"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle(if (makeModerator) "Add Moderator" else "Remove Moderator").setMessage(if (makeModerator) "Enter the user's account email." else "Enter the moderator's account email.").setView(input)
            .setPositiveButton(if (makeModerator) "Assign" else "Remove") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isBlank()) { Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                ModeratorRoomClient.setModeratorByEmail(email, makeModerator) { success, error -> Toast.makeText(this, if (success) if (makeModerator) "Moderator role assigned" else "Moderator role removed" else error ?: "Operation failed", Toast.LENGTH_LONG).show() }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showActions() { AlertDialog.Builder(this).setTitle("Moderator Management").setItems(arrayOf("Add Moderator", "Remove Moderator", "Moderator Console", "Audit Log", "Close")) { _, which -> when (which) { 0 -> promptModeratorChange(true); 1 -> promptModeratorChange(false); 2 -> startActivity(ModeratorActivity.intent(this)); 3 -> startActivity(ModerationAuditActivity.intent(this)) } }.show() }
    private fun actionButton(label: String, onClick: () -> Unit) = TextView(this).apply { text = label; gravity = Gravity.CENTER; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#0F5132")); setPadding(dp(12), dp(14), dp(12), dp(14)); setOnClickListener { onClick() } }
    private fun buttonParams() = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    companion object { fun intent(context: Context): Intent = Intent(context, ModeratorManagementActivity::class.java) }
}
