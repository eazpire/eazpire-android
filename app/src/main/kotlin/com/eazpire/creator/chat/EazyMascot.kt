package com.eazpire.creator.chat

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

private val MascotSize = 48.dp
private val EazyOrange = Color(0xFFF97316)

/** Leichte Vibration beim Long-Press (Snap-Mode aktiv) – wie Web: 30ms */
private fun vibrateSnapMode(context: android.content.Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(30)
    }
}

/** Vibration beim Snappen – wie Web: [50, 80, 50, 80, 100] ms */
private fun vibrateSnap(context: android.content.Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(50, 80, 50, 80, 100), -1))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(longArrayOf(50, 80, 50, 80, 100), -1)
    }
}
private val EazyOrangeLight = Color(0xFFFF9A2A)
/** Must cover finger imprecision + coordinate rounding; header slot is small */
private val SNAP_DISTANCE_DP = 112f
private val LONG_PRESS_MS = 300L
private val DRAG_SLOP_PX = 18f

/**
 * Eazy mascot – same as web: orange blob with eyes.
 * Freely movable, long-press snap into header.
 */
private fun clamp(v: Float, min: Float, max: Float) = v.coerceIn(min, max)

@Composable
fun EazyMascot(
    modifier: Modifier = Modifier,
    isDocked: Boolean,
    positionX: Float?,
    positionY: Float?,
    onPositionChange: (Float, Float) -> Unit,
    onDockedChange: (Boolean) -> Unit,
    onOpenChat: () -> Unit,
    slotBoundsInRoot: Rect?,
    onSnapModeChange: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    contentWidthPx: Float? = null,
    contentHeightPx: Float? = null,
    contentBoundsInRoot: Rect? = null,
    /** Face left (e.g. toward generator speech bubble). */
    lookLeft: Boolean = false,
    /**
     * When true, horizontal facing follows mascot center vs screen half (left → look right, right → look left),
     * matching web `eazy-mascot.js` / `creator-mobile.js` undocked behavior.
     */
    autoFaceFromScreenHalf: Boolean = false,
    /** Fires on every frame with current visual position (incl. drag) so overlays can follow the mascot. */
    onVisualPositionChange: (Float, Float) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val mascotSizePx = with(density) { MascotSize.toPx() }

    val areaWidthPx = contentWidthPx ?: screenWidthPx
    val areaHeightPx = contentHeightPx ?: screenHeightPx

    val defaultX = areaWidthPx - mascotSizePx - 32
    val defaultY = areaHeightPx - mascotSizePx - 100
    val maxX = (areaWidthPx - mascotSizePx).coerceAtLeast(0f)
    val maxY = (areaHeightPx - mascotSizePx).coerceAtLeast(0f)

    val rawX = when {
        positionX == null || positionX.isNaN() -> defaultX
        else -> clamp(positionX, 0f, maxX)
    }
    val rawY = when {
        positionY == null || positionY.isNaN() -> defaultY
        else -> clamp(positionY, 0f, maxY)
    }

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var snapModeActive by remember { mutableStateOf(false) }
    var dragBaseX by remember { mutableStateOf(0f) }
    var dragBaseY by remember { mutableStateOf(0f) }

    val currentX = if (isDocked) 0f else rawX
    val currentY = if (isDocked) 0f else rawY

    val displayX = if (isDragging) dragBaseX + offsetX else currentX + offsetX
    val displayY = if (isDragging) dragBaseY + offsetY else currentY + offsetY

    SideEffect {
        onVisualPositionChange(displayX, displayY)
    }

    val snapDistancePx = with(density) { SNAP_DISTANCE_DP.dp.toPx() }

    fun trySnapToSlot(mascotX: Float, mascotY: Float) {
        val slot = slotBoundsInRoot ?: return
        val contentBounds = contentBoundsInRoot ?: return
        val mascotCenterX = contentBounds.left + mascotX + mascotSizePx / 2
        val mascotCenterY = contentBounds.top + mascotY + mascotSizePx / 2
        val slotCenterX = slot.left + slot.width / 2
        val slotCenterY = slot.top + slot.height / 2
        val dist = hypot(mascotCenterX - slotCenterX, mascotCenterY - slotCenterY)
        if (dist < snapDistancePx) {
            vibrateSnap(context)
            onDockedChange(true)
        }
    }

    fun savePosition(x: Float, y: Float) {
        val clampedX = clamp(x, 0f, maxX)
        val clampedY = clamp(y, 0f, maxY)
        onPositionChange(clampedX, clampedY)
    }

    if (isDocked) return

    var halfScreenLookLeft by remember { mutableStateOf(lookLeft) }
    val effectiveLookLeft =
        if (autoFaceFromScreenHalf) halfScreenLookLeft else lookLeft

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                if (!autoFaceFromScreenHalf) return@onGloballyPositioned
                val c = coords.boundsInRoot().center
                halfScreenLookLeft = c.x >= screenWidthPx * 0.5f
            }
            .offset {
                IntOffset(displayX.roundToInt(), displayY.roundToInt())
            }
            .size(MascotSize)
            .pointerInput(rawX, rawY, maxX, maxY) {
                forEachGesture {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != androidx.compose.ui.input.pointer.PointerEventType.Press) continue
                            var accOffsetX = 0f
                            var accOffsetY = 0f
                            var dragging = false
                            var snapMode = false
                            var baseX = rawX
                            var baseY = rawY
                            val lpJob = scope.launch {
                                delay(LONG_PRESS_MS)
                                snapMode = true
                                vibrateSnapMode(context)
                                onSnapModeChange(true)
                            }
                            outer@ while (true) {
                                val ev = awaitPointerEvent()
                                when (ev.type) {
                                    androidx.compose.ui.input.pointer.PointerEventType.Move -> {
                                        val ch = ev.changes.first()
                                        val delta = ch.positionChange()
                                        if (!dragging) {
                                            val totalDx = accOffsetX + delta.x
                                            val totalDy = accOffsetY + delta.y
                                            if (hypot(totalDx, totalDy) >= DRAG_SLOP_PX) {
                                                lpJob.cancel()
                                                dragging = true
                                                baseX = rawX + accOffsetX
                                                baseY = rawY + accOffsetY
                                                accOffsetX += delta.x
                                                accOffsetY += delta.y
                                            } else {
                                                accOffsetX = totalDx
                                                accOffsetY = totalDy
                                            }
                                        } else {
                                            accOffsetX += delta.x
                                            accOffsetY += delta.y
                                        }
                                        dragBaseX = baseX
                                        dragBaseY = baseY
                                        offsetX = accOffsetX
                                        offsetY = accOffsetY
                                        isDragging = dragging
                                        ch.consume()
                                    }
                                    androidx.compose.ui.input.pointer.PointerEventType.Release -> {
                                        lpJob.cancel()
                                        onSnapModeChange(false)
                                        val finalX = baseX + accOffsetX
                                        val finalY = baseY + accOffsetY
                                        if (snapMode) trySnapToSlot(finalX, finalY)
                                        else if (!dragging) onOpenChat()
                                        else savePosition(finalX, finalY)
                                        offsetX = 0f
                                        offsetY = 0f
                                        isDragging = false
                                        break@outer
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(scaleX = if (effectiveLookLeft) -1f else 1f, scaleY = 1f)
        ) {
            drawEazyMascot(this)
        }
    }
}

@Composable
fun EazyMascotIcon(
    modifier: Modifier = Modifier,
    /** When true, mascot faces left (for speech bubble on the left). */
    lookLeft: Boolean = false
) {
    Canvas(
        modifier = modifier.scale(scaleX = if (lookLeft) -1f else 1f, scaleY = 1f)
    ) {
        drawEazyMascot(this)
    }
}

private fun drawEazyMascot(ds: DrawScope) {
    val s = minOf(ds.size.width, ds.size.height) / 128f
    ds.scale(s, s, pivot = Offset.Zero) {
        val p = Path().apply {
            moveTo(30f, 62f)
            cubicTo(30f, 36f, 50f, 18f, 94f, 18f)
            cubicTo(116f, 18f, 132f, 34f, 132f, 52f)
            cubicTo(132f, 77f, 113f, 96f, 68f, 96f)
            cubicTo(63f, 96f, 58f, 95.2f, 53.5f, 93.8f)
            lineTo(37f, 103f)
            lineTo(42.8f, 86.2f)
            cubicTo(35.7f, 83f, 30f, 73.2f, 30f, 62f)
            close()
        }
        ds.drawPath(p, Brush.linearGradient(listOf(EazyOrangeLight, EazyOrange), Offset.Zero, Offset(128f, 128f)))
        val inner = Path().apply {
            moveTo(56f, 39f)
            cubicTo(41f, 45f, 32f, 59f, 32f, 74f)
            cubicTo(32f, 93f, 48f, 108f, 68f, 108f)
            cubicTo(88f, 108f, 104f, 92f, 104f, 72f)
            cubicTo(104f, 51f, 87f, 34f, 66f, 34f)
            cubicTo(62.3f, 34f, 58.4f, 34.7f, 56f, 34.9f)
            lineTo(56f, 39f)
            close()
        }
        ds.drawPath(inner, Color.White)
        ds.drawCircle(EazyOrange, 6f, Offset(72f, 62f))
        ds.drawCircle(EazyOrange, 5f, Offset(90f, 54f))
        ds.drawCircle(Brush.linearGradient(listOf(EazyOrangeLight, EazyOrange), Offset.Zero, Offset(128f, 128f)), 7f, Offset(50f, 28f))
    }
}
