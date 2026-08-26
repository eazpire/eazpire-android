package com.eazpire.creator.ui.creator

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Research → Generator handoff. Analysis fields only — never invented sales.
 *
 * Crop heuristic matches `src/features/eazyResearch/printAreaCrop.js`:
 * Artwork-Bounding-Box auf dem Shirt-Mock (Kontrast zum Stoff, Rand ignorieren,
 * ~10 % Padding). Fallback: größere Brustzone als das alte 28/18/44/38.
 */
data class ResearchGeneratorHandoff(
    val imageUrl: String,
    val prompt: String,
    val topic: String,
    val subtopic: String,
    val tags: List<String>,
    val designType: String?,
    val language: String?,
    val asin: String,
    val marketplace: String,
)

object ResearchPrintArea {
    const val X = 0.2f
    const val Y = 0.12f
    const val W = 0.6f
    const val H = 0.56f
    const val ARTWORK_PAD = 0.1f
    const val EDGE_MARGIN = 0.12f
    const val GARMENT_THRESHOLD = 48.0
    const val MIN_ARTWORK_FRAC = 0.0035

    /** JVM-safe box (android.graphics.Rect fields are stubs in unit tests). */
    data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun width(): Int = right - left
        fun height(): Int = bottom - top
    }

    fun rect(width: Int, height: Int): PixelBox? {
        if (width < 8 || height < 8) return null
        val x = (width * X).roundToInt().coerceAtLeast(0)
        val y = (height * Y).roundToInt().coerceAtLeast(0)
        val w = (width * W).roundToInt().coerceAtLeast(2)
        val h = (height * H).roundToInt().coerceAtLeast(2)
        if (x + w > width || y + h > height) return null
        return PixelBox(x, y, x + w, y + h)
    }

    /**
     * ARGB packed pixels (same order as Bitmap.getPixels).
     * Keep in sync with printAreaCrop.js detectArtworkRect.
     */
    fun detectArtworkRect(width: Int, height: Int, argb: IntArray): PixelBox? {
        if (width < 8 || height < 8 || argb.size < width * height) return null
        val x0 = (width * EDGE_MARGIN).roundToInt()
        val x1 = (width * (1f - EDGE_MARGIN)).roundToInt()
        val y0 = (height * 0.12f).roundToInt()
        val y1 = (height * 0.78f).roundToInt()
        if (x1 - x0 < 4 || y1 - y0 < 4) return null

        val sampleR = ArrayList<Int>()
        val sampleG = ArrayList<Int>()
        val sampleB = ArrayList<Int>()
        val leftXa = (width * 0.12f).roundToInt()
        val leftXb = (width * 0.22f).roundToInt()
        val rightXa = (width * 0.78f).roundToInt()
        val rightXb = (width * 0.88f).roundToInt()
        val sy0 = (height * 0.28f).roundToInt()
        val sy1 = (height * 0.55f).roundToInt()
        for (y in sy0 until sy1) {
            for (x in leftXa until leftXb) {
                val c = argb[y * width + x]
                if (((c ushr 24) and 0xFF) < 16) continue
                sampleR.add((c ushr 16) and 0xFF)
                sampleG.add((c ushr 8) and 0xFF)
                sampleB.add(c and 0xFF)
            }
            for (x in rightXa until rightXb) {
                val c = argb[y * width + x]
                if (((c ushr 24) and 0xFF) < 16) continue
                sampleR.add((c ushr 16) and 0xFF)
                sampleG.add((c ushr 8) and 0xFF)
                sampleB.add(c and 0xFF)
            }
        }
        if (sampleR.size < 20) return null
        val gR = median(sampleR)
        val gG = median(sampleG)
        val gB = median(sampleB)

        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        var count = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val c = argb[y * width + x]
                if (((c ushr 24) and 0xFF) < 16) continue
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val dr = (r - gR).toDouble()
                val dg = (g - gG).toDouble()
                val db = (b - gB).toDouble()
                if (sqrt(dr * dr + dg * dg + db * db) <= GARMENT_THRESHOLD) continue
                count++
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
        val searchArea = (x1 - x0) * (y1 - y0)
        if (count < max(24, (searchArea * MIN_ARTWORK_FRAC).toInt())) return null
        if (maxX < minX || maxY < minY) return null

        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val padX = (bw * ARTWORK_PAD).roundToInt()
        val padY = (bh * ARTWORK_PAD).roundToInt()
        val x = (minX - padX).coerceIn(0, width - 2)
        val y = (minY - padY).coerceIn(0, height - 2)
        val w = (bw + padX * 2).coerceIn(2, width - x)
        val h = (bh + padY * 2).coerceIn(2, height - y)
        return PixelBox(x, y, x + w, y + h)
    }

    fun crop(src: Bitmap): Bitmap {
        val detected = detectFromBitmap(src)
        val r = detected ?: rect(src.width, src.height) ?: return src
        return try {
            Bitmap.createBitmap(src, r.left, r.top, r.width(), r.height())
        } catch (_: Exception) {
            src
        }
    }

    /** Normalized 0..1 crop for the influence overlay (artwork detect, else chest fallback). */
    fun defaultCropRect(src: Bitmap): CropRect {
        val r = detectFromBitmap(src) ?: rect(src.width, src.height) ?: return CropRect.FULL
        return fromPixelBox(r, src.width, src.height)
    }

    fun defaultCropRect(width: Int, height: Int, argb: IntArray): CropRect {
        val r = detectArtworkRect(width, height, argb) ?: rect(width, height) ?: return CropRect.FULL
        return fromPixelBox(r, width, height)
    }

    private fun fromPixelBox(r: PixelBox, width: Int, height: Int): CropRect {
        val iw = width.toFloat().coerceAtLeast(1f)
        val ih = height.toFloat().coerceAtLeast(1f)
        return CropRect(
            left = (r.left / iw).coerceIn(0f, 1f),
            top = (r.top / ih).coerceIn(0f, 1f),
            width = (r.width() / iw).coerceIn(0.02f, 1f),
            height = (r.height() / ih).coerceIn(0.02f, 1f),
        )
    }

    internal fun makeSyntheticShirtArgb(width: Int, height: Int, dx: Int, dy: Int, dw: Int, dh: Int): IntArray {
        val gray = 0xFFB4B4B4.toInt()
        val ink = 0xFF141414.toInt()
        val pixels = IntArray(width * height) { gray }
        for (y in dy until min(height, dy + dh)) {
            for (x in dx until min(width, dx + dw)) {
                if (x >= 0 && y >= 0) pixels[y * width + x] = ink
            }
        }
        return pixels
    }

    private fun detectFromBitmap(src: Bitmap): PixelBox? {
        val maxW = 160
        val scale = min(1f, maxW / src.width.toFloat())
        val sw = max(8, (src.width * scale).roundToInt())
        val sh = max(8, (src.height * scale).roundToInt())
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, sw, sh, true)
        } else {
            src
        }
        val pixels = IntArray(sw * sh)
        scaled.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        val r = detectArtworkRect(sw, sh, pixels) ?: return null
        if (scale >= 1f) return r
        val invX = src.width.toFloat() / sw
        val invY = src.height.toFloat() / sh
        val x = (r.left * invX).roundToInt().coerceIn(0, src.width - 2)
        val y = (r.top * invY).roundToInt().coerceIn(0, src.height - 2)
        val w = (r.width() * invX).roundToInt().coerceIn(2, src.width - x)
        val h = (r.height() * invY).roundToInt().coerceIn(2, src.height - y)
        return PixelBox(x, y, x + w, y + h)
    }

    private fun median(values: List<Int>): Int {
        if (values.isEmpty()) return 128
        val s = values.sorted()
        return s[s.size / 2]
    }
}

