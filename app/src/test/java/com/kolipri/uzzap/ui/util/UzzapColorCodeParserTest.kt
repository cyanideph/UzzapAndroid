package com.kolipri.uzzap.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UzzapColorCodeParserTest {
    @Test
    fun singleColorMarkupIsStrippedAndColored() {
        val result = UzzapColorCodeParser.parseMarkup("[c03 test]")
        assertEquals("test", result.text)
        assertEquals(1, result.ranges.size)
        assertEquals(UzzapColorCodeParser.colorFor(3), result.ranges[0].color)
    }

    @Test
    fun inlineColorsCanSwitchWithoutClosingTag() {
        val result = UzzapColorCodeParser.parseMarkup("[c03 A [c04 I")
        assertEquals(" A I", result.text)
        assertTrue(result.ranges.any { it.color == UzzapColorCodeParser.colorFor(3) })
        assertTrue(result.ranges.any { it.color == UzzapColorCodeParser.colorFor(4) })
    }

    @Test
    fun nicknameMarkupProducesVisibleNickname() {
        assertEquals("cY", UzzapColorCodeParser.plainText("[c02 cY]"))
    }

    @Test
    fun unknownColorCodeDoesNotCrash() {
        val result = UzzapColorCodeParser.parseMarkup("[c99 test]")
        assertEquals("test", result.text)
        assertTrue(result.ranges.isEmpty())
    }

    @Test
    fun allThirtyColorCodesAreDefined() {
        for (code in 1..30) {
            assertTrue("Missing color code c${code.toString().padStart(2, '0')}", UzzapColorCodeParser.colorFor(code) != null)
        }
    }
}
