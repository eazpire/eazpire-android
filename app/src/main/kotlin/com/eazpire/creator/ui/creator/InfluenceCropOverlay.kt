package com.eazpire.creator.ui.creator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import kotlin.math.abs
import kotlin.math.min

enum class InfluenceCropHandle {
    NW, N, NE, E, SE, S, SW, W, MOVE, NONE
}

data class ContainedFitRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun toComposeRect(): Rect = Rect(left, top, left + width, top + height)
}

/** Hit-test and resize math for the Reference Influence crop frame (matches Web 8 handles). */
object InfluenceCropMath {
    fun containedFit(containerW: Int, containerH: Int, imgW: Int, imgH: Int): ContainedFitRect {
        if (containerW <= 0 || containerH <= 0 || imgW <= 0 || imgH <= 0) {
            return ContainedFitRect(0f, 0f, 0f, 0f)
        }
        val scale = min(containerW.toFloat() / imgW, containerH.toFloat() / imgH)
        val dw = imgW * scale
        val dh = imgH * scale
        val left = (containerW - dw) / 2f
        val top = (containerH - dh) / 2f
        return ContainedFitRect(left, top, dw, dh)
    }

    fun hit(
        px: Float,
        py: Float,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        hitRadius: Float,
    ): InfluenceCropHandle {
        val right = left + width
        val bottom = top + height
        val m = hitRadius
        if (abs(px - left) < m && abs(py - top) < m) return InfluenceCropHandle.NW
        if (abs(px - right) < m && abs(py - top) < m) return InfluenceCropHandle.NE
        if (abs(px - left) < m && abs(py - bottom) < m) return InfluenceCropHandle.SW
        if (abs(px - right) < m && abs(py - bottom) < m) return InfluenceCropHandle.SE
        if (abs(py - top) < m && px >= left && px <= right) return InfluenceCropHandle.N
        if (abs(py - bottom) < m && px >= left && px <= right) return InfluenceCropHandle.S
        if (abs(px - left) < m && py >= top && py <= bottom) return InfluenceCropHandle.W
        if (abs(px - right) < m && py >= top && py <= bottom) return InfluenceCropHandle.E
        if (px >= left && px <= right && py >= top && py <= bottom) return InfluenceCropHandle.MOVE
        return InfluenceCropHandle.NONE
    }

    fun apply(
        handle: InfluenceCropHandle,
        orig: CropRect,
        dxNorm: Float,
        dyNorm: Float,
        minSize: Float = 0.05f,
    ): CropRect {
        val next = when (handle) {
            InfluenceCropHandle.MOVE -> CropRect(orig.left + dxNorm, orig.top + dyNorm, orig.width, orig.height)
            InfluenceCropHandle.E -> orig.copy(width = orig.width + dxNorm)
            InfluenceCropHandle.W -> CropRect(orig.left + dxNorm, orig.top, orig.width - dxNorm, orig.height)
            InfluenceCropHandle.S -> orig.copy(height = orig.height + dyNorm)
            InfluenceCropHandle.N -> CropRect(orig.left, orig.top + dyNorm, orig.width, orig.height - dyNorm)
            InfluenceCropHandle.SE -> orig.copy(width = orig.width + dxNorm, height = orig.height + dyNorm)
            InfluenceCropHandle.NW -> CropRect(
                orig.left + dxNorm,
                orig.top + dyNorm,
                orig.width - dxNorm,
                orig.height - dyNorm,
            )
            InfluenceCropHandle.NE -> CropRect(
                orig.left,
                orig.top + dyNorm,
                orig.width + dxNorm,
                orig.height - dyNorm,
            )
            InfluenceCropHandle.SW -> CropRect(
                orig.left + dxNorm,
                orig.top,
                orig.width - dxNorm,
                orig.height + dyNorm,
            )
            InfluenceCropHandle.NONE -> orig
        }
        return clamp(next, minSize)
    }

    fun clamp(r: CropRect, minSize: Float = 0.05f): CropRect {
        val l = r.left.coerceIn(0f, 1f - minSize)
        val t = r.top.coerceIn(0f, 1f - minSize)
        val w = r.width.coerceIn(minSize, 1f - l)
        val h = r.height.coerceIn(minSize, 1f - t)
        return CropRect(l, t, w, h)
    }
}

