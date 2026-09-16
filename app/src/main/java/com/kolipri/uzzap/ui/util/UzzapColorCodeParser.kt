package com.kolipri.uzzap.ui.util

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/**
 * Renders Uzzap inline color controls such as [c03 red] and [c03 A [c04 I].
 * The raw markup stays in Supabase; only Android rendering is styled.
 */
object UzzapColorCodeParser {
    private val colors = mapOf(
        1 to Color.BLACK,
        2 to Color.rgb(0, 150, 0),
        3 to Color.RED,
        4 to Color.BLUE,
        5 to Color.rgb(190, 170, 0),
        6 to Color.rgb(255, 140, 0),
        7 to Color.rgb(128, 0, 128),
        8 to Color.rgb(255, 105, 180),
        9 to Color.GRAY,
        10 to Color.WHITE,
        11 to Color.CYAN,
        12 to Color.MAGENTA,
        13 to Color.LIME,
        14 to Color.rgb(212, 175, 55),
        15 to Color.rgb(139, 69, 19),
        16 to Color.rgb(0, 0, 128),
        17 to Color.rgb(135, 206, 235),
        18 to Color.rgb(138, 43, 226),
        19 to Color.rgb(0, 128, 128),
        20 to Color.rgb(128, 0, 0),
        21 to Color.rgb(128, 128, 0),
        22 to Color.rgb(192, 192, 192),
        23 to Color.rgb(255, 127, 80),
        24 to Color.rgb(64, 224, 208),
        25 to Color.rgb(75, 0, 130),
        26 to Color.rgb(220, 20, 60),
        27 to Color.rgb(0, 100, 0),
        28 to Color.rgb(0, 0, 139),
        29 to Color.rgb(255, 20, 147),
        30 to Color.rgb(255, 69, 0)
    )

    fun colorFor(code: Int): Int? = colors[code]

    fun plainText(markup: String?): String {
        if (markup.isNullOrEmpty()) return ""
        val out = StringBuilder(markup.length)
        var i = 0
        while (i < markup.length) {
            val token = tokenAt(markup, i)
            if (token != null) {
                i = token.nextIndex
                continue
            }
            if (markup[i] == ']' && hasActiveTokenBefore(markup, i)) {
                i++
                continue
            }
            out.append(markup[i])
            i++
        }
        return out.toString()
    }

    fun parse(markup: String?, defaultColor: Int = Color.DKGRAY): Spanned {
        val source = markup.orEmpty()
        val text = StringBuilder(source.length)
        val spans = mutableListOf<Pair<Int, Int>>()
        var activeColor = defaultColor
        var activeStart = 0
        var i = 0

        fun closeActive(end: Int) {
            if (activeStart < end) spans += activeStart to end
        }

        while (i < source.length) {
            val token = tokenAt(source, i)
            if (token != null) {
                closeActive(text.length)
                activeColor = token.color ?: defaultColor
                activeStart = text.length
                i = token.nextIndex
                continue
            }
            if (source[i] == ']' && activeStart < text.length) {
                closeActive(text.length)
                activeColor = defaultColor
                activeStart = text.length
                i++
                continue
            }
            text.append(source[i])
            i++
        }
        closeActive(text.length)

        val result = SpannableString(text.toString())
        var cursor = 0
        for ((start, end) in spans) {
            val segment = text.substring(start, end)
            val code = findColorAtSegment(source, segment, cursor)
            result.setSpan(ForegroundColorSpan(code ?: defaultColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            cursor = end
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
        return Token(colors[code], next)
    }

    private fun hasActiveTokenBefore(value: String, index: Int): Boolean =
        value.substring(0, index).lastIndexOf("[c", ignoreCase = true) >= 0

    private fun findColorAtSegment(source: String, segment: String, fallback: Int): Int? {
        if (segment.isEmpty()) return fallback
        val search = source.indexOf(segment)
        if (search < 0) return fallback
        val prefix = source.substring(0, search)
        val match = Regex("\\[c(\\d{2})", RegexOption.IGNORE_CASE).findAll(prefix).lastOrNull() ?: return fallback
        return colors[match.groupValues[1].toIntOrNull() ?: return fallback] ?: fallback
    }
}
