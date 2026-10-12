package com.kolipri.uzzap.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.ui.dialogs.J2mePopup
import com.kolipri.uzzap.ui.dialogs.PopupItem

class ConnectingActivity : BaseChromeActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun contentLayoutRes() = R.layout.content_connecting

    private val pendingRunnables = mutableListOf<Runnable>()
    private var navigated = false
    private var keyError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle("Uzzap")
        showConnectedBall(false)
        setSoftkeys(getString(R.string.softkey_options), null, onLeft = { showOptions() })

        val tvStatus = findViewById<TextView>(R.id.tv_status)

        // Stage 1: Connecting..
        tvStatus.text = getString(R.string.status_connecting)

        // Start background health and room sync
        SupabaseClient.checkHealth { isOnline, tablesReady, status ->
            if (isFinishing || isDestroyed) return@checkHealth
            if (!isOnline && status.contains("key", ignoreCase = true)) {
                keyError = true
                showConnectedBall(false)
                tvStatus.text = status
                return@checkHealth
            }
            if (isOnline) {
                AppState.syncProfileToSupabase()
                com.kolipri.uzzap.data.FcmClient.syncTokenWithSupabase(this@ConnectingActivity)
                if (tablesReady) {
                    SupabaseClient.fetchCategoriesAndRooms { newCategories ->
                        if (!newCategories.isNullOrEmpty()) {
                            AppState.categories = newCategories
                        }
                    }
                }
            }
        }

        // Stage 2: Authenticating.. after 800ms
        val authRunnable = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            tvStatus.text = getString(R.string.status_authenticating)
        }
        pendingRunnables.add(authRunnable)
        handler.postDelayed(authRunnable, 800)

        // Stage 3: Initializing.. after 1700ms (and show connected green ball)
        val initRunnable = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            showConnectedBall(true)
            tvStatus.text = getString(R.string.status_initializing)
        }
        pendingRunnables.add(initRunnable)
        handler.postDelayed(initRunnable, 1700)

        // Stage 4: Navigate to AppGridActivity after 2600ms (blocked on key/config errors)
        val navRunnable = Runnable { navigateOnce() }
        pendingRunnables.add(navRunnable)
        handler.postDelayed(navRunnable, 2600)
    }

    private fun navigateOnce() {
        if (navigated || keyError || isFinishing || isDestroyed) return
        navigated = true
        startActivity(Intent(this, AppGridActivity::class.java))
        finish()
    }

    private fun showOptions() {
        J2mePopup.show(this, listOf(PopupItem("Cancel Connection"))) {
            cancelAll()
            Toast.makeText(this, "Connection cancelled", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun cancelAll() {
        pendingRunnables.forEach { handler.removeCallbacks(it) }
        pendingRunnables.clear()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAll()
    }
}
