package com.kolipri.uzzap.ui.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.core.content.ContextCompat
import com.kolipri.uzzap.R
import java.util.regex.Pattern

class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
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

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
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
        // Smile
        listOf(":)", ":-)", "(:", "(:-", "🙂", "😊", "☺️", "😀", "😃", "😄", "😸").forEach { put(it, R.drawable.d_emoticon_smile) }

        // Wink
        listOf(";)", ";-)", ";D", ";d", "😉", "😜").forEach { put(it, R.drawable.d_emoticon_wink) }

        // Sad
        listOf(":(", ":-(", "):", ")-:", "🙁", "☹️", "😞", "😔", "😟").forEach { put(it, R.drawable.d_emoticon_sad) }

        // Laugh
        listOf(":D", ":-D", ":d", ":-d", "xD", "XD", "😆", "😹", "🤣", "😂", "😁").forEach { put(it, R.drawable.d_emoticon_laugh) }

        // Tongue
        listOf(":p", ":-p", ":P", ":-P", "😛", "😋", "😝").forEach { put(it, R.drawable.d_emoticon_tongue) }

        // Love
        listOf("<3", "[love]", "[heart]", "❤️", "💖", "💕", "💗", "💓", "💘", "💌", "😍", "🥰").forEach { put(it, R.drawable.d_emoticon_love) }

        // Cry
        listOf(";(", ";-(", ":'(", ":'-(", "T_T", "T.T", "😢", "😭", "😿").forEach { put(it, R.drawable.d_emoticon_cry) }

        // Angry
        listOf(">:(", ">:-(", ":-@", ":@", "😡", "😠", "🤬").forEach { put(it, R.drawable.d_emoticon_angry) }

        // Surprise
        listOf(":o", ":-o", ":O", ":-O", "=o", "=O", "😮", "😲", "😯", "😱", "🤯").forEach { put(it, R.drawable.d_emoticon_surprise) }

        // Sick
        listOf(":s", ":-s", ":S", ":-S", "🤢", "🤮", "🤒", "🥴", "🤧").forEach { put(it, R.drawable.d_emoticon_sick) }

        // Clown
        listOf(":o)", ":-o)", "🤡").forEach { put(it, R.drawable.d_emoticon_clown) }

        // Party
        listOf("\\o/", "\\\\o/", "[party]", "🎉", "🥳", "🎊", "🍾", "🥂").forEach { put(it, R.drawable.d_emoticon_party) }

        // Drink
        listOf("[drink]", "[beer]", "🍺", "🍻", "🍹", "🍸", "🍷", "☕").forEach { put(it, R.drawable.d_emoticon_drink) }

        // Rose
        listOf("[rose]", "[flower]", "@}->--", "🌹", "🥀", "🌸", "💐").forEach { put(it, R.drawable.d_emoticon_rose) }

        // Kiss
        listOf("[kiss]", ":*", ":-*", "💋", "😘", "😽").forEach { put(it, R.drawable.d_kiss) }
    }

    private val EMOTICON_PATTERN: Pattern = Pattern.compile(
        CODE_TO_DRAWABLE.keys
            .sortedByDescending { it.length }
            .joinToString("|") { Pattern.quote(it) }
    )

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

    /**
     * Checks if the given text contains any recognizable Uzzap emoticon codes.
     */
    fun hasEmoticons(text: CharSequence?): Boolean {
        if (text.isNullOrEmpty()) return false
        return EMOTICON_PATTERN.matcher(text).find()
    }

    /**
     * Converts raw text into a CharSequence with authentic retro Uzzap emoticon images.
     */
    fun parse(context: Context, text: CharSequence?, textSizeSp: Float = 15f): CharSequence {
        if (text.isNullOrEmpty()) return ""
        val ssb = SpannableStringBuilder(text)
        applySpans(context, ssb, textSizeSp)
        return ssb
    }

    /**
     * Applies ImageSpans in-place to an existing Spannable (such as an EditText's Editable).
     */
    fun applySpans(context: Context, text: Spannable, textSizeSp: Float = 15f) {
        val oldSpans = text.getSpans(0, text.length, CenteredImageSpan::class.java)
        for (old in oldSpans) {
            text.removeSpan(old)
        }

        val density = context.resources.displayMetrics.scaledDensity
        val targetHeightPx = (textSizeSp * density * 1.25f).toInt().coerceAtLeast(18)

        val matcher = EMOTICON_PATTERN.matcher(text)
        while (matcher.find()) {
            val code = matcher.group()
            val resId = CODE_TO_DRAWABLE[code] ?: continue
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
