package com.eazpire.creator.admin.cursoragent

/**
 * Pure helpers for FAB percent ↔ pixel mapping (matches web admin shell).
 */
object AdminCursorFabGeometry {
    fun clampPct(value: Float): Float = value.coerceIn(0f, 100f)

    /** Convert stored percent to top-left pixel offset. */
    fun offsetFromPct(
        xPct: Float,
        yPct: Float,
        containerW: Float,
        containerH: Float,
        fabSizePx: Float,
    ): Pair<Float, Float> {
        val maxX = (containerW - fabSizePx).coerceAtLeast(0f)
        val maxY = (containerH - fabSizePx).coerceAtLeast(0f)
        val left = (clampPct(xPct) / 100f) * maxX
        val top = (clampPct(yPct) / 100f) * maxY
        return left to top
    }

    /** Convert current top-left offset to percent for server prefs. */
    fun pctFromOffset(
        left: Float,
        top: Float,
        containerW: Float,
        containerH: Float,
        fabSizePx: Float,
    ): AdminCursorFabPos {
        val maxX = (containerW - fabSizePx).coerceAtLeast(1f)
        val maxY = (containerH - fabSizePx).coerceAtLeast(1f)
        return AdminCursorFabPos(
            xPct = clampPct((left / maxX) * 100f),
            yPct = clampPct((top / maxY) * 100f),
        )
    }

    /** Default bottom-right placement (~16dp inset expressed as %). */
    fun defaultBottomRightPct(
        containerW: Float,
        containerH: Float,
        fabSizePx: Float,
        insetPx: Float,
    ): AdminCursorFabPos {
        val left = (containerW - fabSizePx - insetPx).coerceAtLeast(0f)
        val top = (containerH - fabSizePx - insetPx).coerceAtLeast(0f)
        return pctFromOffset(left, top, containerW, containerH, fabSizePx)
    }
}
