package com.kolipri.uzzap.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UzzapColorCodeParserTest {
    @Test
    fun nicknameMarkupIsStripped() {
        assertEquals("cY", UzzapColorCodeParser.plainText("[c02 cY"))
    }

    @Test
    fun singleSpaceBeforeSwitchIsSyntax() {
        assertEquals("AI", UzzapColorCodeParser.plainText("[c03 A [c04 I"))
    }

    @Test
    fun doubleSpaceBeforeSwitchLeavesOneVisibleSpace() {
        assertEquals("A I", UzzapColorCodeParser.plainText("[c03 A  [c04 I"))
    }

    @Test
    fun unknownColorCodeIsStripped() {
        assertEquals("test", UzzapColorCodeParser.plainText("[c99 test"))
    }

    @Test
    fun closingBracketIsOptional() {
        assertEquals("green", UzzapColorCodeParser.plainText("[c02 green"))
        assertEquals("green", UzzapColorCodeParser.plainText("[c02] green"))
    }

    @Test
    fun colorSwitchesProduceExpectedRanges() {
        val parsed = UzzapColorCodeParser.parseMarkup("[c03 red [c02 green")
        assertEquals("redgreen", parsed.text)
        assertEquals(2, parsed.ranges.size)
        assertEquals(0, parsed.ranges[0].start)
        assertEquals(3, parsed.ranges[0].end)
        assertEquals(UzzapColorCodeParser.colorFor(3), parsed.ranges[0].color)
        assertEquals(3, parsed.ranges[1].start)
        assertEquals(8, parsed.ranges[1].end)
        assertEquals(UzzapColorCodeParser.colorFor(2), parsed.ranges[1].color)
    }

    @Test
    fun allThirtyColorsExist() {
        for (code in 1..30) {
            assertTrue("Missing c${code.toString().padStart(2, '0')}", UzzapColorCodeParser.colorFor(code) != null)
        }
    }
}
