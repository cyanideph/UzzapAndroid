package com.kolipri.uzzap.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.kolipri.uzzap.R
import java.io.BufferedReader
import java.io.InputStreamReader

class SimpleTextActivity : BaseChromeActivity() {

    override fun contentLayoutRes() = R.layout.content_simple_text

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.menu_help)
        val assetName = intent.getStringExtra(EXTRA_ASSET)

        setScreenTitle(title)
        showConnectedBall(true)
        setSoftkeys(null, getString(R.string.softkey_close), onRight = { finish() })

        val body = findViewById<TextView>(R.id.tv_body)
        body.text = if (assetName != null) readAsset(assetName) else (intent.getStringExtra(EXTRA_BODY) ?: "")
    }

    private fun readAsset(name: String): String = try {
        BufferedReader(InputStreamReader(assets.open(name))).use { it.readText() }
    } catch (e: Exception) {
        "Content unavailable."
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_ASSET = "extra_asset"
        private const val EXTRA_BODY = "extra_body"

        fun intentForAsset(context: Context, title: String, assetName: String): Intent =
            Intent(context, SimpleTextActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ASSET, assetName)

        fun intentForText(context: Context, title: String, body: String): Intent =
            Intent(context, SimpleTextActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_BODY, body)
    }
}
