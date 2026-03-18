package com.eazpire.creator.ui.creator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.pow

private val FALLBACK_URLS = listOf(
    "https://placehold.co/400x400/1f2937/f97316?text=Design",
    "https://placehold.co/400x400/111827/fb923c?text=Creator"
)

private const val DENSITY = 6
private const val PARTICLE_OPACITY = 0.35f
private const val BG_COLOR = 0xFF0B0F18.toInt()

private fun easeOutCubic(t: Float) = 1f - (1f - t).pow(3)

private data class Pixel(val x: Float, val y: Float, val r: Int, val g: Int, val b: Int, val a: Float)
private data class SampledImage(val pixels: List<Pixel>, val bitmap: Bitmap?, val ox: Float, val oy: Float, val iw: Int, val ih: Int)

private suspend fun loadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val conn = URL(url).openConnection()
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.getInputStream().use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }
}

private fun sampleImage(bitmap: Bitmap?, canvasW: Float, canvasH: Float, sampleStep: Int): SampledImage? {
    if (bitmap == null) return null
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return null

    val scale = minOf((canvasW - 20) / w, (canvasH - 20) / h, 1f)
    val iw = (w * scale).toInt().coerceAtLeast(1)
    val ih = (h * scale).toInt().coerceAtLeast(1)
    val ox = (canvasW - iw) / 2
    val oy = (canvasH - ih) / 2

    val scaled = Bitmap.createScaledBitmap(bitmap, iw, ih, true)
    val pixels = mutableListOf<Pixel>()

    for (y in 0 until ih step sampleStep) {
        for (x in 0 until iw step sampleStep) {
            val px = scaled.getPixel(x, y)
            val a = (px shr 24 and 0xFF) / 255f
            if (a > 0.1f) {
                pixels.add(
                    Pixel(
                        ox + x,
                        oy + y,
                        px shr 16 and 0xFF,
                        px shr 8 and 0xFF,
                        px and 0xFF,
                        a
                    )
                )
            }
        }
    }
    return SampledImage(pixels, scaled, ox, oy, iw, ih)
}

private fun createRows(pixels: List<Pixel>): List<List<Pixel>> {
    val byRow = pixels.groupBy { (it.y / 4).toInt() * 4 }
    return byRow.keys.sorted().map { byRow[it]!! }
}

/**
 * Drawer Aquarium 1:1 wie Web particle-reveal.js:
 * Phase 1: Pixel schweben von unten nach oben, Reihe für Reihe → echtes Bild
 * Phase 2: Dissolve – parallel beginnt nächstes Design mit Assemble
 */
