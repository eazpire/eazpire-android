package com.eazpire.creator.ui.creator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfluenceCropMathTest {
    @Test
    fun containedFitCentersPortraitInWideBox() {
        val fit = InfluenceCropMath.containedFit(400, 200, 100, 100)
        assertEquals(100f, fit.left, 0.01f)
        assertEquals(0f, fit.top, 0.01f)
        assertEquals(200f, fit.width, 0.01f)
        assertEquals(200f, fit.height, 0.01f)
    }

    @Test
    fun hitPrefersCornersThenEdgesThenMove() {
        val left = 40f
        val top = 40f
        val w = 200f
        val h = 120f
        val m = 24f
        assertEquals(InfluenceCropHandle.NW, InfluenceCropMath.hit(left, top, left, top, w, h, m))
        assertEquals(InfluenceCropHandle.SE, InfluenceCropMath.hit(left + w, top + h, left, top, w, h, m))
        assertEquals(InfluenceCropHandle.N, InfluenceCropMath.hit(left + w / 2f, top, left, top, w, h, m))
        assertEquals(InfluenceCropHandle.E, InfluenceCropMath.hit(left + w, top + h / 2f, left, top, w, h, m))
        assertEquals(
            InfluenceCropHandle.MOVE,
            InfluenceCropMath.hit(left + w / 2f, top + h / 2f, left, top, w, h, m),
        )
        assertEquals(InfluenceCropHandle.NONE, InfluenceCropMath.hit(0f, 0f, left, top, w, h, m))
    }

    @Test
    fun moveKeepsSizeAndStaysInside() {
        val orig = CropRect(0.2f, 0.2f, 0.3f, 0.4f)
        val moved = InfluenceCropMath.apply(InfluenceCropHandle.MOVE, orig, 0.1f, -0.05f)
        assertEquals(0.3f, moved.left, 0.001f)
        assertEquals(0.15f, moved.top, 0.001f)
        assertEquals(0.3f, moved.width, 0.001f)
        assertEquals(0.4f, moved.height, 0.001f)
    }

    @Test
    fun seHandleGrowsWidthAndHeight() {
        val orig = CropRect(0.2f, 0.2f, 0.3f, 0.3f)
        val next = InfluenceCropMath.apply(InfluenceCropHandle.SE, orig, 0.1f, 0.05f)
        assertEquals(0.2f, next.left, 0.001f)
        assertEquals(0.2f, next.top, 0.001f)
        assertEquals(0.4f, next.width, 0.001f)
        assertEquals(0.35f, next.height, 0.001f)
    }

    @Test
    fun nwHandleDoesNotInvertRect() {
        val orig = CropRect(0.4f, 0.4f, 0.2f, 0.2f)
        val next = InfluenceCropMath.apply(InfluenceCropHandle.NW, orig, 0.5f, 0.5f)
        assertTrue(next.width >= 0.05f)
        assertTrue(next.height >= 0.05f)
        assertTrue(next.left >= 0f)
        assertTrue(next.top >= 0f)
        assertTrue(next.left + next.width <= 1.001f)
        assertTrue(next.top + next.height <= 1.001f)
    }
}
