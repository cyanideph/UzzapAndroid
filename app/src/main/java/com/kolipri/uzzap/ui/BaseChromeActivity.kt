package com.kolipri.uzzap.ui

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import java.util.Collections
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Intent
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppDiagnostics
import com.kolipri.uzzap.data.PresenceClient
import com.kolipri.uzzap.data.SupabaseClient

/** Shared phone chrome plus automatic diagnostics for screens and hosted fragments. */
abstract class BaseChromeActivity : AppCompatActivity() {
    protected lateinit var contentFrame: FrameLayout
    protected lateinit var tvTitle: TextView
    protected lateinit var ivStatusBall: ImageView
    protected lateinit var btnLeft: TextView
    protected lateinit var btnRight: TextView
    protected lateinit var tabStripContainer: LinearLayout

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val activeNetworks = Collections.synchronizedSet(mutableSetOf<Network>())
    @Volatile private var destroyed = false
    protected var isNetworkConnected: Boolean = true
        private set

    private val sessionExpiredListener: () -> Unit = {
        if (!destroyed && !isFinishing && !isDestroyed) {
            AppDiagnostics.warn(this, "Auth.Session", "Session expired; returning to login")
            startActivity(Intent(this, SplashMenuActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }
    }

    private val fragmentDiagnostics = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {
            AppDiagnostics.event("Page.${f.javaClass.simpleName}", AppDiagnostics.Status.PASS, "FRAGMENT_CREATED")
        }
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            AppDiagnostics.event("Page.${f.javaClass.simpleName}", AppDiagnostics.Status.PASS, "FRAGMENT_STARTED")
        }
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            AppDiagnostics.event("Page.${f.javaClass.simpleName}", AppDiagnostics.Status.PASS, "FRAGMENT_RESUMED")
        }
        override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
            AppDiagnostics.info(this@BaseChromeActivity, "Page", "${f.javaClass.simpleName} FRAGMENT_PAUSED")
        }
        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
            AppDiagnostics.info(this@BaseChromeActivity, "Page", "${f.javaClass.simpleName} FRAGMENT_DESTROYED")
        }
    }

    @LayoutRes
    protected open fun contentLayoutRes(): Int? = null

    private var appliedThemeName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        appliedThemeName = ThemeManager.current(this)
        super.onCreate(savedInstanceState)
        AppDiagnostics.monitorActivity(this, "chrome_create")
        SupabaseClient.addSessionExpiredListener(sessionExpiredListener)
        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentDiagnostics, true)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_chrome)
        val chromeRoot = findViewById<View>(R.id.chrome_root)
        val statusBarSpacer = findViewById<View>(R.id.status_bar_spacer)
        val navBarSpacer = findViewById<View>(R.id.nav_bar_spacer)
        val softkeyBar = findViewById<View>(R.id.softkey_bar_root)
        contentFrame = findViewById(R.id.content_frame)
        tvTitle = findViewById(R.id.tv_title)
        ivStatusBall = findViewById(R.id.iv_status_ball)
        btnLeft = findViewById(R.id.btn_left)
        btnRight = findViewById(R.id.btn_right)
        tabStripContainer = findViewById(R.id.tab_strip_container)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork
        if (activeNetwork != null) {
            activeNetworks.add(activeNetwork)
            setConnectionStatus(true)
        } else {
            setConnectionStatus(false)
        }
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = runOnUiThread {
                if (destroyed || isFinishing || isDestroyed) return@runOnUiThread
                val wasDisconnected = activeNetworks.isEmpty()
                activeNetworks.add(network)
                setConnectionStatus(true)
                AppDiagnostics.event("Network", AppDiagnostics.Status.PASS, "AVAILABLE")
                if (wasDisconnected) onNetworkRestored()
            }
            override fun onLost(network: Network) = runOnUiThread {
                if (destroyed || isFinishing || isDestroyed) return@runOnUiThread
                activeNetworks.remove(network)
                val stillConnected = activeNetworks.isNotEmpty()
                setConnectionStatus(stillConnected)
                AppDiagnostics.event("Network", AppDiagnostics.Status.WARN, "LOST")
                if (!stillConnected) onNetworkLost()
            }
        }
        runCatching { connectivityManager?.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback!!) }

        ViewCompat.setOnApplyWindowInsetsListener(chromeRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            statusBarSpacer.layoutParams = statusBarSpacer.layoutParams.apply { height = bars.top }
            navBarSpacer.layoutParams = navBarSpacer.layoutParams.apply { height = if (imeVisible) 0 else bars.bottom }
            statusBarSpacer.requestLayout()
            navBarSpacer.requestLayout()
            softkeyBar.visibility = if (imeVisible) View.GONE else View.VISIBLE
            contentFrame.updatePadding(bottom = if (imeVisible) ime.bottom else 0)
            insets
        }
        ViewCompat.requestApplyInsets(chromeRoot)
        contentLayoutRes()?.let { res -> LayoutInflater.from(this).inflate(res, contentFrame, true) }
    }

    fun setScreenTitle(title: String) {
        tvTitle.text = title
        AppDiagnostics.event("Page.${javaClass.simpleName}", AppDiagnostics.Status.PASS, "TITLE=${title.take(80)}")
    }

    fun showConnectedBall(show: Boolean) {
        ivStatusBall.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun setConnectionStatus(connected: Boolean) {
        isNetworkConnected = connected
        if (::ivStatusBall.isInitialized) ivStatusBall.setImageResource(if (connected) R.drawable.d_online else R.drawable.d_disconnected)
    }

    protected open fun onNetworkRestored() {}
    protected open fun onNetworkLost() {}

    override fun onResume() {
        super.onResume()
        if (!com.kolipri.uzzap.data.SupabaseClient.userAccessToken.isNullOrBlank()) PresenceClient.startHeartbeat()
    }

    override fun onPause() {
        PresenceClient.stopHeartbeat()
        super.onPause()
    }

    override fun onDestroy() {
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentDiagnostics)
        SupabaseClient.removeSessionExpiredListener(sessionExpiredListener)
        destroyed = true
        activeNetworks.clear()
        networkCallback?.let { runCatching { connectivityManager?.unregisterNetworkCallback(it) } }
        AppDiagnostics.monitorActivity(this, "chrome_destroy")
        super.onDestroy()
    }

    fun setSoftkeys(leftText: String?, rightText: String?, onLeft: (() -> Unit)? = null, onRight: (() -> Unit)? = null) {
        if (leftText == null) btnLeft.visibility = View.INVISIBLE else {
            btnLeft.visibility = View.VISIBLE
            btnLeft.text = leftText
            btnLeft.setOnClickListener {
                AppDiagnostics.event("UI.${javaClass.simpleName}.softkey.left", AppDiagnostics.Status.RUNNING, "clicked")
                runCatching { onLeft?.invoke() }.onFailure { AppDiagnostics.failed("UI.${javaClass.simpleName}.softkey.left", it.message ?: it.javaClass.simpleName) }
            }
        }
        if (rightText == null) btnRight.visibility = View.INVISIBLE else {
            btnRight.visibility = View.VISIBLE
            btnRight.text = rightText
            btnRight.setOnClickListener {
                AppDiagnostics.event("UI.${javaClass.simpleName}.softkey.right", AppDiagnostics.Status.RUNNING, "clicked")
                runCatching { onRight?.invoke() }.onFailure { AppDiagnostics.failed("UI.${javaClass.simpleName}.softkey.right", it.message ?: it.javaClass.simpleName) }
            }
        }
    }

    protected fun showTabStrip(show: Boolean) {
        tabStripContainer.visibility = if (show) View.VISIBLE else View.GONE
    }
}
