package com.kolipri.uzzap.ui.util

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/** Renders Uzzap [cNN] inline color controls. Raw markup remains stored in Supabase. */
object UzzapColorCodeParser {
    private val colors = mapOf(
        1 to 0xFF000000.toInt(), 2 to 0xFF009600.toInt(), 3 to 0xFFFF0000.toInt(), 4 to 0xFF0000FF.toInt(),
        5 to 0xFFBEAA00.toInt(), 6 to 0xFFFF8C00.toInt(), 7 to 0xFF800080.toInt(), 8 to 0xFFFF69B4.toInt(),
        9 to 0xFF808080.toInt(), 10 to 0xFFFFFFFF.toInt(), 11 to 0xFF00FFFF.toInt(), 12 to 0xFFFF00FF.toInt(),
        13 to 0xFF00FF00.toInt(), 14 to 0xFFD4AF37.toInt(), 15 to 0xFF8B4513.toInt(), 16 to 0xFF000080.toInt(),
        17 to 0xFF87CEEB.toInt(), 18 to 0xFF8A2BE2.toInt(), 19 to 0xFF008080.toInt(), 20 to 0xFF800000.toInt(),
        21 to 0xFF808000.toInt(), 22 to 0xFFC0C0C0.toInt(), 23 to 0xFFFF7F50.toInt(), 24 to 0xFF40E0D0.toInt(),
        25 to 0xFF4B0082.toInt(), 26 to 0xFFDC143C.toInt(), 27 to 0xFF006400.toInt(), 28 to 0xFF00008B.toInt(),
        29 to 0xFFFF1493.toInt(), 30 to 0xFFFF4500.toInt()
    )

    data class ColorRange(val start: Int, val end: Int, val color: Int)
    data class ParsedMarkup(val text: String, val ranges: List<ColorRange>)

    fun colorFor(code: Int): Int? = colors[code]

    /** Pure Kotlin parser; safe to execute in ordinary JVM unit tests. */
    fun parseMarkup(markup: String?, defaultColor: Int? = null): ParsedMarkup {
        val source = markup.orEmpty()
        val text = StringBuilder(source.length)
        val ranges = mutableListOf<ColorRange>()
        var activeColor = defaultColor
        var activeStart = 0
        var i = 0

        fun close(end: Int) {
            val color = activeColor
            if (color != null && activeStart < end) ranges += ColorRange(activeStart, end, color)
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
        return ParsedMarkup(text.toString(), ranges)
    }

    fun plainText(markup: String?): String = parseMarkup(markup).text

    fun parse(markup: String?, defaultColor: Int = Color.DKGRAY): Spanned {
        val parsed = parseMarkup(markup, defaultColor)
        val result = SpannableString(parsed.text)
        parsed.ranges.forEach { range ->
            result.setSpan(ForegroundColorSpan(range.color), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return result
    }

    private data class Token(val color: Int?, val nextIndex: Int)

    private fun tokenAt(value: String, index: Int): Token? {
        if (index + 4 > value.length || value[index] != '[' || !value[index + 1].equals('c', true)) return null
        val a = value[index + 2]
        val b = value[index + 3]
        if (!a.isDigit() || !b.isDigit()) return null
        val code = (a - '0') * 10 + (b - '0')
        var next = index + 4
        if (next < value.length && value[next] == ']') next++
        // A single space immediately after a color token is markup syntax.
        // Two spaces intentionally preserve one visible space.
        if (next < value.length && value[next] == ' ') next++
        return Token(colors[code], next)
    }
}
