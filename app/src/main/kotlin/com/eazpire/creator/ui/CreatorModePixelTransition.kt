package com.eazpire.creator.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.eazpire.creator.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val CREATOR_LOGO_URL =
    "https://cdn.shopify.com/s/files/1/0739/5203/5098/files/eazpire-creator-logo.png?v=1763666950"

private val Orange = Color(0xFFF97316)

enum class CreatorModePixelDirection {
    /** Branded intro when switching Shop → Creator */
    ShopToCreatorIntro,
    /** Pixel dissolve when switching Creator → Shop */
    CreatorToShop,
}

sealed class CreatorModePixelTransitionState {
    data object ShopToCreatorIntro : CreatorModePixelTransitionState()
    data class CreatorToShop(val snapshot: Bitmap) : CreatorModePixelTransitionState()
}

private data class PixelCell(
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Color,
    val reveal: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
)

private data class LogoLayout(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

private data class IntroAssets(
    val galaxy: Bitmap,
    val shopLogo: Bitmap,
    val creatorLogo: Bitmap,
    val width: Int,
    val height: Int,
    val splashCells: List<PixelCell>,
    val galaxyCells: List<PixelCell>,
    val shopLogoCells: List<PixelCell>,
    val creatorLogoCells: List<PixelCell>,
    val shopLogoLayout: LogoLayout,
    val creatorLogoLayout: LogoLayout,
    val galaxyImage: androidx.compose.ui.graphics.ImageBitmap,
    val shopLogoImage: androidx.compose.ui.graphics.ImageBitmap,
    val creatorLogoImage: androidx.compose.ui.graphics.ImageBitmap,
)

private object IntroTimings {
    const val HOLD_MS = 750L
    const val CENTER_DISSOLVE_MS = 1300L
    const val SHOP_LOGO_FADE_MS = 650L
    const val GALAXY_REVEAL_MS = 2400L
    const val LOGO_SWAP_MS = 1300L

    val totalMs: Long
        get() = HOLD_MS + CENTER_DISSOLVE_MS + SHOP_LOGO_FADE_MS + GALAXY_REVEAL_MS + LOGO_SWAP_MS
}

private fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)

private fun easeInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

private fun clamp(v: Float, minV: Float = 0f, maxV: Float = 1f): Float = max(minV, min(maxV, v))

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

private fun layoutBitmap(img: Bitmap, boxW: Float, boxH: Float, maxScale: Float = 1f): LogoLayout {
    val iw = img.width.toFloat().coerceAtLeast(1f)
    val ih = img.height.toFloat().coerceAtLeast(1f)
    val scale = min(boxW / iw, boxH / ih) * maxScale
    val w = iw * scale
    val h = ih * scale
    return LogoLayout(
        x = (boxW - w) / 2f,
        y = (boxH - h) / 2f,
        w = w,
        h = h,
    )
}

private fun sampleBitmapCells(
    source: Bitmap,
    step: Int,
    revealFn: (normX: Float, normY: Float) -> Float,
    boundsX: Int = 0,
    boundsY: Int = 0,
    boundsW: Int = source.width,
    boundsH: Int = source.height,
): List<PixelCell> {
    val cells = ArrayList<PixelCell>(4096)
    val size = (step + 1).toFloat()
    val w = source.width
    val h = source.height
    var y = boundsY
    while (y < boundsY + boundsH) {
        var x = boundsX
        while (x < boundsX + boundsW) {
            val sx = x.coerceIn(0, w - 1)
            val sy = y.coerceIn(0, h - 1)
            val px = source.getPixel(sx, sy)
            val a = (px ushr 24 and 0xFF) / 255f
            if (a > 0.06f) {
                cells.add(
                    PixelCell(
                        x = sx.toFloat(),
                        y = sy.toFloat(),
                        size = size,
                        color = Color(
                            red = (px shr 16 and 0xFF) / 255f,
                            green = (px shr 8 and 0xFF) / 255f,
                            blue = (px and 0xFF) / 255f,
                            alpha = a,
                        ),
                        reveal = revealFn(sx / w.toFloat(), sy / h.toFloat()),
                    ),
                )
            }
            x += step
        }
        y += step
    }
    return cells
}

