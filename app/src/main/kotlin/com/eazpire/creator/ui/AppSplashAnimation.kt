package com.eazpire.creator.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.eazpire.creator.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val CREATOR_LOGO_URL =
    "https://cdn.shopify.com/s/files/1/0739/5203/5098/files/eazpire-creator-logo.png?v=1763666950"

private val White = Color.White

private object SplashTimings {
    const val HOLD_MS = 1000L
    const val REVEAL_MS = 2400L
    const val SETTLE_MS = 650L

    val totalMs: Long
        get() = HOLD_MS + REVEAL_MS + SETTLE_MS
}

private data class SharedLogoLayout(
    val x: Float,
    val y: Float,
    val w: Float,
    val blockH: Float,
    val shopH: Float,
    val creatorH: Float,
)

private data class SplashBgCell(
    val x: Float,
    val y: Float,
    val size: Float,
    val reveal: Float,
    val color: Color,
)

private data class SplashLogoCell(
    val x: Float,
    val y: Float,
    val size: Float,
    val reveal: Float,
    val shopColor: Color,
    val creatorColor: Color,
    val shopAlpha: Float,
    val creatorAlpha: Float,
)

private data class SplashAssets(
    val width: Int,
    val height: Int,
    val logoLayout: SharedLogoLayout,
    val galaxyImage: androidx.compose.ui.graphics.ImageBitmap,
    val shopLogo: androidx.compose.ui.graphics.ImageBitmap,
    val creatorLogo: androidx.compose.ui.graphics.ImageBitmap,
    val bgCells: List<SplashBgCell>,
    val logoCells: List<SplashLogoCell>,
)

private fun clamp(v: Float, minV: Float = 0f, maxV: Float = 1f): Float = max(minV, min(maxV, v))

private fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)

private fun smoothstep(t: Float): Float {
    val x = clamp(t)
    return x * x * (3f - 2f * x)
}

private suspend fun loadCreatorLogoBitmap(context: Context): Bitmap = withContext(Dispatchers.IO) {
    try {
        val conn = URL(CREATOR_LOGO_URL).openConnection()
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.getInputStream().use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        BitmapFactory.decodeResource(context.resources, R.drawable.eazpire_logo)
    }
}

private fun computeLogoLayout(
    width: Float,
    height: Float,
    shopLogo: Bitmap,
    creatorLogo: Bitmap,
): SharedLogoLayout {
    val targetW = width * 0.56f
    val shopW = shopLogo.width.toFloat().coerceAtLeast(1f)
    val shopH = shopLogo.height.toFloat().coerceAtLeast(1f)
    val creatorW = creatorLogo.width.toFloat().coerceAtLeast(1f)
    val creatorH = creatorLogo.height.toFloat().coerceAtLeast(1f)
    val shopDrawH = shopH * (targetW / shopW)
    val creatorDrawH = creatorH * (targetW / creatorW)
    val blockH = max(shopDrawH, creatorDrawH)
    return SharedLogoLayout(
        x = (width - targetW) / 2f,
        y = (height - blockH) / 2f,
        w = targetW,
        blockH = blockH,
        shopH = shopDrawH,
        creatorH = creatorDrawH,
    )
}

private fun buildGalaxyBitmap(galaxy: Bitmap, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawBitmap(galaxy, null, android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
    val paint = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(0x660A0514.toInt(), 0x9905020F.toInt()),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    return bitmap
}

private fun buildLogoLayerBitmap(
    logo: Bitmap,
    layout: SharedLogoLayout,
    width: Int,
    height: Int,
    logoH: Float,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawBitmap(
        logo,
        null,
        android.graphics.RectF(layout.x, layout.y, layout.x + layout.w, layout.y + logoH),
        null,
    )
    return bitmap
}

private fun buildBackgroundCells(galaxyBmp: Bitmap, width: Int, height: Int, step: Int): List<SplashBgCell> {
    val cells = ArrayList<SplashBgCell>(4096)
    val size = (step + 1).toFloat()
    val hMax = max(1, height - step).toFloat()
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val px = galaxyBmp.getPixel(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))
            cells.add(
                SplashBgCell(
                    x = x.toFloat(),
                    y = y.toFloat(),
                    size = size,
                    reveal = y / hMax,
                    color = Color(
                        red = (px shr 16 and 0xFF) / 255f,
                        green = (px shr 8 and 0xFF) / 255f,
                        blue = (px and 0xFF) / 255f,
                        alpha = (px ushr 24 and 0xFF) / 255f,
                    ),
                ),
            )
            x += step
        }
        y += step
    }
    return cells
}

