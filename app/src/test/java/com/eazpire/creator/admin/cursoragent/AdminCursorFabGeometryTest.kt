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
    fun defaultBottomRight_isNearCorner() {
        val w = 1000f
        val h = 2000f
        val fab = 100f
        val inset = 20f
        val pos = AdminCursorFabGeometry.defaultBottomRightPct(w, h, fab, inset)
        assertTrue(pos.xPct > 80f)
        assertTrue(pos.yPct > 80f)
    }

    @Test
    fun modeFromApi() {
        assertEquals(AdminCursorMode.ASK, AdminCursorMode.fromApi("ask"))
        assertEquals(AdminCursorMode.AGENT, AdminCursorMode.fromApi("agent"))
        assertEquals(AdminCursorMode.AGENT, AdminCursorMode.fromApi(null))
    }
}