@Composable
fun DrawerAquarium(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val designUrls = remember { mutableStateListOf<String>() }
    var poolIndex by remember { mutableIntStateOf(0) }
    var currentSampled by remember { mutableStateOf<SampledImage?>(null) }
    var nextSampled by remember { mutableStateOf<SampledImage?>(null) }
    var phase by remember { mutableStateOf("assemble") }
    var startTime by remember { mutableFloatStateOf(0f) }
    var dissolveStartTime by remember { mutableFloatStateOf(0f) }
    var rows by remember { mutableStateOf<List<List<Pixel>>>(emptyList()) }
    var nextRows by remember { mutableStateOf<List<List<Pixel>>>(emptyList()) }
    var stagger by remember { mutableFloatStateOf(0f) }
    var travelDuration by remember { mutableFloatStateOf(0.65f) }
    var dissolveDuration by remember { mutableFloatStateOf(0.75f) }
    var nextStagger by remember { mutableFloatStateOf(0f) }
    var nextTravelDuration by remember { mutableFloatStateOf(0.65f) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(Size(280f, 800f)) }
    val scope = rememberCoroutineScope()
    val api = remember { CreatorApi() }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        try {
            val urls = withContext(Dispatchers.IO) {
                val r = api.listPublic(40)
                if (r.optBoolean("ok", false)) {
                    val items = r.optJSONArray("items") ?: return@withContext emptyList<String>()
                    (0 until items.length()).mapNotNull { i ->
                        val it = items.optJSONObject(i)
                        (it?.optString("preview_url") ?: it?.optString("original_url"))
                            ?.takeIf { u -> u.startsWith("http") }
                    }
                } else emptyList()
            }
            designUrls.clear()
            designUrls.addAll(if (urls.isNotEmpty()) urls.shuffled() else FALLBACK_URLS)
        } catch (_: Exception) {
            designUrls.clear()
            designUrls.addAll(FALLBACK_URLS)
        }
    }

    LaunchedEffect(designUrls.size, designUrls.isEmpty(), canvasSize.width, canvasSize.height, poolIndex, phase) {
        if (designUrls.isEmpty() || canvasSize.width < 10 || canvasSize.height < 10) return@LaunchedEffect
        val url = designUrls[poolIndex % designUrls.size]
        val bmp = loadBitmap(url)
        val w = canvasSize.width
        val h = canvasSize.height
        val sampled = sampleImage(bmp, w, h, DENSITY)
        if (sampled != null && sampled.pixels.isNotEmpty()) {
            val rowList = createRows(sampled.pixels)
            val numRows = rowList.size
            val totalDuration = (numRows * 0.11f).coerceIn(1.5f, 5f)
            travelDuration = (totalDuration * 0.18f).coerceAtMost(0.7f)
            dissolveDuration = (numRows * 0.05f).coerceIn(0.75f, 2.5f)
            stagger = if (numRows > 1) (totalDuration - travelDuration) / (numRows - 1) else 0f

            if (currentSampled == null) {
                currentSampled = sampled
                rows = rowList
                phase = "assemble"
                startTime = elapsed
            } else if (phase == "dissolve" && nextSampled == null) {
                nextSampled = sampled
                nextRows = rowList
                val nextTotal = (numRows * 0.11f).coerceIn(1.5f, 5f)
                nextTravelDuration = (nextTotal * 0.18f).coerceAtMost(0.7f)
                nextStagger = if (numRows > 1) (nextTotal - nextTravelDuration) / (numRows - 1) else 0f
            }
        }
    }

    LaunchedEffect(Unit) {
        var last = System.currentTimeMillis()
        while (true) {
            delay(16)
            val now = System.currentTimeMillis()
            elapsed += (now - last) / 1000f
            last = now
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
    ) {
        val w = size.width
        val h = size.height
        val now = elapsed
        val startY = h + 15
        val r = (DENSITY + 2) / 2f

        drawRect(Color(BG_COLOR))

        if (currentSampled == null || rows.isEmpty()) return@Canvas

        val sampled = currentSampled!!
        val dissolveElapsed = if (phase == "dissolve") now - dissolveStartTime else 0f
        val numRows = rows.size
        val fadeDuration = 0.35f
        val dissolveStagger = if (numRows > 1) (dissolveDuration - fadeDuration) / (numRows - 1) else 0f
        val totalDissolveTime = (numRows - 1) * dissolveStagger + fadeDuration
        val lastRowStartTime = (numRows - 1) * dissolveStagger

        if (phase == "assemble") {
            var allComplete = true
            var revealY = 0f

            for (ri in rows.indices) {
                val rowPixels = rows[ri]
                val rowDelay = ri * stagger
                val localTime = (now - startTime) - rowDelay
                val rowComplete = localTime >= travelDuration
                if (!rowComplete) allComplete = false

                var rowBottom = 0f
                for (p in rowPixels) rowBottom = maxOf(rowBottom, p.y + r / 2)

                if (rowComplete) {
                    revealY = maxOf(revealY, rowBottom)
                } else {
                    for (p in rowPixels) {
                        if (localTime < 0) continue
                        val drawY = if (localTime >= travelDuration) {
                            p.y
                        } else {
                            val t = (localTime / travelDuration).coerceIn(0f, 1f)
                            startY + (p.y - startY) * easeOutCubic(t)
                        }
                        drawRect(
                            Color(p.r / 255f, p.g / 255f, p.b / 255f, p.a * PARTICLE_OPACITY),
                            topLeft = Offset(p.x - r / 2, drawY - r / 2),
                            size = Size(r, r)
                        )
                    }
                }
            }

            if (revealY > 0 && sampled.bitmap != null) {
                clipRect(left = 0f, top = 0f, right = w, bottom = revealY) {
                    drawImage(
                        sampled.bitmap.asImageBitmap(),
                        topLeft = Offset(sampled.ox, sampled.oy),
                        alpha = PARTICLE_OPACITY
                    )
                }
            }

            if (allComplete) {
                scope.launch {
                    phase = "dissolve"
                    dissolveStartTime = elapsed
                    poolIndex++
                }
            }
        } else if (phase == "dissolve") {
            val nextElapsed = maxOf(0f, dissolveElapsed - lastRowStartTime)

            if (nextRows.isNotEmpty() && nextSampled != null) {
                val next = nextSampled!!
                var nextRevealY = 0f
                for (nri in nextRows.indices) {
                    val nextRowPixels = nextRows[nri]
                    val nextRowDelay = nri * nextStagger
                    val nextLocalTime = nextElapsed - nextRowDelay
                    val nextRowComplete = nextLocalTime >= nextTravelDuration

                    var nextRowBottom = 0f
                    for (p in nextRowPixels) nextRowBottom = maxOf(nextRowBottom, p.y + r / 2)

                    if (nextRowComplete) {
                        nextRevealY = maxOf(nextRevealY, nextRowBottom)
                    } else {
                        for (np in nextRowPixels) {
                            if (nextLocalTime < 0) continue
                            val nDrawY = if (nextLocalTime >= nextTravelDuration) {
                                np.y
                            } else {
                                val nt = (nextLocalTime / nextTravelDuration).coerceIn(0f, 1f)
                                startY + (np.y - startY) * easeOutCubic(nt)
                            }
                            drawRect(
                                Color(np.r / 255f, np.g / 255f, np.b / 255f, np.a * PARTICLE_OPACITY),
                                topLeft = Offset(np.x - r / 2, nDrawY - r / 2),
                                size = Size(r, r)
                            )
                        }
                    }
                }
                if (nextRevealY > 0 && next.bitmap != null) {
                    clipRect(left = 0f, top = 0f, right = w, bottom = nextRevealY) {
                        drawImage(
                            next.bitmap.asImageBitmap(),
                            topLeft = Offset(next.ox, next.oy),
                            alpha = PARTICLE_OPACITY
                        )
                    }
                }
            }

            for (ri in rows.indices) {
                val rowPixels = rows[ri]
                val rowDelay = ri * dissolveStagger
                val localTime = dissolveElapsed - rowDelay

                for (p in rowPixels) {
                    val alpha = when {
                        localTime < 0 -> p.a * PARTICLE_OPACITY
                        localTime >= fadeDuration -> continue
                        else -> p.a * PARTICLE_OPACITY * (1 - easeOutCubic(localTime / fadeDuration))
                    }
                    drawRect(
                        Color(p.r / 255f, p.g / 255f, p.b / 255f, alpha),
                        topLeft = Offset(p.x - r / 2, p.y - r / 2),
                        size = Size(r, r)
                    )
                }
            }

            if (dissolveElapsed >= totalDissolveTime) {
                scope.launch {
                    if (nextRows.isNotEmpty() && nextSampled != null) {
                        currentSampled = nextSampled
                        rows = nextRows
                        stagger = nextStagger
                        travelDuration = nextTravelDuration
                        val promotedRows = rows.size
                        dissolveDuration = (promotedRows * 0.05f).coerceIn(0.75f, 2.5f)
                        startTime = dissolveStartTime + lastRowStartTime
                        nextSampled = null
                        nextRows = emptyList()
                        phase = "assemble"
                    } else {
                        phase = "assemble"
                        startTime = elapsed
                        currentSampled = null
                        rows = emptyList()
                    }
                }
            }
        }
    }
}