private fun buildLogoCells(
    shopLayer: Bitmap,
    creatorLayer: Bitmap,
    layout: SharedLogoLayout,
    width: Int,
    height: Int,
    step: Int,
): List<SplashLogoCell> {
    val cells = ArrayList<SplashLogoCell>(2048)
    val size = (step + 1).toFloat()
    val hMax = max(1, height - step).toFloat()
    val minY = layout.y.toInt()
    val maxY = (layout.y + layout.blockH).toInt()
    val minX = layout.x.toInt()
    val maxX = (layout.x + layout.w).toInt()

    var y = minY
    while (y < maxY) {
        var x = minX
        while (x < maxX) {
            val px = x.coerceIn(0, width - 1)
            val py = y.coerceIn(0, height - 1)
            val shopPx = shopLayer.getPixel(px, py)
            val creatorPx = creatorLayer.getPixel(px, py)
            val shopA = (shopPx ushr 24 and 0xFF) / 255f
            val creatorA = (creatorPx ushr 24 and 0xFF) / 255f
            if (shopA >= 0.04f || creatorA >= 0.04f) {
                cells.add(
                    SplashLogoCell(
                        x = px.toFloat(),
                        y = py.toFloat(),
                        size = size,
                        reveal = py / hMax,
                        shopColor = Color(
                            red = (shopPx shr 16 and 0xFF) / 255f,
                            green = (shopPx shr 8 and 0xFF) / 255f,
                            blue = (shopPx and 0xFF) / 255f,
                        ),
                        creatorColor = Color(
                            red = (creatorPx shr 16 and 0xFF) / 255f,
                            green = (creatorPx shr 8 and 0xFF) / 255f,
                            blue = (creatorPx and 0xFF) / 255f,
                        ),
                        shopAlpha = shopA,
                        creatorAlpha = creatorA,
                    ),
                )
            }
            x += step
        }
        y += step
    }
    return cells
}

private suspend fun buildSplashAssets(context: Context, width: Int, height: Int): SplashAssets {
    val galaxySrc = BitmapFactory.decodeResource(context.resources, R.drawable.galaxy_nebula_bg)
    val shopLogo = BitmapFactory.decodeResource(context.resources, R.drawable.eazpire_logo)
    val creatorLogo = loadCreatorLogoBitmap(context)
    val step = when {
        min(width, height) < 720 -> 8
        min(width, height) < 1080 -> 10
        else -> 12
    }

    val layout = computeLogoLayout(width.toFloat(), height.toFloat(), shopLogo, creatorLogo)
    val galaxyBmp = buildGalaxyBitmap(galaxySrc, width, height)
    val shopLayer = buildLogoLayerBitmap(shopLogo, layout, width, height, layout.shopH)
    val creatorLayer = buildLogoLayerBitmap(creatorLogo, layout, width, height, layout.creatorH)

    val bgCells = buildBackgroundCells(galaxyBmp, width, height, step)
    val logoCells = buildLogoCells(shopLayer, creatorLayer, layout, width, height, step)

    shopLayer.recycle()
    creatorLayer.recycle()

    return SplashAssets(
        width = width,
        height = height,
        logoLayout = layout,
        galaxyImage = galaxyBmp.asImageBitmap(),
        shopLogo = shopLogo.asImageBitmap(),
        creatorLogo = creatorLogo.asImageBitmap(),
        bgCells = bgCells,
        logoCells = logoCells,
    )
}

/**
 * App launch splash — parity with [dev/splash-animation-test/splash-animation.js]:
 * shop logo on white → top-to-bottom pixel wipe → galaxy + creator logo.
 */
