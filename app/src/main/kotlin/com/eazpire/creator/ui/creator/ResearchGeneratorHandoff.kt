package com.eazpire.creator.ui.creator

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Research → Generator handoff. Analysis fields only — never invented sales.
 *
 * Crop heuristic matches `src/features/eazyResearch/generatorHandoff.js`:
 * center-chest rectangle on Amazon garment mocks
 * (28% from left, 18% from top, 44% × 38%). Fail → original.
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
    const val X = 0.28f
    const val Y = 0.18f
    const val W = 0.44f
    const val H = 0.38f

    fun rect(width: Int, height: Int): Rect? {
        if (width < 8 || height < 8) return null
        val x = (width * X).toInt().coerceAtLeast(0)
        val y = (height * Y).toInt().coerceAtLeast(0)
        val w = (width * W).toInt().coerceAtLeast(2)
        val h = (height * H).toInt().coerceAtLeast(2)
        if (x + w > width || y + h > height) return null
        return Rect(x, y, x + w, y + h)
    }

    fun crop(src: Bitmap): Bitmap {
        val r = rect(src.width, src.height) ?: return src
        return try {
            Bitmap.createBitmap(src, r.left, r.top, r.width(), r.height())
        } catch (_: Exception) {
            src
        }
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

fun amazonListingUrl(asin: String, marketplace: String): String {
    val host = marketplace.trim().ifBlank { "amazon.de" }
    return "https://www.$host/dp/${asin.trim()}"
}
