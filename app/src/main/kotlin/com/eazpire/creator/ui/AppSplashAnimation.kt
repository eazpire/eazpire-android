package com.eazpire.creator.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import kotlin.math.sqrt

private const val TAG = "AppSplash"

/** Remote creator logo — optional; falls back to local drawable on failure. */
private const val CREATOR_LOGO_URL =
    "https://cdn.shopify.com/s/files/1/0739/5203/5098/files/eazpire-creator-logo.png?v=1763666950"

/** Max decoded pixels for galaxy (full-screen draw is scaled in Canvas). */
private const val MAX_GALAXY_DECODE_PIXELS = 640_000L

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

private data class SplashAssets(
    val logoLayout: SharedLogoLayout,
    val galaxyImage: androidx.compose.ui.graphics.ImageBitmap,
    val shopLogo: androidx.compose.ui.graphics.ImageBitmap,
    val creatorLogo: androidx.compose.ui.graphics.ImageBitmap,
)

private fun clamp(v: Float, minV: Float = 0f, maxV: Float = 1f): Float = max(minV, min(maxV, v))

private fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)

private fun cappedDecodeSize(screenW: Int, screenH: Int): Pair<Int, Int> {
    val w = screenW.coerceAtLeast(1)
    val h = screenH.coerceAtLeast(1)
    val pixels = w.toLong() * h
    if (pixels <= MAX_GALAXY_DECODE_PIXELS) return w to h
    val scale = sqrt(MAX_GALAXY_DECODE_PIXELS.toDouble() / pixels).toFloat()
    return max(320, (w * scale).toInt()) to max(480, (h * scale).toInt())
}

private fun decodeDrawable(context: Context, @DrawableRes id: Int): Bitmap? {
    val opts =
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = true
        }
    return BitmapFactory.decodeResource(context.resources, id, opts)?.asSoftwareBitmapOrSelf()
}

private fun Bitmap.asSoftwareBitmapOrSelf(): Bitmap {
    if (config != Bitmap.Config.HARDWARE) return this
    return copy(Bitmap.Config.ARGB_8888, false) ?: this
}

private suspend fun loadCreatorLogoBitmap(context: Context): Bitmap =
    withContext(Dispatchers.IO) {
        try {
            val conn = URL(CREATOR_LOGO_URL).openConnection()
            conn.connectTimeout = 4_000
            conn.readTimeout = 4_000
            conn.getInputStream().use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
        } catch (_: Exception) {
            null
        }?.asSoftwareBitmapOrSelf()
        ?: decodeDrawable(context, R.drawable.eazpire_logo)
        ?: decodeDrawable(context, R.drawable.ic_launcher_foreground)
        ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
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
    val paint =
        android.graphics.Paint().apply {
            shader =
                android.graphics.LinearGradient(
                    0f,
                    0f,
                    0f,
                    height.toFloat(),
                    intArrayOf(0x660A0514.toInt(), 0x9905020F.toInt()),
                    floatArrayOf(0f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
        }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    return bitmap
}

/**
 * Lightweight splash assets — no per-pixel cell grids (OOM/crash on cold start with ShopScreen).
 * Never recycle bitmaps backing [androidx.compose.ui.graphics.ImageBitmap].
 */
private suspend fun buildSplashAssets(context: Context, screenW: Int, screenH: Int): SplashAssets? {
    val (decodeW, decodeH) = cappedDecodeSize(screenW, screenH)
    val galaxySrc = decodeDrawable(context, R.drawable.galaxy_nebula_bg) ?: return null
    val shopBmp = decodeDrawable(context, R.drawable.eazpire_logo) ?: return null

    return try {
        val creatorBmp = loadCreatorLogoBitmap(context)
        val galaxyBmp = buildGalaxyBitmap(galaxySrc, decodeW, decodeH)
        val layout = computeLogoLayout(screenW.toFloat(), screenH.toFloat(), shopBmp, creatorBmp)
        val shopImage = shopBmp.asImageBitmap()
        val creatorImage =
            if (creatorBmp === shopBmp) shopImage else creatorBmp.asImageBitmap()
        SplashAssets(
            logoLayout = layout,
            galaxyImage = galaxyBmp.asImageBitmap(),
            shopLogo = shopImage,
            creatorLogo = creatorImage,
        )
    } catch (e: OutOfMemoryError) {
        Log.e(TAG, "Splash OOM", e)
        null
    } catch (e: Exception) {
        Log.e(TAG, "Splash build failed", e)
        null
    } finally {
        galaxySrc.recycle()
    }
}

/**
 * App launch splash: shop logo on white → crossfade to galaxy + creator logo.
 * (Pixel-wipe removed — too heavy alongside parallel ShopScreen init.)
 */
@Composable
fun AppSplashOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var assets by remember { mutableStateOf<SplashAssets?>(null) }
    var elapsedMs by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val dm = context.resources.displayMetrics
        val screenW = dm.widthPixels.coerceAtLeast(1)
        val screenH = dm.heightPixels.coerceAtLeast(1)
        val built =
            withContext(Dispatchers.Default) {
                buildSplashAssets(context, screenW, screenH)
            }
        if (built == null) {
            done = true
            onFinished()
            return@LaunchedEffect
        }
        assets = built
        val start = System.currentTimeMillis()
        val total = SplashTimings.totalMs
        while (true) {
            elapsedMs = (System.currentTimeMillis() - start).toFloat().coerceAtMost(total.toFloat())
            if (elapsedMs >= total) break
            delay(16)
        }
        done = true
        onFinished()
    }

    if (done && assets == null) return

    val a = assets
    if (a == null) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(White),
        )
        return
    }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { /* block touches during splash */ }
                },
    ) {
        val w = size.width
        val h = size.height
        val t = elapsedMs
        val l = a.logoLayout

        if (t < SplashTimings.HOLD_MS) {
            drawRect(White, topLeft = Offset.Zero, size = Size(w, h))
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
        val sharp = easeOutCubic(settleT)

        drawRect(White, topLeft = Offset.Zero, size = Size(w, h))

        val galaxyAlpha = max(eased, sharp)
        if (galaxyAlpha > 0.01f) {
            drawImage(
                image = a.galaxyImage,
                dstSize = IntSize(w.toInt(), h.toInt()),
                alpha = galaxyAlpha,
                filterQuality = FilterQuality.High,
            )
        }

        val shopAlpha = (1f - eased) * (1f - sharp * 0.85f)
        if (shopAlpha > 0.01f) {
            drawImage(
                image = a.shopLogo,
                dstOffset = IntOffset(l.x.toInt(), l.y.toInt()),
                dstSize = IntSize(l.w.toInt(), l.shopH.toInt()),
                alpha = shopAlpha,
                filterQuality = FilterQuality.High,
            )
        }

        val creatorAlpha = max(eased * 0.35f, sharp)
        if (creatorAlpha > 0.01f) {
            drawImage(
                image = a.creatorLogo,
                dstOffset = IntOffset(l.x.toInt(), l.y.toInt()),
                dstSize = IntSize(l.w.toInt(), l.creatorH.toInt()),
                alpha = creatorAlpha.coerceIn(0f, 1f),
                filterQuality = FilterQuality.High,
            )
        }
    }
}
