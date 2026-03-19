package com.eazpire.creator.ui.creator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

/**
 * Normalized crop rect: left, top, width, height in 0..1 (relative to displayed image).
 */
data class CropRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    fun toPixelRect(imgWidth: Int, imgHeight: Int): android.graphics.Rect {
        return android.graphics.Rect(
            (left * imgWidth).toInt().coerceIn(0, imgWidth),
            (top * imgHeight).toInt().coerceIn(0, imgHeight),
            ((left + width) * imgWidth).toInt().coerceIn(0, imgWidth),
            ((top + height) * imgHeight).toInt().coerceIn(0, imgHeight)
        )
    }

    companion object {
        val FULL = CropRect(0f, 0f, 1f, 1f)
    }
}

/**
 * Draggable and resizable crop frame overlay.
 * @param imageDisplayRect Rect of the displayed image within the container (in pixels)
 * @param cropRect Current crop rect (normalized 0-1)
 * @param onCropRectChange Callback when crop rect changes
 */
@Composable
fun CropFrameOverlay(
    imageDisplayRect: Rect,
    cropRect: CropRect,
    onCropRectChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    val minSize = 0.05f
    var current by remember(cropRect) { mutableStateOf(cropRect) }

    fun clampRect(r: CropRect): CropRect {
        val l = r.left.coerceIn(0f, 1f - minSize)
        val t = r.top.coerceIn(0f, 1f - minSize)
        val w = r.width.coerceIn(minSize, 1f - l)
        val h = r.height.coerceIn(minSize, 1f - t)
        return CropRect(l, t, w, h)
    }

    fun toDisplayRect(r: CropRect): Rect {
        val x = imageDisplayRect.left + r.left * imageDisplayRect.width
        val y = imageDisplayRect.top + r.top * imageDisplayRect.height
        val w = r.width * imageDisplayRect.width
        val h = r.height * imageDisplayRect.height
        return Rect(Offset(x, y), Size(w, h))
    }

    fun hitTest(px: Float, py: Float): String {
        val r = toDisplayRect(current)
        val handleSize = 28f
        val centerSize = 48f
        val cx = r.left + r.width / 2
        val cy = r.top + r.height / 2
        if (px in (cx - centerSize)..(cx + centerSize) && py in (cy - centerSize)..(cy + centerSize)) return "move"
        if (px < r.left + handleSize && py < r.top + handleSize) return "tl"
        if (px > r.left + r.width - handleSize && py < r.top + handleSize) return "tr"
        if (px > r.left + r.width - handleSize && py > r.top + r.height - handleSize) return "br"
        if (px < r.left + handleSize && py > r.top + r.height - handleSize) return "bl"
        if (py < r.top + handleSize) return "t"
        if (py > r.top + r.height - handleSize) return "b"
        if (px < r.left + handleSize) return "l"
        if (px > r.left + r.width - handleSize) return "r"
        return ""
    }

    fun pxToNorm(dx: Float, dy: Float): Pair<Float, Float> {
        return (dx / imageDisplayRect.width) to (dy / imageDisplayRect.height)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dragStart: CropRect? = null
                var handle = ""
                detectDragGestures(
                    onDragStart = { offset ->
                        handle = hitTest(offset.x, offset.y)
                        dragStart = current
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val s = dragStart ?: return@detectDragGestures
                        val (dx, dy) = pxToNorm(dragAmount.x, dragAmount.y)
                        val next = when (handle) {
                            "move" -> CropRect(
                                (s.left + dx).coerceIn(0f, 1f - s.width),
                                (s.top + dy).coerceIn(0f, 1f - s.height),
                                s.width,
                                s.height
                            )
                            "tl" -> CropRect(
                                (s.left + dx).coerceIn(0f, s.left + s.width - minSize),
                                (s.top + dy).coerceIn(0f, s.top + s.height - minSize),
                                (s.width - dx).coerceIn(minSize, 1f),
                                (s.height - dy).coerceIn(minSize, 1f)
                            )
                            "tr" -> CropRect(
                                s.left,
                                (s.top + dy).coerceIn(0f, s.top + s.height - minSize),
                                (s.width + dx).coerceIn(minSize, 1f - s.left),
                                (s.height - dy).coerceIn(minSize, 1f)
                            )
                            "br" -> s.copy(
                                width = (s.width + dx).coerceIn(minSize, 1f - s.left),
                                height = (s.height + dy).coerceIn(minSize, 1f - s.top)
                            )
                            "bl" -> CropRect(
                                (s.left + dx).coerceIn(0f, s.left + s.width - minSize),
                                s.top,
                                (s.width - dx).coerceIn(minSize, 1f),
                                (s.height + dy).coerceIn(minSize, 1f - s.top)
                            )
                            "t" -> s.copy(
                                top = (s.top + dy).coerceIn(0f, s.top + s.height - minSize),
                                height = (s.height - dy).coerceIn(minSize, 1f)
                            )
                            "b" -> s.copy(height = (s.height + dy).coerceIn(minSize, 1f - s.top))
                            "l" -> s.copy(
                                left = (s.left + dx).coerceIn(0f, s.left + s.width - minSize),
                                width = (s.width - dx).coerceIn(minSize, 1f)
                            )
                            "r" -> s.copy(width = (s.width + dx).coerceIn(minSize, 1f - s.left))
                            else -> s
                        }
                        current = clampRect(next)
                        onCropRectChange(current)
                        dragStart = current
                    },
                    onDragEnd = { dragStart = null; handle = "" }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = toDisplayRect(current)
            val white = Color.White.copy(alpha = 0.9f)
            val handleSize = 10f
            val crossSize = 12f

            // Rahmen
            drawRect(
                color = white,
                topLeft = r.topLeft,
                size = r.size,
                style = Stroke(width = 2f)
            )

            // Fadenkreuz in der Mitte (für Verschieben)
            val cx = r.left + r.width / 2
            val cy = r.top + r.height / 2
            drawLine(white, Offset(cx - crossSize / 2, cy), Offset(cx + crossSize / 2, cy), strokeWidth = 2f)
            drawLine(white, Offset(cx, cy - crossSize / 2), Offset(cx, cy + crossSize / 2), strokeWidth = 2f)

            // Kleine Quadrate an Ecken und Kanten (8 Handles)
            val handles = listOf(
                Offset(r.left, r.top),                    // tl
                Offset(r.left + r.width / 2, r.top),       // t
                Offset(r.left + r.width, r.top),           // tr
                Offset(r.left + r.width, r.top + r.height / 2), // r
                Offset(r.left + r.width, r.top + r.height),     // br
                Offset(r.left + r.width / 2, r.top + r.height), // b
                Offset(r.left, r.top + r.height),          // bl
                Offset(r.left, r.top + r.height / 2)       // l
            )
            handles.forEach { center ->
                val topLeft = Offset(center.x - handleSize / 2, center.y - handleSize / 2)
                drawRect(
                    color = Color.White.copy(alpha = 0.25f),
                    topLeft = topLeft,
                    size = Size(handleSize, handleSize)
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.7f),
                    topLeft = topLeft,
                    size = Size(handleSize, handleSize),
                    style = Stroke(width = 1f)
                )
            }
        }
    }
}