@Composable
fun AppSplashOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var assets by remember { mutableStateOf<SplashAssets?>(null) }
    var elapsedMs by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(1)
        val h = dm.heightPixels.coerceAtLeast(1)
        assets = buildSplashAssets(context, w, h)
        val start = System.currentTimeMillis()
        val total = SplashTimings.totalMs
        while (true) {
            elapsedMs = (System.currentTimeMillis() - start).toFloat().coerceAtMost(total.toFloat())
            if (elapsedMs >= total) break
            delay(16)
        }
        onFinished()
    }

    val a = assets ?: return

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { /* block touches during splash */ }
            },
    ) {
        val w = size.width
        val h = size.height
        val t = elapsedMs

        if (t < SplashTimings.HOLD_MS) {
            drawRect(White, topLeft = Offset.Zero, size = Size(w, h))
            val l = a.logoLayout
            drawImage(
                image = a.shopLogo,
                dstOffset = IntOffset(l.x.toInt(), l.y.toInt()),
                dstSize = IntSize(l.w.toInt(), l.shopH.toInt()),
                filterQuality = FilterQuality.High,
            )
            return@Canvas
        }

        val transitionElapsed = t - SplashTimings.HOLD_MS
        val revealT = clamp(transitionElapsed / SplashTimings.REVEAL_MS)
        val eased = easeOutCubic(revealT)
        val settleT = clamp((transitionElapsed - SplashTimings.REVEAL_MS * 0.78f) / SplashTimings.SETTLE_MS)
        val sharp = smoothstep(easeOutCubic(settleT))
        val pixelAlpha = 1f - sharp

        drawRect(White, topLeft = Offset.Zero, size = Size(w, h))

        if (sharp > 0f) {
            drawImage(
                image = a.galaxyImage,
                dstSize = IntSize(w.toInt(), h.toInt()),
                alpha = sharp,
                filterQuality = FilterQuality.High,
            )
        }

        if (pixelAlpha > 0.01f) {
            for (cell in a.bgCells) {
                val local = clamp((eased - cell.reveal * 0.84f) / 0.16f)
                if (local <= 0f) continue
                val mix = local * pixelAlpha
                if (mix <= 0f) continue
                drawRect(
                    color = cell.color.copy(alpha = cell.color.alpha * mix),
                    topLeft = Offset(cell.x, cell.y),
                    size = Size(cell.size, cell.size),
                )
            }

            for (cell in a.logoCells) {
                val local = clamp((eased - cell.reveal * 0.84f) / 0.16f)
                val color: Color
                val alpha: Float
                if (local <= 0f) {
                    if (cell.shopAlpha <= 0.04f) continue
                    color = cell.shopColor
                    alpha = cell.shopAlpha * pixelAlpha
                } else {
                    if (cell.creatorAlpha <= 0.04f) continue
                    color = cell.creatorColor
                    alpha = cell.creatorAlpha * pixelAlpha
                }
                drawRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(cell.x, cell.y),
                    size = Size(cell.size, cell.size),
                )
            }
        }

        if (sharp > 0f) {
            val l = a.logoLayout
            drawImage(
                image = a.creatorLogo,
                dstOffset = IntOffset(l.x.toInt(), l.y.toInt()),
                dstSize = IntSize(l.w.toInt(), l.creatorH.toInt()),
                alpha = sharp,
                filterQuality = FilterQuality.High,
            )
        } else if (transitionElapsed >= SplashTimings.REVEAL_MS + SplashTimings.SETTLE_MS) {
            drawImage(
                image = a.galaxyImage,
                dstSize = IntSize(w.toInt(), h.toInt()),
                filterQuality = FilterQuality.High,
            )
            val l = a.logoLayout
            drawImage(
                image = a.creatorLogo,
                dstOffset = IntOffset(l.x.toInt(), l.y.toInt()),
                dstSize = IntSize(l.w.toInt(), l.creatorH.toInt()),
                filterQuality = FilterQuality.High,
            )
        }
    }
}
