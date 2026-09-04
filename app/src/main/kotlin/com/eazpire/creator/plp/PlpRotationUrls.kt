package com.eazpire.creator.plp

import com.eazpire.creator.api.ShopifyProductsApi
import org.json.JSONArray

/**
 * PLP mock rotation URL list (parity with theme eaz-product-card-redesign.liquid
 * and worker buildPlpRotationUrls).
 */
data class PlpRotationBuild(
    val urls: List<String>,
    /** Color/group label per [urls] entry (from image alt), for personalized mock lookup. */
    val colorNames: List<String>,
    /** Per-color view URLs for left thumbs (IDEA-065 / PARITY-PLP-CARD-001). Keys lowercase. */
    val viewsByColor: Map<String, List<String>> = emptyMap(),
)

object PlpRotationUrls {

    private const val MAX_SLOTS = 24
    private const val MAX_VIEW_COLORS = 8
    private const val MAX_VIEWS_PER_COLOR = 4
    private val APPAREL_VIEW_HINTS = setOf("back", "right", "left", "folded", "detail")

    fun viewsByColorFromImages(images: List<ShopifyProductsApi.ProductImage>): Map<String, List<String>> {
        val map = linkedMapOf<String, MutableList<String>>()
        for (pi in images) {
            val alt = (pi.alt ?: "").trim()
            if (!alt.contains("|")) continue
            val color = alt.split("|").firstOrNull()?.trim()?.lowercase().orEmpty()
            if (color.isBlank()) continue
            val list = map[color]
            if (list == null) {
                if (map.size >= MAX_VIEW_COLORS) continue
                map[color] = mutableListOf()
            }
            val bucket = map[color] ?: continue
            if (bucket.size >= MAX_VIEWS_PER_COLOR) continue
            val src = pi.src.trim()
            if (src.isNotBlank() && src !in bucket) bucket.add(src)
        }
        return map
    }

    fun fromProductJsonImages(
        imagesArr: JSONArray?,
        productKey: String? = null,
        preferredLifestyleView: String? = null,
    ): PlpRotationBuild {
        if (imagesArr == null || imagesArr.length() == 0) return PlpRotationBuild(emptyList(), emptyList())
        val images = (0 until imagesArr.length()).mapNotNull { i ->
            val img = imagesArr.optJSONObject(i) ?: return@mapNotNull null
            val src = img.optString("src").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val alt = img.optString("alt", "").trim()
            ShopifyProductsApi.ProductImage(src = src, variantIds = emptyList(), alt = alt.ifBlank { null })
        }
        return fromProductImages(images, productKey, preferredLifestyleView)
    }

    fun isPhotopaperLikeProductKey(productKey: String?): Boolean {
        val pk = productKey?.trim()?.lowercase().orEmpty()
        if (pk.isBlank()) return false
        if (pk.contains("photopaper")) return true
        if (pk.contains("poster") && !pk.contains("frame")) return true
        return false
    }

    /** PDP poster AR — same signals as web eaz-product-card-redesign (product_key, alt layout, type/title hints). */
    fun isPosterArEligible(
        productKey: String?,
        images: List<ShopifyProductsApi.ProductImage>,
        productType: String? = null,
        title: String? = null,
    ): Boolean {
        if (isPhotopaperLikeProductKey(productKey)) return true
        if (isPhotopaperLikeAltLayout(images)) return true
        val typeHint = productType?.trim()?.lowercase().orEmpty()
        if (typeHint.contains("photopaper") || (typeHint.contains("poster") && !typeHint.contains("frame"))) {
            return true
        }
        val titleHint = title?.trim()?.lowercase().orEmpty()
        if (titleHint.contains("photopaper") || (titleHint.contains("poster") && !titleHint.contains("frame"))) {
            return true
        }
        return false
    }

    fun isPhotopaperLikeAltLayout(images: List<ShopifyProductsApi.ProductImage>): Boolean {
        var hasStructured = false
        for (pi in images) {
            val alt = (pi.alt ?: "").trim()
            if (!alt.contains("|")) continue
            hasStructured = true
            val view = alt.split("|").getOrNull(1)?.trim()?.lowercase().orEmpty()
            if (view.isBlank()) continue
            if (APPAREL_VIEW_HINTS.any { h -> view == h || view.startsWith(h) }) return false
        }
        if (!hasStructured) return false
        return images.any { pi ->
            val alt = (pi.alt ?: "").trim().lowercase()
            alt.contains("context_1") || alt.contains("context_2")
        }
    }

    private fun shouldUsePhotopaperSizeRotation(
        images: List<ShopifyProductsApi.ProductImage>,
        productKey: String?,
    ): Boolean = isPhotopaperLikeProductKey(productKey) || isPhotopaperLikeAltLayout(images)

