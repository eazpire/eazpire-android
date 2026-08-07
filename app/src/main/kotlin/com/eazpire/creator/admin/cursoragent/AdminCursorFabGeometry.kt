package com.eazpire.creator.admin.cursoragent

/**
 * Pure helpers for FAB percent ↔ pixel mapping (matches web admin shell).
 *
 * Percentages map into the **usable** rectangle (container minus edge paddings),
 * so 100%/100% is bottom-end of content — above system bars / footer clearance —
 * not the raw screen corner.
 */
object AdminCursorFabGeometry {
    fun clampPct(value: Float): Float = value.coerceIn(0f, 100f)

    /**
     * Usable top-left range for the FAB inside [container] given [fabSize] and
     * start/end padding along that axis.
     */
    fun usableRange(
        container: Float,
        fabSize: Float,
        paddingStart: Float,
        paddingEnd: Float,
    ): Pair<Float, Float> {
        val min = paddingStart.coerceAtLeast(0f)
        val max = (container - fabSize - paddingEnd.coerceAtLeast(0f)).coerceAtLeast(min)
        return min to max
    }

    /** Convert stored percent to top-left pixel offset within padded content bounds. */
    fun offsetFromPct(
        xPct: Float,
        yPct: Float,
        containerW: Float,
        containerH: Float,
        fabSizePx: Float,
        paddingLeft: Float = 0f,
        paddingTop: Float = 0f,
        paddingRight: Float = 0f,
        paddingBottom: Float = 0f,
    ): Pair<Float, Float> {
        val (minX, maxX) = usableRange(containerW, fabSizePx, paddingLeft, paddingRight)
        val (minY, maxY) = usableRange(containerH, fabSizePx, paddingTop, paddingBottom)
        val left = minX + (clampPct(xPct) / 100f) * (maxX - minX)
        val top = minY + (clampPct(yPct) / 100f) * (maxY - minY)
        return left to top
    }

    /** Convert current top-left offset to percent for server prefs. */
    fun pctFromOffset(
        left: Float,
        top: Float,
        containerW: Float,
        containerH: Float,
        fabSizePx: Float,
        paddingLeft: Float = 0f,
        paddingTop: Float = 0f,
        paddingRight: Float = 0f,
        paddingBottom: Float = 0f,
    ): AdminCursorFabPos {
        val (minX, maxX) = usableRange(containerW, fabSizePx, paddingLeft, paddingRight)
        val (minY, maxY) = usableRange(containerH, fabSizePx, paddingTop, paddingBottom)
        val rangeX = (maxX - minX).coerceAtLeast(1f)
        val rangeY = (maxY - minY).coerceAtLeast(1f)
        return AdminCursorFabPos(
            xPct = clampPct(((left - minX) / rangeX) * 100f),
            yPct = clampPct(((top - minY) / rangeY) * 100f),
        )
    }

    /** Clamp drag offset so the FAB stays inside the padded content area. */
    fun clampOffset(
        left: Float,
        top: Float,
        containerW: Float,
        containerH: Float,
        fabSizePx: Float,
        paddingLeft: Float = 0f,
        paddingTop: Float = 0f,
        paddingRight: Float = 0f,
        paddingBottom: Float = 0f,
    ): Pair<Float, Float> {
        val (minX, maxX) = usableRange(containerW, fabSizePx, paddingLeft, paddingRight)
        val (minY, maxY) = usableRange(containerH, fabSizePx, paddingTop, paddingBottom)
        return left.coerceIn(minX, maxX) to top.coerceIn(minY, maxY)
    }

    /**
     * Default bottom-right of the usable content area.
     * Callers must pass the same paddings into [offsetFromPct] when resolving pixels
     * ([insetPx] on all sides + [paddingBottomExtraPx] for app footer clearance).
     */
    fun defaultBottomRightPct(
        containerW: Float,
        containerH: Float,
        fabSizePx: Float,
        insetPx: Float,
        paddingBottomExtraPx: Float = 0f,
    ): AdminCursorFabPos {
        // Keep signature stable for call sites / tests; dimensions validate the safe range exists.
        usableRange(containerW, fabSizePx, insetPx, insetPx)
        usableRange(containerH, fabSizePx, insetPx, insetPx + paddingBottomExtraPx)
        return AdminCursorFabPos(xPct = 100f, yPct = 100f)
    }
}
