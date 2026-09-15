package com.kolipri.uzzap.ui

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.annotation.LayoutRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.LegacyThemeManager

/** Shared Uzzap phone chrome with the legacy-Java theme engine applied. */
abstract class BaseChromeActivity : AppCompatActivity() {

    protected lateinit var contentFrame: FrameLayout
    protected lateinit var tvTitle: TextView
    protected lateinit var ivStatusBall: ImageView
    protected lateinit var btnLeft: TextView
    protected lateinit var btnRight: TextView
    protected lateinit var tabStripContainer: LinearLayout

    protected val activeTheme: LegacyThemeManager.Theme by lazy { LegacyThemeManager.current(this) }

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    protected var isNetworkConnected: Boolean = true
        private set

    @LayoutRes
    protected open fun contentLayoutRes(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = activeTheme.primary
        window.navigationBarColor = activeTheme.primary
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = activeTheme.onPrimary == Color.BLACK
            isAppearanceLightNavigationBars = activeTheme.onPrimary == Color.BLACK
        }

        setContentView(R.layout.activity_chrome)

        val chromeRoot = findViewById<View>(R.id.chrome_root)
        LegacyThemeManager.applyChrome(chromeRoot, activeTheme)
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
        val activeNetwork = connectivityManager?.activeNetworkInfo
        val initialConnected = activeNetwork?.isConnectedOrConnecting == true
        setConnectionStatus(initialConnected)

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { setConnectionStatus(true); onNetworkRestored() }
            }
            override fun onLost(network: Network) {
                runOnUiThread { setConnectionStatus(false); onNetworkLost() }
            }
        }
        runCatching { connectivityManager?.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback!!) }

        ViewCompat.setOnApplyWindowInsetsListener(chromeRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            statusBarSpacer.layoutParams = statusBarSpacer.layoutParams.apply { height = bars.top }
            navBarSpacer.layoutParams = navBarSpacer.layoutParams.apply { height = if (imeVisible) 0 else bars.bottom }
            statusBarSpacer.requestLayout(); navBarSpacer.requestLayout()
            softkeyBar.visibility = if (imeVisible) View.GONE else View.VISIBLE
            contentFrame.updatePadding(bottom = if (imeVisible) ime.bottom else 0)
            insets
        }
        ViewCompat.requestApplyInsets(chromeRoot)
        contentLayoutRes()?.let { res -> LayoutInflater.from(this).inflate(res, contentFrame, true) }
    }

    fun setScreenTitle(title: String) { tvTitle.text = title }
    fun showConnectedBall(show: Boolean) { ivStatusBall.visibility = if (show) View.VISIBLE else View.GONE }
    fun setConnectionStatus(connected: Boolean) {
        isNetworkConnected = connected
        if (::ivStatusBall.isInitialized) ivStatusBall.setImageResource(if (connected) R.drawable.d_online else R.drawable.d_disconnected)
    }
    protected open fun onNetworkRestored() {}
    protected open fun onNetworkLost() {}

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let { runCatching { connectivityManager?.unregisterNetworkCallback(it) } }
    }

    fun setSoftkeys(leftText: String?, rightText: String?, onLeft: (() -> Unit)? = null, onRight: (() -> Unit)? = null) {
        if (leftText == null) btnLeft.visibility = View.INVISIBLE else {
            btnLeft.visibility = View.VISIBLE; btnLeft.text = leftText; btnLeft.setOnClickListener { onLeft?.invoke() }
        }
        if (rightText == null) btnRight.visibility = View.INVISIBLE else {
            btnRight.visibility = View.VISIBLE; btnRight.text = rightText; btnRight.setOnClickListener { onRight?.invoke() }
        }
    }

    protected fun showTabStrip(show: Boolean) { tabStripContainer.visibility = if (show) View.VISIBLE else View.GONE }
}
