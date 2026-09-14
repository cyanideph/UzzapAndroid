package com.kolipri.uzzap.ui

import android.graphics.Color
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

/**
 * Every screen in the clone shares the same "phone chrome": a black gradient
 * title bar up top (with an optional green connection ball), a white content
 * area in the middle, and a two button softkey bar at the bottom - exactly
 * like the reference J2ME screenshots.
 */
abstract class BaseChromeActivity : AppCompatActivity() {

    protected lateinit var contentFrame: FrameLayout
    protected lateinit var tvTitle: TextView
    protected lateinit var ivStatusBall: ImageView
    protected lateinit var btnLeft: TextView
    protected lateinit var btnRight: TextView
    protected lateinit var tabStripContainer: LinearLayout

    @LayoutRes
    protected open fun contentLayoutRes(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        ViewCompat.setOnApplyWindowInsetsListener(chromeRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            statusBarSpacer.layoutParams = statusBarSpacer.layoutParams.apply { height = bars.top }
            navBarSpacer.layoutParams = navBarSpacer.layoutParams.apply { height = bars.bottom }
            statusBarSpacer.requestLayout()
            navBarSpacer.requestLayout()

            // Chat/edit screens must use the area above the IME. The legacy
            // softkey bar would otherwise sit underneath the keyboard and
            // reduce the usable area for the message composer.
            softkeyBar.visibility = if (imeVisible) View.GONE else View.VISIBLE
            contentFrame.updatePadding(bottom = if (imeVisible) ime.bottom else 0)

            insets
        }
        ViewCompat.requestApplyInsets(chromeRoot)

        contentLayoutRes()?.let { res ->
            LayoutInflater.from(this).inflate(res, contentFrame, true)
        }
    }

    fun setScreenTitle(title: String) {
        tvTitle.text = title
    }

    fun showConnectedBall(show: Boolean) {
        ivStatusBall.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun setSoftkeys(
        leftText: String?,
        rightText: String?,
        onLeft: (() -> Unit)? = null,
        onRight: (() -> Unit)? = null
    ) {
        if (leftText == null) {
            btnLeft.visibility = View.INVISIBLE
        } else {
            btnLeft.visibility = View.VISIBLE
            btnLeft.text = leftText
            btnLeft.setOnClickListener { onLeft?.invoke() }
        }
        if (rightText == null) {
            btnRight.visibility = View.INVISIBLE
        } else {
            btnRight.visibility = View.VISIBLE
            btnRight.text = rightText
            btnRight.setOnClickListener { onRight?.invoke() }
        }
    }

    protected fun showTabStrip(show: Boolean) {
        tabStripContainer.visibility = if (show) View.VISIBLE else View.GONE
    }
}
