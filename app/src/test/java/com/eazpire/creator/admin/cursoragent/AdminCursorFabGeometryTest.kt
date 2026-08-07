package com.eazpire.creator.admin.cursoragent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminCursorFabGeometryTest {

    @Test
    fun clampPct_bounds() {
        assertEquals(0f, AdminCursorFabGeometry.clampPct(-5f), 0.001f)
        assertEquals(100f, AdminCursorFabGeometry.clampPct(140f), 0.001f)
        assertEquals(42f, AdminCursorFabGeometry.clampPct(42f), 0.001f)
    }

    @Test
    fun roundTrip_pct_offset() {
        val w = 1080f
        val h = 1920f
        val fab = 112f
        val pos = AdminCursorFabPos(25f, 75f)
        val (left, top) = AdminCursorFabGeometry.offsetFromPct(pos.xPct, pos.yPct, w, h, fab)
        val back = AdminCursorFabGeometry.pctFromOffset(left, top, w, h, fab)
        assertEquals(25f, back.xPct, 0.5f)
        assertEquals(75f, back.yPct, 0.5f)
    }

    @Test
    fun roundTrip_pct_offset_withPadding() {
        val w = 1080f
        val h = 1920f
        val fab = 112f
        val pad = 32f
        val bottomExtra = 80f
        val pos = AdminCursorFabPos(100f, 100f)
        val (left, top) =
            AdminCursorFabGeometry.offsetFromPct(
                pos.xPct,
                pos.yPct,
                w,
                h,
                fab,
                paddingLeft = pad,
                paddingTop = pad,
                paddingRight = pad,
                paddingBottom = pad + bottomExtra,
            )
        // Bottom-right of usable area: margin from right/bottom edges
        assertEquals(w - fab - pad, left, 0.5f)
        assertEquals(h - fab - pad - bottomExtra, top, 0.5f)
        val back =
            AdminCursorFabGeometry.pctFromOffset(
                left,
                top,
                w,
                h,
                fab,
                paddingLeft = pad,
                paddingTop = pad,
                paddingRight = pad,
                paddingBottom = pad + bottomExtra,
            )
        assertEquals(100f, back.xPct, 0.5f)
        assertEquals(100f, back.yPct, 0.5f)
    }

    @Test
    fun defaultBottomRight_isNearCorner() {
        val w = 1000f
        val h = 2000f
        val fab = 100f
        val inset = 16f
        val footer = 40f
        val pos =
            AdminCursorFabGeometry.defaultBottomRightPct(
                w,
                h,
                fab,
                inset,
                paddingBottomExtraPx = footer,
            )
        assertTrue(pos.xPct > 80f)
        assertTrue(pos.yPct > 80f)
        val (left, top) =
            AdminCursorFabGeometry.offsetFromPct(
                pos.xPct,
                pos.yPct,
                w,
                h,
                fab,
                paddingLeft = inset,
                paddingTop = inset,
                paddingRight = inset,
                paddingBottom = inset + footer,
            )
        assertEquals(w - fab - inset, left, 0.5f)
        assertEquals(h - fab - inset - footer, top, 0.5f)
        // Stays above footer clearance
        assertTrue(top + fab <= h - footer)
    }

    @Test
    fun clampOffset_respectsBottomPadding() {
        val w = 1000f
        val h = 2000f
        val fab = 100f
        val pad = 16f
        val footer = 40f
        val (left, top) =
            AdminCursorFabGeometry.clampOffset(
                left = 9999f,
                top = 9999f,
                containerW = w,
                containerH = h,
                fabSizePx = fab,
                paddingLeft = pad,
                paddingTop = pad,
                paddingRight = pad,
                paddingBottom = pad + footer,
            )
        assertEquals(w - fab - pad, left, 0.5f)
        assertEquals(h - fab - pad - footer, top, 0.5f)
    }

    @Test
    fun modeFromApi() {
        assertEquals(AdminCursorMode.ASK, AdminCursorMode.fromApi("ask"))
        assertEquals(AdminCursorMode.AGENT, AdminCursorMode.fromApi("agent"))
        assertEquals(AdminCursorMode.AGENT, AdminCursorMode.fromApi(null))
    }
}