/**
 * Reference Influence crop overlay: darkened outside, white frame, 8 orange handles
 * (corners + edges), drag interior to move. Matches Web `reference-influence-modal.js`.
 */
@Composable
fun InfluenceCropOverlay(
    imageDisplayRect: Rect,
    cropRect: CropRect,
    onCropRectChange: (CropRect) -> Unit,
    frameDescription: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val hitRadius = with(density) { 24.dp.toPx() }
    val handleVisual = with(density) { 12.dp.toPx() }
    val stroke = with(density) { 2.dp.toPx() }
    val cropState = remember { mutableStateOf(cropRect) }
    cropState.value = cropRect

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = frameDescription }
            .pointerInput(
                imageDisplayRect.left,
                imageDisplayRect.top,
                imageDisplayRect.width,
                imageDisplayRect.height,
                hitRadius,
            ) {
                var handle = InfluenceCropHandle.NONE
                var orig = cropState.value
                var start = Offset.Zero
                detectDragGestures(
                    onDragStart = { offset ->
                        if (imageDisplayRect.width < 1f || imageDisplayRect.height < 1f) {
                            handle = InfluenceCropHandle.NONE
                            return@detectDragGestures
                        }
                        val r = cropState.value
                        val left = imageDisplayRect.left + r.left * imageDisplayRect.width
                        val top = imageDisplayRect.top + r.top * imageDisplayRect.height
                        val w = r.width * imageDisplayRect.width
                        val h = r.height * imageDisplayRect.height
                        handle = InfluenceCropMath.hit(offset.x, offset.y, left, top, w, h, hitRadius)
                        orig = r
                        start = offset
                    },
                    onDrag = { change, _ ->
                        if (handle == InfluenceCropHandle.NONE) return@detectDragGestures
                        if (imageDisplayRect.width < 1f || imageDisplayRect.height < 1f) return@detectDragGestures
                        change.consume()
                        val dx = (change.position.x - start.x) / imageDisplayRect.width
                        val dy = (change.position.y - start.y) / imageDisplayRect.height
                        val next = InfluenceCropMath.apply(handle, orig, dx, dy)
                        cropState.value = next
                        onCropRectChange(next)
                    },
                    onDragEnd = { handle = InfluenceCropHandle.NONE },
                    onDragCancel = { handle = InfluenceCropHandle.NONE },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = cropState.value
            val selLeft = imageDisplayRect.left + r.left * imageDisplayRect.width
            val selTop = imageDisplayRect.top + r.top * imageDisplayRect.height
            val selW = r.width * imageDisplayRect.width
            val selH = r.height * imageDisplayRect.height
            val selRight = selLeft + selW
            val selBottom = selTop + selH
            val dim = Color.Black.copy(alpha = 0.55f)
            drawRect(dim, Offset(0f, 0f), Size(size.width, selTop.coerceAtLeast(0f)))
            drawRect(
                dim,
                Offset(0f, selBottom),
                Size(size.width, (size.height - selBottom).coerceAtLeast(0f)),
            )
            drawRect(dim, Offset(0f, selTop), Size(selLeft.coerceAtLeast(0f), selH.coerceAtLeast(0f)))
            drawRect(
                dim,
                Offset(selRight, selTop),
                Size((size.width - selRight).coerceAtLeast(0f), selH.coerceAtLeast(0f)),
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(selLeft, selTop),
                size = Size(selW.coerceAtLeast(0f), selH.coerceAtLeast(0f)),
                style = Stroke(width = stroke),
            )
            val pts = listOf(
                Offset(selLeft, selTop),
                Offset(selLeft + selW / 2f, selTop),
                Offset(selRight, selTop),
                Offset(selRight, selTop + selH / 2f),
                Offset(selRight, selBottom),
                Offset(selLeft + selW / 2f, selBottom),
                Offset(selLeft, selBottom),
                Offset(selLeft, selTop + selH / 2f),
            )
            val hsz = handleVisual / 2f
            pts.forEach { p ->
                drawRect(
                    color = EazColors.Orange,
                    topLeft = Offset(p.x - hsz, p.y - hsz),
                    size = Size(handleVisual, handleVisual),
                )
            }
        }
    }
}
