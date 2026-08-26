package com.eazpire.creator.ui.creator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignCanvasTextLayoutTest {
    @Test
    fun exportFontPxScalesPosterSize() {
        assertEquals(396f, DesignCanvasTextLayout.exportFontPx(88), 0.001f)
    }

    @Test
    fun exportFontPxFallsBackForInvalidKeys() {
        assertEquals(396f, DesignCanvasTextLayout.exportFontPx(0), 0.001f)
        assertEquals(396f, DesignCanvasTextLayout.exportFontPx(-4), 0.001f)
    }

    @Test
    fun viewerFontPxScalesFromStageWidth() {
        assertEquals(42.24f, DesignCanvasTextLayout.viewerFontPx(480f, 88), 0.001f)
    }

    @Test
    fun wrapLinesBreaksOnWidth() {
        val measure: (String) -> Float = { it.length.toFloat() }
        assertEquals(listOf("hello", "world"), DesignCanvasTextLayout.wrapLines("hello world", 8f, measure))
        assertEquals(listOf("hello world"), DesignCanvasTextLayout.wrapLines("hello world", 20f, measure))
    }

    @Test
    fun wrapLinesKeepsExplicitBreaks() {
        val measure: (String) -> Float = { it.length.toFloat() }
        assertEquals(listOf("stay", "wild"), DesignCanvasTextLayout.wrapLines("stay\nwild", 40f, measure))
        assertEquals(listOf("stay", "", "wild"), DesignCanvasTextLayout.wrapLines("stay\n\nwild", 40f, measure))
    }

    @Test
    fun wrapLinesKeepsOversizedWord() {
        val measure: (String) -> Float = { it.length.toFloat() }
        assertEquals(
            listOf("supercalifragilistic"),
            DesignCanvasTextLayout.wrapLines("supercalifragilistic", 4f, measure),
        )
    }

    @Test
    fun sizeKeysMatchWeb() {
        assertTrue(DESIGN_CANVAS_SIZE_KEYS.containsAll(listOf(32, 48, 64, 88, 120)))
    }
}
