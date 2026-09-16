package com.kolipri.uzzap.ui.util

import android.graphics.Color
import android.text.style.ForegroundColorSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UzzapColorCodeParserTest {
    @Test
    fun singleColorMarkupIsStrippedAndColored() {
        val result = UzzapColorCodeParser.parse("[c03 test]", Color.DKGRAY)
        assertEquals("test", result.toString())
        val spans = result.getSpans(0, result.length, ForegroundColorSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(Color.RED, spans[0].foregroundColor)
    }

    @Test
    fun inlineColorsCanSwitchWithoutClosingTag() {
        val result = UzzapColorCodeParser.parse("[c03 A [c04 I", Color.DKGRAY)
        assertEquals(" A I", result.toString())
        val spans = result.getSpans(0, result.length, ForegroundColorSpan::class.java)
        assertTrue(spans.any { it.foregroundColor == Color.RED })
        assertTrue(spans.any { it.foregroundColor == Color.BLUE })
    }

    @Test
    fun nicknameMarkupProducesVisibleNickname() {
        assertEquals("cY", UzzapColorCodeParser.plainText("[c02 cY]"))
    }

    @Test
    fun unknownColorCodeDoesNotCrash() {
        val result = UzzapColorCodeParser.parse("[c99 test]", Color.DKGRAY)
        assertEquals("test", result.toString())
    }
}
