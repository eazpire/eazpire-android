package com.eazpire.creator.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random

enum class CreatorModePixelDirection {
    /** Shop → Creator: outgoing screen dissolves left → right */
    LeftToRight,
    /** Creator → Shop: outgoing screen dissolves right → left */
    RightToLeft,
}

data class CreatorModePixelTransitionState(
    val snapshot: Bitmap,
    val direction: CreatorModePixelDirection,
)

private data class DissolvePixel(
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Color,
    val dissolveAt: Float,
    val vx: Float,
    val vy: Float,
)

private fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)

private fun sampleSnapshot(
    bitmap: Bitmap,
    canvasW: Float,
    canvasH: Float,
    stepPx: Int,
    direction: CreatorModePixelDirection,
    rng: Random,
): List<DissolvePixel> {
    if (canvasW < 1f || canvasH < 1f) return emptyList()

    val bmpW = bitmap.width.toFloat().coerceAtLeast(1f)
    val bmpH = bitmap.height.toFloat().coerceAtLeast(1f)
    val scale = maxOf(canvasW / bmpW, canvasH / bmpH)
    val dw = bmpW * scale
    val dh = bmpH * scale
    val ox = (canvasW - dw) / 2f
    val oy = (canvasH - dh) / 2f
    val sampleStep = (stepPx / scale).toInt().coerceIn(1, 24)
    val drawSize = (stepPx * 1.05f).coerceAtLeast(4f)

    val pixels = ArrayList<DissolvePixel>(4096)
    var gy = 0
    while (gy < bitmap.height) {
        var gx = 0
        while (gx < bitmap.width) {
            val px = bitmap.getPixel(
                gx.coerceIn(0, bitmap.width - 1),
                gy.coerceIn(0, bitmap.height - 1),
            )
            val alpha = (px ushr 24 and 0xFF) / 255f
            if (alpha > 0.06f) {
                val screenX = ox + gx * scale
                val screenY = oy + gy * scale
                val normX = (screenX / canvasW).coerceIn(0f, 1f)
                val jitter = rng.nextFloat() * 0.08f - 0.04f
                val dissolveAt = when (direction) {
                    CreatorModePixelDirection.LeftToRight -> normX + jitter
                    CreatorModePixelDirection.RightToLeft -> (1f - normX) + jitter
                }
                val sweepSign = when (direction) {
                    CreatorModePixelDirection.LeftToRight -> 1f
                    CreatorModePixelDirection.RightToLeft -> -1f
                }
                pixels.add(
                    DissolvePixel(
                        x = screenX,
                        y = screenY,
                        size = drawSize,
                        color = Color(
                            red = (px shr 16 and 0xFF) / 255f,
                            green = (px shr 8 and 0xFF) / 255f,
                            blue = (px and 0xFF) / 255f,
                            alpha = alpha,
                        ),
                        dissolveAt = dissolveAt.coerceIn(0f, 1f),
                        vx = sweepSign * (1.6f + rng.nextFloat() * 2.8f),
                        vy = (rng.nextFloat() - 0.5f) * 2.2f,
                    ),
                )
            }
            gx += sampleStep
        }
        gy += sampleStep
    }
    return pixels
}

/**
 * Full-screen pixel dissolve overlay for Shop ↔ Creator mode switches.
 * The outgoing screen is sampled into colored blocks that scatter at a moving frontier;
 * the incoming screen is already rendered underneath.
 */
@Composable
fun CreatorModePixelTransitionOverlay(
    snapshot: Bitmap,
    direction: CreatorModePixelDirection,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 1500L,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val rng = remember { Random(42) }
    val canvasW = snapshot.width.toFloat().coerceAtLeast(1f)
    val canvasH = snapshot.height.toFloat().coerceAtLeast(1f)
    val pixels = remember(snapshot, direction) {
        val step = when {
            minOf(canvasW, canvasH) < 720f -> 10
            minOf(canvasW, canvasH) < 1080f -> 12
            else -> 14
        }
        sampleSnapshot(snapshot, canvasW, canvasH, step, direction, rng)
    }

    LaunchedEffect(snapshot, direction) {
        progress = 0f
        val start = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            if (progress >= 1f) break
            delay(16)
        }
        onFinished()
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { /* block touches during transition */ }
            },
    ) {
        if (pixels.isEmpty()) return@Canvas

        val dissolveBand = 0.14f
        val p = easeOutCubic(progress)

        for (pixel in pixels) {
            val local = (p - pixel.dissolveAt) / dissolveBand
            if (local < 0f) {
                drawRect(
                    color = pixel.color,
                    topLeft = Offset(pixel.x, pixel.y),
                    size = Size(pixel.size, pixel.size),
                )
            } else if (local < 1f) {
                val t = easeOutCubic(local)
                val alpha = pixel.color.alpha * (1f - t)
                if (alpha > 0.02f) {
                    val drift = t * 28f
                    drawRect(
                        color = pixel.color.copy(alpha = alpha),
                        topLeft = Offset(
                            pixel.x + pixel.vx * drift,
                            pixel.y + pixel.vy * drift,
                        ),
                        size = Size(
                            pixel.size * (1f - t * 0.35f),
                            pixel.size * (1f - t * 0.35f),
                        ),
                    )
                }
            }
        }
    }
}
