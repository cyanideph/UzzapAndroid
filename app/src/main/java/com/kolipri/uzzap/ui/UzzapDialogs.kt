package com.kolipri.uzzap.ui

import android.content.Context
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import coil.load
import com.kolipri.uzzap.R

/** Shared modern dialog: rounded card, optional animated icon tile, themed text,
 *  optional input field, pill buttons. Theme-aware (light/dark/green/blue/black). */
object UzzapDialogs {
    val GREEN: Int = 0xFF8EEF04.toInt()
    private val GREEN_DARK_TEXT: Int = 0xFF16210A.toInt()

    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(c: Context, color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(c, radiusDp).toFloat() }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun loadAnimIcon(iv: ImageView, asset: String) {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(iv.context.assets, asset))
                iv.setImageDrawable(d)
                (d as? AnimatedImageDrawable)?.start()
                return
            } catch (e: Exception) { }
        }
        iv.load("file:///android_asset/$asset")
    }

    fun show(
        context: Context,
        iconAsset: String? = null,
        title: String,
        message: String? = null,
        inputHint: String? = null,
        prefilled: String = "",
        positive: String,
        onPositive: ((String) -> Unit)? = null,
        negative: String? = "Cancel",
        onNegative: (() -> Unit)? = null,
    ): AlertDialog {
        val surface = ThemeManager.resolve(context, R.attr.uzzapColorSurface)
        val text = ThemeManager.resolve(context, R.attr.uzzapColorText)
        val muted = ThemeManager.resolve(context, R.attr.uzzapColorMuted)
        val asset = iconAsset ?: "anim-icons/dialog_default.gif"

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 22), dp(context, 20), dp(context, 22), dp(context, 16))
            background = rounded(context, surface, 24)
        }

        if (iconAsset != null) {
            val tile = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(context, withAlpha(GREEN, 42), 16)
            }
            tile.addView(ImageView(context).apply { loadAnimIcon(this, iconAsset) },
                LinearLayout.LayoutParams(dp(context, 34), dp(context, 34)))
            card.addView(tile, LinearLayout.LayoutParams(dp(context, 54), dp(context, 54)))
        }

        card.addView(TextView(context).apply {
            this.text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(text)
            setPadding(0, dp(context, if (iconAsset != null) 14 else 2), 0, 0)
        })

        if (!message.isNullOrBlank()) {
            card.addView(TextView(context).apply {
                this.text = message
                textSize = 14f
                setTextColor(muted)
                setLineSpacing(dp(context, 2).toFloat(), 1.0f)
                setPadding(0, dp(context, 6), 0, 0)
            })
        }

        var input: EditText? = null
        if (inputHint != null) {
            input = EditText(context).apply {
                hint = inputHint
                setHintTextColor(muted)
                setTextColor(text)
                textSize = 14f
                setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
                background = rounded(context, withAlpha(muted, 28), 12)
                setText(prefilled)
            }
            card.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 14) })
        }

        val negBtn = TextView(context).apply {
            this.text = negative ?: "Cancel"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(text)
            background = rounded(context, withAlpha(muted, 32), 22)
        }
        val posBtn = TextView(context).apply {
            this.text = positive
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(GREEN_DARK_TEXT)
            background = rounded(context, GREEN, 22)
        }
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(0, dp(context, 44), 1f)
            lp.marginEnd = dp(context, 8)
            addView(negBtn, lp)
            addView(posBtn, LinearLayout.LayoutParams(0, dp(context, 44), 1f))
        }
        card.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 18) })

        val root = FrameLayout(context).apply {
            setPadding(dp(context, 26), dp(context, 30), dp(context, 26), dp(context, 30))
            addView(card, FrameLayout.LayoutParams(-1, -2))
        }

        val dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        negBtn.setOnClickListener { dialog.dismiss(); onNegative?.invoke() }
        posBtn.setOnClickListener { dialog.dismiss(); onPositive?.invoke(input?.text?.toString()?.trim() ?: "") }
        dialog.show()
        return dialog
    }
}
