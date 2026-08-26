package com.eazpire.creator.ui.creator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchPrintAreaTest {
    @Test
    fun fallbackChestIsLargerThanLegacyBox() {
        val r = ResearchPrintArea.rect(1000, 1000)
        assertNotNull(r)
        assertEquals(200, r!!.left)
        assertEquals(120, r.top)
        assertEquals(800, r.right)
        assertEquals(680, r.bottom)
        assertTrue(r.height() > 380)
    }

    @Test
    fun tinyImagesHaveNoFallbackRect() {
        assertNull(ResearchPrintArea.rect(4, 4))
    }

    @Test
    fun syntheticDesignIsNotClippedByArtworkDetect() {
        val w = 200
        val h = 200
        val dx = 70
        val dy = 45
        val dw = 60
        val dh = 100
        val legacyBottom = (h * (0.18f + 0.38f)).toInt()
        assertTrue(dy + dh > legacyBottom)
        val pixels = ResearchPrintArea.makeSyntheticShirtArgb(w, h, dx, dy, dw, dh)
        val crop = ResearchPrintArea.detectArtworkRect(w, h, pixels)
        assertNotNull(crop)
        assertTrue(crop!!.left <= dx)
        assertTrue(crop.top <= dy)
        assertTrue(crop.right >= dx + dw)
        assertTrue(crop.bottom >= dy + dh)
        assertTrue(crop.bottom > legacyBottom)
    }

    @Test
    fun uniformShirtFallsBackToNullDetect() {
        val pixels = ResearchPrintArea.makeSyntheticShirtArgb(200, 200, 0, 0, 0, 0)
        assertNull(ResearchPrintArea.detectArtworkRect(200, 200, pixels))
    }

    @Test
    fun defaultCropRectUsesArtworkThenChestFallback() {
        val w = 200
        val h = 200
        val ink = ResearchPrintArea.makeSyntheticShirtArgb(w, h, 70, 45, 60, 100)
        val detected = ResearchPrintArea.defaultCropRect(w, h, ink)
        assertTrue(detected.left < 0.4f)
        assertTrue(detected.top < 0.3f)
        assertTrue(detected.left + detected.width > 0.6f)
        assertTrue(detected.top + detected.height > 0.7f)

        val uniform = ResearchPrintArea.makeSyntheticShirtArgb(w, h, 0, 0, 0, 0)
        val fallback = ResearchPrintArea.defaultCropRect(w, h, uniform)
        assertEquals(ResearchPrintArea.X, fallback.left, 0.02f)
        assertEquals(ResearchPrintArea.Y, fallback.top, 0.02f)
        assertEquals(ResearchPrintArea.W, fallback.width, 0.02f)
        assertEquals(ResearchPrintArea.H, fallback.height, 0.02f)
    }
}
