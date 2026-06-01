package com.eazpire.creator.plp

import com.eazpire.creator.api.ShopifyProductsApi
import org.json.JSONArray

/**
 * PLP mock rotation URL list (parity with theme eaz-product-card-redesign.liquid
 * and worker getShopifyProducts.buildRotationImageUrls).
 */
data class PlpRotationBuild(
    val urls: List<String>,
    /** Color label per [urls] entry (from image alt), for personalized mock lookup. */
    val colorNames: List<String>,
)

object PlpRotationUrls {

    private const val MAX_SLOTS = 24

    fun fromProductJsonImages(imagesArr: JSONArray?): PlpRotationBuild {
        if (imagesArr == null || imagesArr.length() == 0) return PlpRotationBuild(emptyList(), emptyList())
        val images = (0 until imagesArr.length()).mapNotNull { i ->
            val img = imagesArr.optJSONObject(i) ?: return@mapNotNull null
            val src = img.optString("src").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val alt = img.optString("alt", "").trim()
            ShopifyProductsApi.ProductImage(src = src, variantIds = emptyList(), alt = alt.ifBlank { null })
        }
        return fromProductImages(images)
    }

    fun fromProductImages(images: List<ShopifyProductsApi.ProductImage>): PlpRotationBuild {
        if (images.isEmpty()) return PlpRotationBuild(emptyList(), emptyList())

        var primaryView = ""
        var primaryColor = ""
        var hasPreviewDefault = false
        val distinctColors = linkedSetOf<String>()

        for (pi in images) {
            val alt = (pi.alt ?: "").trim()
            if (alt.contains("preview-default", ignoreCase = true)) hasPreviewDefault = true
            val parts = alt.split("|")
            if (parts.size >= 2) {
                val color = parts[0].trim().lowercase()
                if (color.isNotBlank()) distinctColors.add(color)
                if (primaryView.isEmpty()) primaryView = parts[1].trim().lowercase()
                if (primaryColor.isEmpty() && alt.contains("preview-default", ignoreCase = true)) {
                    primaryColor = color
                }
            }
        }
        if (primaryColor.isEmpty() && distinctColors.size == 1) {
            primaryColor = distinctColors.first()
        }

        val isSingleColor = distinctColors.size <= 1
        val usedColors = mutableSetOf<String>()
        val seenUrls = linkedSetOf<String>()
        val urls = mutableListOf<String>()
        val colorNames = mutableListOf<String>()

        for (pi in images) {
            if (urls.size >= MAX_SLOTS) break
            val src = pi.src.trim()
            if (src.isBlank()) continue
            val alt = (pi.alt ?: "").trim()
            if (!alt.contains("|")) continue
            val parts = alt.split("|")
            val colorKey = parts.getOrNull(0)?.trim()?.lowercase().orEmpty()
            val mediaView = parts.getOrNull(1)?.trim()?.lowercase().orEmpty()
            if (colorKey.isEmpty()) continue

            val include = when {
                isSingleColor -> primaryColor.isBlank() || colorKey == primaryColor
                hasPreviewDefault -> alt.contains("preview-default", ignoreCase = true) &&
                    (primaryView.isBlank() || mediaView == primaryView)
                else -> (primaryView.isBlank() || mediaView == primaryView) && colorKey !in usedColors
            }
            if (!include) continue
            if (!isSingleColor) usedColors.add(colorKey)
            if (!seenUrls.add(src)) continue
            urls.add(src)
            colorNames.add(parts[0].trim())
        }

        if (urls.size >= 2) return PlpRotationBuild(urls, colorNames)

        if (!isSingleColor) {
            val seenVariant = linkedSetOf<Long>()
            for (pi in images) {
                if (urls.size >= MAX_SLOTS) break
                val src = pi.src.trim()
            if (src.isBlank()) continue
                val alt = (pi.alt ?: "").trim()
                if (!alt.contains("|")) continue
                val parts = alt.split("|")
                val mediaView = parts.getOrNull(1)?.trim()?.lowercase().orEmpty()
                if (primaryView.isNotBlank() && mediaView != primaryView) continue
                if (!seenUrls.add(src)) continue
                urls.add(src)
                colorNames.add(parts.getOrNull(0)?.trim().orEmpty())
            }
        }

        if (urls.isEmpty()) {
            val fallback = images.map { it.src.trim() }.filter { it.isNotBlank() }.distinct().take(MAX_SLOTS)
            return PlpRotationBuild(
                urls = fallback,
                colorNames = List(fallback.size) { "" }
            )
        }

        return PlpRotationBuild(urls, colorNames)
    }
}