fun researchT2iPrompt(h: ResearchGeneratorHandoff): String {
    val parts = mutableListOf<String>()
    if (h.prompt.isNotBlank()) parts += h.prompt.trim()
    if (h.topic.isNotBlank()) parts += "Topic: ${h.topic.trim()}"
    if (h.subtopic.isNotBlank()) parts += "Subtopic: ${h.subtopic.trim()}"
    if (h.tags.isNotEmpty()) parts += "Tags: ${h.tags.joinToString(", ")}"
    h.designType?.takeIf { it.isNotBlank() }?.let {
        parts += "Design type: ${it.replace('_', ' ')}"
    }
    h.language?.takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }?.let {
        parts += "Language: $it"
    }
    return parts.joinToString("\n")
}

data class T2iDesignEntry(val label: String, val text: String)

fun t2iSlotLabel(index: Int): String = ('A' + index.coerceIn(0, 25)).toString()

fun t2iDesignEntryOrNull(index: Int, t2i: Boolean, h: ResearchGeneratorHandoff): T2iDesignEntry? {
    if (!t2i) return null
    val text = researchT2iPrompt(h)
    if (text.isBlank()) return null
    return T2iDesignEntry(t2iSlotLabel(index), text)
}

fun amazonListingUrl(asin: String, marketplace: String): String {
    val host = marketplace.trim().ifBlank { "amazon.de" }
    return "https://www.$host/dp/${asin.trim()}"
}