private fun buildSplashBitmap(context: Context, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.parseColor("#F97316"))
    val eSize = (min(width, height) * 0.34f).toInt().coerceAtLeast(1)
    val drawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome)
    if (drawable != null) {
        val left = (width - eSize) / 2
        val top = (height - eSize) / 2
        drawable.setBounds(left, top, left + eSize, top + eSize)
        DrawableCompat.setTint(drawable, android.graphics.Color.WHITE)
        drawable.draw(canvas)
    } else {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        canvas.drawCircle(width / 2f, height / 2f, eSize / 3f, paint)
    }
    return bitmap
}

private fun buildGalaxyBitmap(galaxy: Bitmap, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawBitmap(galaxy, null, android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
    val paint = Paint().apply {
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

private fun buildLogoBitmap(logo: Bitmap, layout: LogoLayout, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawBitmap(
        logo,
        null,
        android.graphics.RectF(layout.x, layout.y, layout.x + layout.w, layout.y + layout.h),
        null,
    )
    return bitmap
}

private suspend fun buildIntroAssets(context: Context, width: Int, height: Int): IntroAssets {
    val galaxy = BitmapFactory.decodeResource(context.resources, R.drawable.galaxy_nebula_bg)
    val shopLogo = BitmapFactory.decodeResource(context.resources, R.drawable.eazpire_logo)
    val creatorLogo = loadCreatorLogoBitmap(context)
    val step = when {
        min(width, height) < 720 -> 8
        min(width, height) < 1080 -> 10
        else -> 12
    }

    val splashBmp = buildSplashBitmap(context, width, height)
    val splashCells = sampleBitmapCells(
        splashBmp,
        step,
        revealFn = { nx, _ -> abs(nx - 0.5f) * 2f },
    )
    splashBmp.recycle()

    val galaxyBmp = buildGalaxyBitmap(galaxy, width, height)
    val galaxyCells = sampleBitmapCells(
        galaxyBmp,
        step,
        revealFn = { _, ny -> ny },
    )

    val shopLayout = layoutBitmap(shopLogo, width * 0.72f, height * 0.22f)
    val creatorLayout = layoutBitmap(creatorLogo, width * 0.72f, height * 0.22f)
    val shopLogoBmp = buildLogoBitmap(shopLogo, shopLayout, width, height)
    val creatorLogoBmp = buildLogoBitmap(creatorLogo, creatorLayout, width, height)

    val shopCellsRaw = sampleBitmapCells(shopLogoBmp, step, { nx, _ -> nx })
    val shopMinX = shopCellsRaw.minOfOrNull { it.x } ?: 0f
    val shopMaxX = shopCellsRaw.maxOfOrNull { it.x } ?: 1f
    val shopSpan = max(1f, shopMaxX - shopMinX)
    val shopLogoCells = shopCellsRaw.map { c ->
        c.copy(reveal = (c.x - shopMinX) / shopSpan)
    }

    val creatorCellsRaw = sampleBitmapCells(creatorLogoBmp, step, { nx, _ -> nx })
    val creatorMinX = creatorCellsRaw.minOfOrNull { it.x } ?: 0f
    val creatorMaxX = creatorCellsRaw.maxOfOrNull { it.x } ?: 1f
    val creatorSpan = max(1f, creatorMaxX - creatorMinX)
    val creatorLogoCells = creatorCellsRaw.map { c ->
        c.copy(reveal = (c.x - creatorMinX) / creatorSpan)
    }

    return IntroAssets(
        galaxy = galaxyBmp,
        shopLogo = shopLogo,
        creatorLogo = creatorLogo,
        width = width,
        height = height,
        splashCells = splashCells,
        galaxyCells = galaxyCells,
        shopLogoCells = shopLogoCells,
        creatorLogoCells = creatorLogoCells,
        shopLogoLayout = shopLayout,
        creatorLogoLayout = creatorLayout,
        galaxyImage = galaxyBmp.asImageBitmap(),
        shopLogoImage = shopLogoBmp.asImageBitmap(),
        creatorLogoImage = creatorLogoBmp.asImageBitmap(),
    )
}

private fun drawDissolveFromCenter(
    cells: List<PixelCell>,
    progress: Float,
    band: Float = 0.16f,
    block: (PixelCell, Float, Float) -> Unit,
) {
    val p = easeInOutCubic(clamp(progress))
    for (cell in cells) {
        val local = (p - cell.reveal) / band
        when {
            local < 0f -> block(cell, 1f, 0f)
            local < 1f -> {
                val t = easeOutCubic(local)
                block(cell, 1f - t, t * 36f)
            }
        }
    }
}

private fun drawRevealCells(
    cells: List<PixelCell>,
    globalT: Float,
    pixelMix: Float,
    block: (PixelCell, Float) -> Unit,
) {
    if (pixelMix <= 0.01f) return
    val eased = easeOutCubic(clamp(globalT))
    for (cell in cells) {
        val local = clamp((eased - cell.reveal * 0.84f) / 0.16f)
        if (local <= 0f) continue
        block(cell, local * pixelMix)
    }
}

@Composable
fun CreatorModePixelTransitionOverlay(
    state: CreatorModePixelTransitionState,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is CreatorModePixelTransitionState.ShopToCreatorIntro -> {
            ShopToCreatorIntroOverlay(onFinished = onFinished, modifier = modifier)
        }
        is CreatorModePixelTransitionState.CreatorToShop -> {
            CreatorToShopDissolveOverlay(
                snapshot = state.snapshot,
                onFinished = onFinished,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ShopToCreatorIntroOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var assets by remember { mutableStateOf<IntroAssets?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(1)
        val h = dm.heightPixels.coerceAtLeast(1)
        assets = buildIntroAssets(context, w, h)
        val start = System.currentTimeMillis()
        val total = IntroTimings.totalMs
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            progress = (elapsed.toFloat() / total).coerceIn(0f, 1f)
            if (elapsed >= total) break
            delay(16)
        }
        assets?.galaxy?.recycle()
        onFinished()
    }

    val a = assets ?: return

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { /* block touches */ }
            },
    ) {
        val w = size.width
        val h = size.height
        val elapsedMs = progress * IntroTimings.totalMs

        val tHold = IntroTimings.HOLD_MS.toFloat()
        val tDissolve = IntroTimings.CENTER_DISSOLVE_MS.toFloat()
        val tShopFade = IntroTimings.SHOP_LOGO_FADE_MS.toFloat()
        val tGalaxy = IntroTimings.GALAXY_REVEAL_MS.toFloat()
        val tLogoSwap = IntroTimings.LOGO_SWAP_MS.toFloat()

        val dissolveStart = tHold
        val shopFadeStart = dissolveStart + tDissolve
        val galaxyStart = shopFadeStart + tShopFade
        val logoSwapStart = galaxyStart + tGalaxy

        // Phase 1: Orange + E logo hold
        if (elapsedMs < dissolveStart) {
            drawRect(Orange, topLeft = Offset.Zero, size = Size(w, h))
            val eSize = min(w, h) * 0.34f
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome)
            if (drawable != null) {
                drawIntoCanvas { canvas ->
                    val bmp = Bitmap.createBitmap(eSize.toInt(), eSize.toInt(), Bitmap.Config.ARGB_8888)
                    val c = AndroidCanvas(bmp)
                    drawable.setBounds(0, 0, eSize.toInt(), eSize.toInt())
                    DrawableCompat.setTint(drawable, android.graphics.Color.WHITE)
                    drawable.draw(c)
                    canvas.nativeCanvas.drawBitmap(
                        bmp,
                        (w - eSize) / 2f,
                        (h - eSize) / 2f,
                        null,
                    )
                    bmp.recycle()
                }
            }
            return@Canvas
        }

        // Phase 2: Center dissolve (orange + E)
        val dissolveElapsed = elapsedMs - dissolveStart
        val dissolveProgress = clamp(dissolveElapsed / tDissolve)
        if (dissolveProgress < 1f || elapsedMs < shopFadeStart) {
            drawRect(Orange, topLeft = Offset.Zero, size = Size(w, h))
            drawDissolveFromCenter(a.splashCells, dissolveProgress) { cell, alpha, drift ->
                if (alpha <= 0.02f) return@drawDissolveFromCenter
                val sign = if (cell.x < w / 2f) -1f else 1f
                drawRect(
                    color = cell.color.copy(alpha = cell.color.alpha * alpha),
                    topLeft = Offset(cell.x + sign * drift, cell.y),
                    size = Size(cell.size * (1f - drift / 80f), cell.size * (1f - drift / 80f)),
                )
            }
        }

        // White base once splash is mostly gone
        if (elapsedMs >= shopFadeStart - 80f && elapsedMs < galaxyStart) {
            drawRect(Color.White, topLeft = Offset.Zero, size = Size(w, h))
        }

        val shopFadeElapsed = elapsedMs - shopFadeStart
        val shopFadeT = easeOutCubic(clamp(shopFadeElapsed / tShopFade))
        val shopLogoAlpha = if (elapsedMs >= logoSwapStart) {
            1f - easeOutCubic(clamp((elapsedMs - logoSwapStart) / tLogoSwap))
        } else {
            shopFadeT
        }

        // Phase 4: Galaxy pixel reveal (sharp underneath revealed area)
        if (elapsedMs >= galaxyStart) {
            val galaxyElapsed = elapsedMs - galaxyStart
            val galaxyT = clamp(galaxyElapsed / tGalaxy)
            val eased = easeOutCubic(galaxyT)
            val frontierY = h * eased
            val band = h * 0.08f
            val sharpTop = (frontierY - band).coerceAtLeast(0f)

            clipRect(left = 0f, top = 0f, right = w, bottom = sharpTop) {
                drawImage(
                    image = a.galaxyImage,
                    dstSize = IntSize(w.toInt(), h.toInt()),
                    filterQuality = FilterQuality.High,
                )
            }

            val pixelMix = if (galaxyT >= 1f) 0f else 1f - smoothstep(clamp((galaxyElapsed - tGalaxy * 0.78f) / (tGalaxy * 0.22f)))
            drawRevealCells(a.galaxyCells, galaxyT, pixelMix) { cell, alpha ->
                if (cell.y > frontierY + band || cell.y < sharpTop - band) return@drawRevealCells
                drawRect(
                    color = cell.color.copy(alpha = cell.color.alpha * alpha),
                    topLeft = Offset(cell.x, cell.y),
                    size = Size(cell.size, cell.size),
                )
            }

            if (galaxyT >= 1f) {
                drawImage(
                    image = a.galaxyImage,
                    dstSize = IntSize(w.toInt(), h.toInt()),
                    filterQuality = FilterQuality.High,
                )
            }
        }

        // Phase 3+: Eazpire shop logo (stays until galaxy completes, then fades during swap)
        if (shopLogoAlpha > 0.01f && elapsedMs >= shopFadeStart) {
            drawImage(
                image = a.shopLogoImage,
                dstOffset = IntOffset(a.shopLogoLayout.x.toInt(), a.shopLogoLayout.y.toInt()),
                dstSize = IntSize(a.shopLogoLayout.w.toInt(), a.shopLogoLayout.h.toInt()),
                alpha = shopLogoAlpha,
                filterQuality = FilterQuality.High,
            )
        }

        // Phase 5: Eazpire Creator in while Eazpire out
        if (elapsedMs >= logoSwapStart) {
            val swapElapsed = elapsedMs - logoSwapStart
            val swapT = easeOutCubic(clamp(swapElapsed / tLogoSwap))
            val frontierX = w * swapT
            val band = w * 0.1f
            val sharpLeft = (frontierX - band).coerceAtLeast(0f)

            clipRect(left = 0f, top = 0f, right = sharpLeft, bottom = h) {
                drawImage(
                    image = a.creatorLogoImage,
                    dstOffset = IntOffset(
                        a.creatorLogoLayout.x.toInt(),
                        a.creatorLogoLayout.y.toInt(),
                    ),
                    dstSize = IntSize(
                        a.creatorLogoLayout.w.toInt(),
                        a.creatorLogoLayout.h.toInt(),
                    ),
                    filterQuality = FilterQuality.High,
                )
            }

            val pixelMix = 1f - smoothstep(clamp((swapElapsed - tLogoSwap * 0.75f) / (tLogoSwap * 0.25f)))
            drawRevealCells(a.creatorLogoCells, swapT, pixelMix) { cell, alpha ->
                if (cell.x > frontierX + band) return@drawRevealCells
                drawRect(
                    color = cell.color.copy(alpha = cell.color.alpha * alpha),
                    topLeft = Offset(cell.x, cell.y),
                    size = Size(cell.size, cell.size),
                )
            }

            if (swapT >= 1f) {
                drawImage(
                    image = a.creatorLogoImage,
                    dstOffset = IntOffset(
                        a.creatorLogoLayout.x.toInt(),
                        a.creatorLogoLayout.y.toInt(),
                    ),
                    dstSize = IntSize(
                        a.creatorLogoLayout.w.toInt(),
                        a.creatorLogoLayout.h.toInt(),
                    ),
                    filterQuality = FilterQuality.High,
                )
            }
        }
    }
}

@Composable
private fun CreatorToShopDissolveOverlay(
    snapshot: Bitmap,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 1500L,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val canvasW = snapshot.width.toFloat().coerceAtLeast(1f)
    val canvasH = snapshot.height.toFloat().coerceAtLeast(1f)
    val pixels = remember(snapshot) {
        val step = when {
            min(canvasW, canvasH) < 720f -> 10
            min(canvasW, canvasH) < 1080f -> 12
            else -> 14
        }
        sampleSnapshotDissolve(snapshot, canvasW, canvasH, step, fromRight = true)
    }

    LaunchedEffect(snapshot) {
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
                detectTapGestures { /* block touches */ }
            },
    ) {
        if (pixels.isEmpty()) return@Canvas
        val dissolveBand = 0.14f
        val p = easeOutCubic(progress)
        for (pixel in pixels) {
            val local = (p - pixel.reveal) / dissolveBand
            when {
                local < 0f -> drawRect(
                    color = pixel.color,
                    topLeft = Offset(pixel.x, pixel.y),
                    size = Size(pixel.size, pixel.size),
                )
                local < 1f -> {
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
}

private fun sampleSnapshotDissolve(
    bitmap: Bitmap,
    canvasW: Float,
    canvasH: Float,
    stepPx: Int,
    fromRight: Boolean,
): List<PixelCell> {
    if (canvasW < 1f || canvasH < 1f) return emptyList()
    val bmpW = bitmap.width.toFloat().coerceAtLeast(1f)
    val bmpH = bitmap.height.toFloat().coerceAtLeast(1f)
    val scale = max(canvasW / bmpW, canvasH / bmpH)
    val dw = bmpW * scale
    val dh = bmpH * scale
    val ox = (canvasW - dw) / 2f
    val oy = (canvasH - dh) / 2f
    val sampleStep = (stepPx / scale).toInt().coerceIn(1, 24)
    val drawSize = (stepPx * 1.05f).coerceAtLeast(4f)
    val cells = ArrayList<PixelCell>(4096)
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
                val normX = (screenX / canvasW).coerceIn(0f, 1f)
                val jitter = (kotlin.random.Random.nextFloat() - 0.5f) * 0.08f
                val dissolveAt = if (fromRight) (1f - normX) + jitter else normX + jitter
                val sweepSign = if (fromRight) -1f else 1f
                cells.add(
                    PixelCell(
                        x = screenX,
                        y = oy + gy * scale,
                        size = drawSize,
                        color = Color(
                            red = (px shr 16 and 0xFF) / 255f,
                            green = (px shr 8 and 0xFF) / 255f,
                            blue = (px and 0xFF) / 255f,
                            alpha = alpha,
                        ),
                        reveal = dissolveAt.coerceIn(0f, 1f),
                        vx = sweepSign * (1.6f + kotlin.random.Random.nextFloat() * 2.8f),
                        vy = (kotlin.random.Random.nextFloat() - 0.5f) * 2.2f,
                    ),
                )
            }
            gx += sampleStep
        }
        gy += sampleStep
    }
    return cells
}
