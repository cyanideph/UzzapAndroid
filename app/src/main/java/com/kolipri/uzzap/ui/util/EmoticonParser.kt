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

object EmoticonParser {
    private val CODE_TO_DRAWABLE: Map<String, Int> = buildMap {
        listOf(":)", ":-)", "(:", "(:-", "🙂", "😊", "☺️", "😀", "😃", "😄", "😸").forEach { put(it, R.drawable.d_emoticon_smile) }
        listOf(";)", ";-)", ";D", ";d", "😉", "😜").forEach { put(it, R.drawable.d_emoticon_wink) }
        listOf(":(", ":-(", "):", ")-:", "🙁", "☹️", "😞", "😔", "😟").forEach { put(it, R.drawable.d_emoticon_sad) }
        listOf(":D", ":-D", ":d", ":-d", "xD", "XD", "😆", "😹", "🤣", "😂", "😁").forEach { put(it, R.drawable.d_emoticon_laugh) }
        listOf(":p", ":-p", ":P", ":-P", "😛", "😋", "😝").forEach { put(it, R.drawable.d_emoticon_tongue) }
        listOf("<3", "[love]", "[heart]", "❤️", "💖", "💕", "💗", "💓", "💘", "💌", "😍", "🥰").forEach { put(it, R.drawable.d_emoticon_love) }
        listOf(";(", ";-(", ":'(", ":'-(", "T_T", "T.T", "😢", "😭", "😿").forEach { put(it, R.drawable.d_emoticon_cry) }
        listOf(">:(", ">:-(", ":-@", ":@", "😡", "😠", "🤬").forEach { put(it, R.drawable.d_emoticon_angry) }
        listOf(":o", ":-o", ":O", ":-O", "=o", "=O", "😮", "😲", "😯", "😱", "🤯").forEach { put(it, R.drawable.d_emoticon_surprise) }
        listOf(":s", ":-s", ":S", ":-S", "🤢", "🤮", "🤒", "🥴", "🤧").forEach { put(it, R.drawable.d_emoticon_sick) }
        listOf(":o)", ":-o)", "🤡").forEach { put(it, R.drawable.d_emoticon_clown) }
        listOf("\\o/", "\\\\o/", "[party]", "🎉", "🥳", "🎊", "🍾", "🥂").forEach { put(it, R.drawable.d_emoticon_party) }
        listOf("[drink]", "[beer]", "🍺", "🍻", "🍹", "🍸", "🍷", "☕").forEach { put(it, R.drawable.d_emoticon_drink) }
        listOf("[rose]", "[flower]", "@}->--", "🌹", "🥀", "🌸", "💐").forEach { put(it, R.drawable.d_emoticon_rose) }
        listOf("[kiss]", ":*", ":-*", "💋", "😘", "😽").forEach { put(it, R.drawable.d_kiss) }

        // Exact legacy Uzzap text-code mapping from the original Java client.
        // These codes are what UzzapBot stores in room_messages; render the
        // complete code as one image so punctuation is never left visible.
        put(":e", R.drawable.d_emoticon_e)
        put(">|", R.drawable.d_emoticon_angry)
        put(":>", R.drawable.d_emoticon_tongue)
        put(":DD", R.drawable.d_emoticon_11)
        put("o/", R.drawable.d_emoticon_12)
        put(":Oo", R.drawable.d_emoticon_13)
        put(">,", R.drawable.d_emoticon_14)
        put(":|", R.drawable.d_emoticon_15)
        put(":B,", R.drawable.d_emoticon_16)
        put(":OOo", R.drawable.d_emoticon_17)
        put(":Zz.", R.drawable.d_emoticon_18)
        put("O:)", R.drawable.d_emoticon_19)
        put("))(", R.drawable.d_emoticon_20)
        put(">><)", R.drawable.d_emoticon_47)
        put("<:D", R.drawable.d_emoticon_clown)
        put("(cU)", R.drawable.d_emoticon_drink)
        put("<:)", R.drawable.d_emoticon_party)
        put("(+)", R.drawable.d_emoticon_sick)
        put("@};-", R.drawable.d_emoticon_rose)

        // Keep numbered markers emitted by the picker/backward-compatible data.
        for (code in listOf(11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 47)) {
            put("[emoticon-$code]", when (code) {
                11 -> R.drawable.d_emoticon_11
                12 -> R.drawable.d_emoticon_12
                13 -> R.drawable.d_emoticon_13
                14 -> R.drawable.d_emoticon_14
                15 -> R.drawable.d_emoticon_15
                16 -> R.drawable.d_emoticon_16
                17 -> R.drawable.d_emoticon_17
                18 -> R.drawable.d_emoticon_18
                19 -> R.drawable.d_emoticon_19
                20 -> R.drawable.d_emoticon_20
                47 -> R.drawable.d_emoticon_47
                else -> error("Unsupported numbered emoticon: $code")
            })
        }

        put("[head-buntis]", R.drawable.d_head_buntis)
        put("[head-deckard]", R.drawable.d_head_deckard)
        put("[head-jp]", R.drawable.d_head_jp)
        put("[head-kool]", R.drawable.d_head_kool)
        put("[head-lupz]", R.drawable.d_head_lupz)
        put("[head-requiem]", R.drawable.d_head_requiem)
        put("[head-root]", R.drawable.d_head_root)
        put("[head-saiyan]", R.drawable.d_head_saiyan)
        put("[head-valkyrie]", R.drawable.d_head_valkyrie)
    }

    private val EMOTICON_PATTERN: Pattern = Pattern.compile(
        CODE_TO_DRAWABLE.keys.sortedByDescending { it.length }.joinToString("|") { Pattern.quote(it) }
    )

    private fun getSizedDrawable(context: Context, resId: Int, targetHeightPx: Int): Drawable? {
        val original = ContextCompat.getDrawable(context, resId) ?: return null
        val d = original.constantState?.newDrawable(context.resources)?.mutate() ?: original.mutate()
        val widthPx = if (d.intrinsicHeight > 0) (targetHeightPx * d.intrinsicWidth) / d.intrinsicHeight else targetHeightPx
        d.setBounds(0, 0, widthPx, targetHeightPx)
        return d
    }

    fun hasEmoticons(text: CharSequence?): Boolean = !text.isNullOrEmpty() && EMOTICON_PATTERN.matcher(text).find()

    fun parse(context: Context, text: CharSequence?, textSizeSp: Float = 15f): CharSequence {
        if (text.isNullOrEmpty()) return ""
        val ssb = SpannableStringBuilder(text)
        applySpans(context, ssb, textSizeSp)
        return ssb
    }

    fun applySpans(context: Context, text: Spannable, textSizeSp: Float = 15f) {
        val oldSpans = text.getSpans(0, text.length, CenteredImageSpan::class.java)
        for (old in oldSpans) text.removeSpan(old)
        val density = context.resources.displayMetrics.scaledDensity
        val targetHeightPx = (textSizeSp * density * 1.25f).toInt().coerceAtLeast(18)
        val matcher = EMOTICON_PATTERN.matcher(text)
        while (matcher.find()) {
            val resId = CODE_TO_DRAWABLE[matcher.group()] ?: continue
            val drawable = getSizedDrawable(context, resId, targetHeightPx) ?: continue
            text.setSpan(CenteredImageSpan(drawable), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
