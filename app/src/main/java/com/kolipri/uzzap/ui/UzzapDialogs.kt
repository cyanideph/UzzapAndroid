package com.kolipri.uzzap.ui

import android.content.Context
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import coil.load
import com.kolipri.uzzap.R

/** Shared modern dialog: rounded card, animated icon tile, themed text,
 *  optional input fields, pill buttons. Theme-aware (light/dark/green/blue/black). */
object UzzapDialogs {
    val GREEN: Int = 0xFF8EEF04.toInt()
    private val GREEN_DARK_TEXT: Int = 0xFF16210A.toInt()

    class FieldSpec(val hint: String, val prefilled: String = "", val password: Boolean = false, val email: Boolean = false)

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

    /** Core builder: card + icon tile + title + message + optional content view + pill buttons.
     *  onPositive receives the dialog and decides when to dismiss. */
    private fun base(
        context: Context,
        iconAsset: String?,
        title: String,
        message: String?,
        content: View?,
        positive: String?,
        negative: String?,
        onPositive: ((AlertDialog) -> Unit)?,
        onNegative: (() -> Unit)?
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

        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(context, withAlpha(GREEN, 42), 16)
        }
        tile.addView(ImageView(context).apply { loadAnimIcon(this, asset) },
            LinearLayout.LayoutParams(dp(context, 34), dp(context, 34)))
        card.addView(tile, LinearLayout.LayoutParams(dp(context, 54), dp(context, 54)))

        card.addView(TextView(context).apply {
            this.text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(text)
            setPadding(0, dp(context, 14), 0, 0)
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

        if (content != null) {
            card.addView(content, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 12) })
        }

        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        if (negative != null) {
            val negBtn = TextView(context).apply {
                this.text = negative
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(text)
                background = rounded(context, withAlpha(muted, 32), 22)
            }
            val lp = LinearLayout.LayoutParams(0, dp(context, 44), 1f)
            lp.marginEnd = dp(context, 8)
            buttons.addView(negBtn, lp)
        }
        if (positive != null) {
            val posBtn = TextView(context).apply {
                this.text = positive
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(GREEN_DARK_TEXT)
                background = rounded(context, GREEN, 22)
            }
            buttons.addView(posBtn, LinearLayout.LayoutParams(0, dp(context, 44), if (negative != null) 1f else 2f))
        }
        card.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 18) })

        val root = FrameLayout(context).apply {
            setPadding(dp(context, 26), dp(context, 30), dp(context, 26), dp(context, 30))
            addView(card, FrameLayout.LayoutParams(-1, -2))
        }

        val dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }
        if (buttons.childCount >= 1 && negative != null) buttons.getChildAt(0).setOnClickListener { dialog.dismiss(); onNegative?.invoke() }
        if (positive != null) buttons.getChildAt(buttons.childCount - 1).setOnClickListener { onPositive?.invoke(dialog) }
        dialog.show()
        return dialog
    }

    /** Simple message/confirm dialog with optional single input field. */
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
        var input: EditText? = null
        var content: View? = null
        if (inputHint != null) {
            val textColor = ThemeManager.resolve(context, R.attr.uzzapColorText)
            val muted = ThemeManager.resolve(context, R.attr.uzzapColorMuted)
            input = EditText(context).apply {
                hint = inputHint
                setHintTextColor(muted)
                setTextColor(textColor)
                textSize = 14f
                setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
                background = rounded(context, withAlpha(muted, 28), 12)
                setText(prefilled)
            }
            content = input
        }
        return base(context, iconAsset, title, message, content, positive, negative,
            onPositive = { d -> d.dismiss(); onPositive?.invoke(input?.text?.toString()?.trim() ?: "") }, onNegative)
    }

    /** Multi-field form dialog. onPositive returns true to dismiss, false to keep open (validation errors). */
    fun form(
        context: Context,
        iconAsset: String? = null,
        title: String,
        message: String? = null,
        fields: List<FieldSpec>,
        positive: String,
        onPositive: (List<EditText>) -> Boolean,
        negative: String? = "Cancel",
    ): AlertDialog {
        val textColor = ThemeManager.resolve(context, R.attr.uzzapColorText)
        val muted = ThemeManager.resolve(context, R.attr.uzzapColorMuted)
        val edits = mutableListOf<EditText>()
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fields.forEach { spec ->
            val ed = EditText(context).apply {
                hint = spec.hint
                setHintTextColor(muted)
                setTextColor(textColor)
                textSize = 14f
                setSingleLine(true)
                if (spec.password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else if (spec.email) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
                background = rounded(context, withAlpha(muted, 28), 12)
                setText(spec.prefilled)
            }
            edits.add(ed)
            box.addView(ed, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 8) })
        }
        return base(context, iconAsset, title, message, box, positive, negative,
            onPositive = { d -> if (onPositive(edits)) d.dismiss() },
            onNegative = null)
    }

    /** Item-picker dialog: tappable list rows below title/message. */
    fun items(
        context: Context,
        iconAsset: String? = null,
        title: String,
        message: String? = null,
        items: Array<String>,
        onPick: (Int) -> Unit,
        negative: String? = "Cancel"
    ): AlertDialog {
        val textColor = ThemeManager.resolve(context, R.attr.uzzapColorText)
        val muted = ThemeManager.resolve(context, R.attr.uzzapColorMuted)
        var dlg: AlertDialog? = null
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        items.forEachIndexed { i, label ->
            if (i > 0) { val div = View(context); div.setBackgroundColor(withAlpha(muted, 40)); box.addView(div, LinearLayout.LayoutParams(-1, dp(context, 1))) }
            val row = TextView(context).apply {
                text = label
                textSize = 15f
                setTextColor(textColor)
                setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
                setOnClickListener { dlg?.dismiss(); onPick(i) }
            }
            box.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        dlg = base(context, iconAsset, title, message, box, positive = null, negative = negative,
            onPositive = null, onNegative = null)
        return dlg
    }

    /** Dialog with fully custom content view. */
    fun custom(
        context: Context,
        iconAsset: String? = null,
        title: String,
        content: View,
        positive: String,
        onPositive: (() -> Unit)? = null,
    ): AlertDialog = base(context, iconAsset, title, null, content, positive, negative = null,
        onPositive = { d -> d.dismiss(); onPositive?.invoke() }, onNegative = null)
}
