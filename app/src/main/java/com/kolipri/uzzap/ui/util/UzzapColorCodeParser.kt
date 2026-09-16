package com.kolipri.uzzap.ui.util

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/** Renders Uzzap [cNN] inline color controls. Raw markup remains stored in Supabase. */
object UzzapColorCodeParser {
    private val colors = mapOf(
        1 to Color.BLACK, 2 to Color.rgb(0, 150, 0), 3 to Color.RED, 4 to Color.BLUE,
        5 to Color.rgb(190, 170, 0), 6 to Color.rgb(255, 140, 0), 7 to Color.rgb(128, 0, 128),
        8 to Color.rgb(255, 105, 180), 9 to Color.GRAY, 10 to Color.WHITE, 11 to Color.CYAN,
        12 to Color.MAGENTA, 13 to Color.LIME, 14 to Color.rgb(212, 175, 55), 15 to Color.rgb(139, 69, 19),
        16 to Color.rgb(0, 0, 128), 17 to Color.rgb(135, 206, 235), 18 to Color.rgb(138, 43, 226),
        19 to Color.rgb(0, 128, 128), 20 to Color.rgb(128, 0, 0), 21 to Color.rgb(128, 128, 0),
        22 to Color.rgb(192, 192, 192), 23 to Color.rgb(255, 127, 80), 24 to Color.rgb(64, 224, 208),
        25 to Color.rgb(75, 0, 130), 26 to Color.rgb(220, 20, 60), 27 to Color.rgb(0, 100, 0),
        28 to Color.rgb(0, 0, 139), 29 to Color.rgb(255, 20, 147), 30 to Color.rgb(255, 69, 0)
    )

    fun colorFor(code: Int): Int? = colors[code]

    fun plainText(markup: String?): String = render(markup, null).first

    fun parse(markup: String?, defaultColor: Int = Color.DKGRAY): Spanned {
        val (plain, ranges) = render(markup, defaultColor)
        val result = SpannableString(plain)
        ranges.forEach { range ->
            result.setSpan(ForegroundColorSpan(range.color), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return result
    }

    private data class Range(val start: Int, val end: Int, val color: Int)
    private data class Token(val color: Int?, val nextIndex: Int)

    private fun render(markup: String?, defaultColor: Int?): Pair<String, List<Range>> {
        val source = markup.orEmpty()
        val text = StringBuilder(source.length)
        val ranges = mutableListOf<Range>()
        var activeColor = defaultColor
        var activeStart = 0
        var i = 0

        fun close(end: Int) {
            val color = activeColor
            if (color != null && activeStart < end) ranges += Range(activeStart, end, color)
        }

        while (i < source.length) {
            val token = tokenAt(source, i)
            if (token != null) {
                close(text.length)
                activeColor = token.color ?: defaultColor
                activeStart = text.length
                i = token.nextIndex
                continue
            }
            if (source[i] == ']' && activeColor != null) {
                close(text.length)
                activeColor = defaultColor
                activeStart = text.length
                i++
                continue
            }
            text.append(source[i])
            i++
        }
        close(text.length)
        return text.toString() to ranges
    }

    private fun tokenAt(value: String, index: Int): Token? {
        if (index + 4 > value.length || value[index] != '[' || !value[index + 1].equals('c', true)) return null
        val a = value[index + 2]
        val b = value[index + 3]
        if (!a.isDigit() || !b.isDigit()) return null
        val code = (a - '0') * 10 + (b - '0')
        var next = index + 4
        if (next < value.length && value[next] == ']') next++
        return Token(colors[code], next)
    }
}
