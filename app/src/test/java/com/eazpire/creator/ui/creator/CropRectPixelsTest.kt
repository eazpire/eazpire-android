package com.eazpire.creator.ui.creator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CropRectPixelsTest {
    @Test
    fun fullRectMapsToFullOriginalPng() {
        val px = CropRect.FULL.toManualCropPixels(4000, 3000)
        assertNotNull(px)
        assertEquals(0, px!!.x)
        assertEquals(0, px.y)
        assertEquals(4000, px.w)
        assertEquals(3000, px.h)
    }

    @Test
    fun centerHalfMapsProportionally() {
        val rect = CropRect(left = 0.25f, top = 0.25f, width = 0.5f, height = 0.5f)
        val px = rect.toManualCropPixels(2000, 1000)
        assertNotNull(px)
        assertEquals(500, px!!.x)
        assertEquals(250, px.y)
        assertEquals(1000, px.w)
        assertEquals(500, px.h)
    }

    @Test
    fun rejectsTinyServerCanvas() {
        assertNull(CropRect.FULL.toManualCropPixels(8, 8))
        assertNull(CropRect.FULL.toManualCropPixels(0, 1024))
    }
}
