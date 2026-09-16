package com.kolipri.uzzap.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UzzapEmoticonRendererTest {
    @Test
    fun smileCodeRendersAsEmoji() {
        assertEquals("cy 🙂", UzzapEmoticonRenderer.render("cy :)"))
    }

    @Test
    fun tongueCodeRendersAsEmoji() {
        assertEquals("cy 😛", UzzapEmoticonRenderer.render("cy :p"))
    }

    @Test
    fun commonCodesRenderTogether() {
        assertEquals("🙂 😛 😄 😞", UzzapEmoticonRenderer.render(":) :p :D :("))
    }

    @Test
    fun ordinaryTextIsPreserved() {
        assertEquals("hello world", UzzapEmoticonRenderer.render("hello world"))
    }
}
