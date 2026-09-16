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
    fun allThirtyColorsExist() {
        for (code in 1..30) {
            assertTrue("Missing c${code.toString().padStart(2, '0')}", UzzapColorCodeParser.colorFor(code) != null)
        }
    }
}
