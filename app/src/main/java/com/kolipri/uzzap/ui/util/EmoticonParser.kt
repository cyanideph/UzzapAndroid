package com.kolipri.uzzap.ui.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.core.content.ContextCompat
import com.kolipri.uzzap.R
import java.util.regex.Pattern

class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val rect = drawable.bounds
        if (fm != null) {
            val fontMetrics = paint.fontMetricsInt
            val lineHeight = fontMetrics.descent - fontMetrics.ascent
            if (rect.height() > lineHeight) {
                val diff = (rect.height() - lineHeight) / 2
                fm.ascent = fontMetrics.ascent - diff
                fm.top = fontMetrics.top - diff
                fm.descent = fontMetrics.descent + diff
                fm.bottom = fontMetrics.bottom + diff
            }
        }
        return rect.right
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val b = drawable
        canvas.save()
        val fontMetrics = paint.fontMetricsInt
        val transY = y + fontMetrics.ascent + (fontMetrics.descent - fontMetrics.ascent - b.bounds.height()) / 2
        canvas.translate(x, transY.toFloat())
        b.draw(canvas)
        canvas.restore()
    }
}

/**
 * Single source of truth for Uzzap emoticons on Android.
 *
 * The network/database layer stores only the text code. Android owns the
 * code -> drawable mapping and is the only layer that decides how a code
 * is rendered. The picker consumes this same registry, so it cannot drift
 * from the renderer.
 */
object EmoticonParser {
    data class Definition(
        val code: String,
        val drawableRes: Int,
        val label: String = code
    )

    val definitions: List<Definition> = listOf(
        Definition(":)", R.drawable.d_emoticon_smile, "Smile"),
        Definition(":(", R.drawable.d_emoticon_sad, "Sad"),
        Definition(";)", R.drawable.d_emoticon_wink, "Wink"),
        Definition(":p", R.drawable.d_emoticon_tongue, "Tongue"),
        Definition(":D", R.drawable.d_emoticon_laugh, "Laugh"),
        Definition("<3", R.drawable.d_emoticon_love, "Love"),
        Definition(";(", R.drawable.d_emoticon_cry, "Cry"),
        Definition(">:(", R.drawable.d_emoticon_angry, "Angry"),
        Definition(":o", R.drawable.d_emoticon_surprise, "Surprise"),
        Definition(":s", R.drawable.d_emoticon_sick, "Sick"),
        Definition(":o)", R.drawable.d_emoticon_clown, "Clown"),
        Definition("\\o/", R.drawable.d_emoticon_party, "Party"),
        Definition("[drink]", R.drawable.d_emoticon_drink, "Drink"),
        Definition("[rose]", R.drawable.d_emoticon_rose, "Rose"),
        Definition("[kiss]", R.drawable.d_kiss, "Kiss"),

        // Exact legacy Uzzap codes from the original client.
        Definition(":e", R.drawable.d_emoticon_e),
        Definition(">|", R.drawable.d_emoticon_angry),
        Definition(":>", R.drawable.d_emoticon_tongue),
        Definition(":DD", R.drawable.d_emoticon_11),
        Definition("o/", R.drawable.d_emoticon_12),
        Definition(":Oo", R.drawable.d_emoticon_13),
        Definition(">,", R.drawable.d_emoticon_14),
        Definition(":|", R.drawable.d_emoticon_15),
        Definition(":B,", R.drawable.d_emoticon_16),
        Definition(":OOo", R.drawable.d_emoticon_17),
        Definition(":Zz.", R.drawable.d_emoticon_18),
        Definition("O:)", R.drawable.d_emoticon_19),
        Definition("))(", R.drawable.d_emoticon_20),
        Definition(">><)", R.drawable.d_emoticon_47),
        Definition("<:D", R.drawable.d_emoticon_clown),
        Definition("(cU)", R.drawable.d_emoticon_drink),
        Definition("<:)", R.drawable.d_emoticon_party),
        Definition("(+)", R.drawable.d_emoticon_sick),
        Definition("@};-", R.drawable.d_emoticon_rose),

        // Backward-compatible markers used by earlier app versions.
        Definition("[emoticon-11]", R.drawable.d_emoticon_11),
        Definition("[emoticon-12]", R.drawable.d_emoticon_12),
        Definition("[emoticon-13]", R.drawable.d_emoticon_13),
        Definition("[emoticon-14]", R.drawable.d_emoticon_14),
        Definition("[emoticon-15]", R.drawable.d_emoticon_15),
        Definition("[emoticon-16]", R.drawable.d_emoticon_16),
        Definition("[emoticon-17]", R.drawable.d_emoticon_17),
        Definition("[emoticon-18]", R.drawable.d_emoticon_18),
        Definition("[emoticon-19]", R.drawable.d_emoticon_19),
        Definition("[emoticon-20]", R.drawable.d_emoticon_20),
        Definition("[emoticon-47]", R.drawable.d_emoticon_47),

        Definition("[head-buntis]", R.drawable.d_head_buntis),
        Definition("[head-deckard]", R.drawable.d_head_deckard),
        Definition("[head-jp]", R.drawable.d_head_jp),
        Definition("[head-kool]", R.drawable.d_head_kool),
        Definition("[head-lupz]", R.drawable.d_head_lupz),
        Definition("[head-requiem]", R.drawable.d_head_requiem),
        Definition("[head-root]", R.drawable.d_head_root),
        Definition("[head-saiyan]", R.drawable.d_head_saiyan),
        Definition("[head-valkyrie]", R.drawable.d_head_valkyrie)
    )

    private val codeToDrawable: Map<String, Int> =
        definitions.associate { it.code to it.drawableRes }

    private val emoticonPattern: Pattern = Pattern.compile(
        definitions.map { it.code }
            .sortedByDescending { it.length }
            .joinToString("|") { Pattern.quote(it) }
    )

    fun drawableFor(code: String): Int? = codeToDrawable[code]

    fun hasEmoticons(text: CharSequence?): Boolean =
        !text.isNullOrEmpty() && emoticonPattern.matcher(text).find()

    fun parse(context: Context, text: CharSequence?, textSizeSp: Float = 15f): CharSequence {
        if (text.isNullOrEmpty()) return ""
        val ssb = SpannableStringBuilder(text)
        applySpans(context, ssb, textSizeSp)
        return ssb
    }

    private fun getSizedDrawable(context: Context, resId: Int, targetHeightPx: Int): Drawable? {
        val original = ContextCompat.getDrawable(context, resId) ?: return null
        val d = original.constantState?.newDrawable(context.resources)?.mutate() ?: original.mutate()
        val widthPx = if (d.intrinsicHeight > 0) {
            (targetHeightPx * d.intrinsicWidth) / d.intrinsicHeight
        } else {
            targetHeightPx
        }
        d.setBounds(0, 0, widthPx, targetHeightPx)
        return d
    }

    fun applySpans(context: Context, text: Spannable, textSizeSp: Float = 15f) {
        val oldSpans = text.getSpans(0, text.length, CenteredImageSpan::class.java)
        for (old in oldSpans) text.removeSpan(old)

        val density = context.resources.displayMetrics.scaledDensity
        val targetHeightPx = (textSizeSp * density * 1.25f).toInt().coerceAtLeast(18)
        val matcher = emoticonPattern.matcher(text)

        while (matcher.find()) {
            val resId = codeToDrawable[matcher.group()] ?: continue
            val drawable = getSizedDrawable(context, resId, targetHeightPx) ?: continue
            text.setSpan(
                CenteredImageSpan(drawable),
                matcher.start(),
                matcher.end(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}