    private fun buildPhotopaperRotationUrls(images: List<ShopifyProductsApi.ProductImage>): PlpRotationBuild {
        val usedGroups = linkedSetOf<String>()
        val seenUrls = linkedSetOf<String>()
        val urls = mutableListOf<String>()
        val colorNames = mutableListOf<String>()

        fun tryAdd(pi: ShopifyProductsApi.ProductImage, groupKey: String): Boolean {
            if (urls.size >= MAX_SLOTS || groupKey.isBlank() || groupKey in usedGroups) return false
            val src = pi.src.trim()
            if (src.isBlank() || src in seenUrls) return false
            usedGroups.add(groupKey)
            seenUrls.add(src)
            urls.add(src)
            colorNames.add(pi.alt?.split("|")?.firstOrNull()?.trim().orEmpty())
            return true
        }

        for (pi in images) {
            val alt = (pi.alt ?: "").trim()
            if (!alt.contains("preview-default", ignoreCase = true)) continue
            val groupKey = alt.split("|").firstOrNull()?.trim()?.lowercase().orEmpty()
            tryAdd(pi, groupKey)
        }

        if (urls.size < 2) {
            for (pi in images) {
                val alt = (pi.alt ?: "").trim()
                if (!alt.contains("|")) continue
                val parts = alt.split("|")
                val groupKey = parts.getOrNull(0)?.trim()?.lowercase().orEmpty()
                val view = parts.getOrNull(1)?.trim()?.lowercase().orEmpty()
                if (view != "front" && !view.startsWith("front")) continue
                tryAdd(pi, groupKey)
            }
        }

        return PlpRotationBuild(urls, colorNames, viewsByColorFromImages(images))
    }

    /**
     * Collection handle → preferred lifestyle alt view (parity with web data-eaz-lifestyle-audience).
     */
    fun preferredLifestyleViewForCollection(collectionHandle: String?): String? =
        when (collectionHandle?.trim()?.lowercase()) {
            "women", "woman" -> "lifestyle-female"
            "men", "man" -> "lifestyle-male"
            else -> null
        }

    fun fromProductImages(
        images: List<ShopifyProductsApi.ProductImage>,
        productKey: String? = null,
        preferredLifestyleView: String? = null,
    ): PlpRotationBuild {
        if (images.isEmpty()) return PlpRotationBuild(emptyList(), emptyList())
        val views = viewsByColorFromImages(images)

        if (shouldUsePhotopaperSizeRotation(images, productKey)) {
            val photopaper = buildPhotopaperRotationUrls(images)
            if (photopaper.urls.size >= 1) {
                return photopaper.copy(viewsByColor = views.ifEmpty { photopaper.viewsByColor })
            }
        }

        var primaryView = ""
        var primaryColor = ""
        var hasPreviewDefault = false
        val distinctColors = linkedSetOf<String>()
        val lifestyleViewsPresent = linkedSetOf<String>()

        for (pi in images) {
            val alt = (pi.alt ?: "").trim()
            if (alt.contains("preview-default", ignoreCase = true)) hasPreviewDefault = true
            val parts = alt.split("|")
            if (parts.size >= 2) {
                val color = parts[0].trim().lowercase()
                val view = parts[1].trim().lowercase()
                if (color.isNotBlank()) distinctColors.add(color)
                if (view == "lifestyle" || view.startsWith("lifestyle-")) {
                    lifestyleViewsPresent.add(view)
                }
                if (primaryView.isEmpty()) primaryView = view
                if (primaryColor.isEmpty() && alt.contains("preview-default", ignoreCase = true)) {
                    primaryColor = color
                    primaryView = view
                }
            }
        }
        if (primaryColor.isEmpty() && distinctColors.size == 1) {
            primaryColor = distinctColors.first()
        }

        val pref = preferredLifestyleView?.trim()?.lowercase().orEmpty()
        if (pref.isNotBlank() && lifestyleViewsPresent.contains(pref)) {
            primaryView = pref
        } else if (lifestyleViewsPresent.size > 1) {
            val female = "lifestyle-female"
            val male = "lifestyle-male"
            when {
                lifestyleViewsPresent.contains(female) && lifestyleViewsPresent.contains(male) -> {
                    val seed = (productKey ?: primaryColor).hashCode()
                    primaryView = if (seed and 1 == 0) female else male
                }
                lifestyleViewsPresent.contains(female) -> primaryView = female
                lifestyleViewsPresent.contains(male) -> primaryView = male
                lifestyleViewsPresent.contains("lifestyle") -> primaryView = "lifestyle"
            }
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

        if (urls.size >= 2) return PlpRotationBuild(urls, colorNames, views)

        if (!isSingleColor) {
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
                colorNames = List(fallback.size) { "" },
                viewsByColor = views,
            )
        }

        return PlpRotationBuild(urls, colorNames, views)
    }
}
