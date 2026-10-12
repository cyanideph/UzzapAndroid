package com.kolipri.uzzap.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.PasswordRecoveryClient
import com.kolipri.uzzap.data.SupabaseClient

class ResetPasswordActivity : BaseChromeActivity() {
    override fun contentLayoutRes() = R.layout.content_simple_text
    private var recoveryToken: String? = null
    private var passwordField: EditText? = null
    private var confirmField: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("Reset Password")
        showConnectedBall(false)
        handleRecoveryIntent(intent)
        showResetForm()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleRecoveryIntent(intent)
            showResetForm()
        }
    }

    private fun handleRecoveryIntent(intent: Intent) {
        if (PasswordRecoveryClient.consumeRecoveryIntent(intent.data)) {
            recoveryToken = AppState.authToken.ifBlank { SupabaseClient.userAccessToken.orEmpty() }
        }
    }

    private fun showResetForm() {
        val textView = findViewById<TextView>(R.id.tv_body) ?: return
        val parent = textView.parent as? ViewGroup ?: return
        parent.removeView(textView)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP; setPadding(32, 28, 32, 28) }
        parent.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply { text = "Choose a new Uzzap password"; textSize = 20f; setTextColor(ThemeManager.resolve(this@ResetPasswordActivity, R.attr.uzzapColorText)) })
        root.addView(TextView(this).apply { text = if (recoveryToken.isNullOrBlank()) "The reset link is missing or has expired. Request a new reset link from Login." else "Enter a new password below. Your old password will stop working after the change."; textSize = 14f; setPadding(0, 12, 0, 18) })
        if (recoveryToken.isNullOrBlank()) {
            root.addView(Button(this).apply { text = "Back to Login"; setOnClickListener { finish() } })
            return
        }
        passwordField = EditText(this).apply { hint = "New password (minimum 6 characters)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        confirmField = EditText(this).apply { hint = "Confirm new password"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        root.addView(passwordField); root.addView(confirmField)
        root.addView(Button(this).apply { text = "Change Password"; setOnClickListener { changePassword() } })
    }

    private fun changePassword() {
        val token = recoveryToken ?: return
        val password = passwordField?.text?.toString().orEmpty()
        val confirm = confirmField?.text?.toString().orEmpty()
        if (password.length < 6) { passwordField?.error = "Password must be at least 6 characters"; return }
        if (password != confirm) { confirmField?.error = "Passwords do not match"; return }
        passwordField?.isEnabled = false
        confirmField?.isEnabled = false
        PasswordRecoveryClient.updatePassword(token, password) { success, error ->
            runOnUiThread {
                passwordField?.isEnabled = true
                confirmField?.isEnabled = true
                if (success) {
                    SupabaseClient.signOut()
                    AppState.resetSession()
                    Toast.makeText(this, "Password changed successfully.", Toast.LENGTH_LONG).show()
                    AlertDialog.Builder(this).setTitle("Password Updated").setMessage("Your password has been changed. You can now log in with your new password.").setPositiveButton("Go to Login") { _, _ ->
                        startActivity(Intent(this, SplashMenuActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP })
                        finish()
                    }.show()
                } else AlertDialog.Builder(this).setTitle("Password Update Failed").setMessage(error ?: "The recovery link may have expired. Request another reset link.").setPositiveButton("OK", null).show()
            }
        }
    }
}
