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
            val parts = alt.split("|")
            val color = parts.firstOrNull()?.trim()?.lowercase().orEmpty()
            val view = parts.getOrNull(1)?.trim()?.lowercase().orEmpty()
            if (color.isBlank()) continue
            // Skip folded for PLP thumbs — prefer lifestyle/front/back only.
            if (view == "folded" || view == "folded_2" || view.startsWith("folded")) continue
            if (view.isNotBlank() &&
                view != "front" &&
                !view.startsWith("front") &&
                view != "lifestyle" &&
                !view.startsWith("lifestyle") &&
                view != "back"
            ) {
                continue
            }
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
        var hasFront = false
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
                if (view == "front" || view.startsWith("front")) hasFront = true
                if (primaryColor.isEmpty() &&
                    alt.contains("preview-default", ignoreCase = true) &&
                    !isApparelNonCardView(view)
                ) {
                    primaryColor = color
                    primaryView = view
                }
            }
        }
        if (primaryColor.isEmpty() && distinctColors.size == 1) {
            primaryColor = distinctColors.first()
        }

        // Prefer lifestyle → front; never keep folded/back as card primary when better exists.
        val pref = preferredLifestyleView?.trim()?.lowercase().orEmpty()
        when {
            pref.isNotBlank() && lifestyleViewsPresent.contains(pref) -> primaryView = pref
            lifestyleViewsPresent.contains("lifestyle-female") &&
                lifestyleViewsPresent.contains("lifestyle-male") -> {
                val seed = (productKey ?: primaryColor).hashCode()
                primaryView = if (seed and 1 == 0) "lifestyle-female" else "lifestyle-male"
            }
            lifestyleViewsPresent.contains("lifestyle-female") -> primaryView = "lifestyle-female"
            lifestyleViewsPresent.contains("lifestyle-male") -> primaryView = "lifestyle-male"
            lifestyleViewsPresent.contains("lifestyle") -> primaryView = "lifestyle"
            hasFront -> primaryView = "front"
            isApparelNonCardView(primaryView) -> primaryView = if (hasFront) "front" else ""
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

            // Variants only: one preferred view per color — never cycle folded/back.
            if (primaryView.isNotBlank() && mediaView != primaryView) continue
            if (isApparelNonCardView(mediaView) && mediaView != primaryView) continue

            val include = when {
                isSingleColor -> {
                    (primaryColor.isBlank() || colorKey == primaryColor) &&
                        (primaryView.isBlank() || mediaView == primaryView)
                }
                hasPreviewDefault -> alt.contains("preview-default", ignoreCase = true) &&
                    (primaryView.isBlank() || mediaView == primaryView) &&
                    colorKey !in usedColors
                else -> (primaryView.isBlank() || mediaView == primaryView) && colorKey !in usedColors
            }
            if (!include) continue
            if (!isSingleColor) usedColors.add(colorKey)
            if (!seenUrls.add(src)) continue
            urls.add(src)
            colorNames.add(parts[0].trim())
            // Single color: stop after preferred mock (no view rotation).
            if (isSingleColor) break
        }

        // Fallback: lifestyle preferred but missing for some colors → front per color.
        if (!isSingleColor && urls.size < 2 && primaryView.startsWith("lifestyle")) {
            for (pi in images) {
                if (urls.size >= MAX_SLOTS) break
                val src = pi.src.trim()
                if (src.isBlank()) continue
                val alt = (pi.alt ?: "").trim()
                if (!alt.contains("|")) continue
                val parts = alt.split("|")
                val colorKey = parts.getOrNull(0)?.trim()?.lowercase().orEmpty()
                val mediaView = parts.getOrNull(1)?.trim()?.lowercase().orEmpty()
                if (colorKey.isEmpty() || mediaView != "front") continue
                if (hasPreviewDefault && !alt.contains("preview-default", ignoreCase = true)) continue
                if (colorKey in usedColors) continue
                usedColors.add(colorKey)
                if (!seenUrls.add(src)) continue
                urls.add(src)
                colorNames.add(parts[0].trim())
            }
        }

        if (urls.size >= 1) return PlpRotationBuild(urls, colorNames, views)

        if (urls.isEmpty()) {
            // Last resort: first front, else any non-folded.
            for (pi in images) {
                val alt = (pi.alt ?: "").trim()
                val view = alt.split("|").getOrNull(1)?.trim()?.lowercase().orEmpty()
                if (view == "front" || view.startsWith("front")) {
                    val src = pi.src.trim()
                    if (src.isNotBlank()) {
                        return PlpRotationBuild(listOf(src), listOf(alt.split("|").firstOrNull()?.trim().orEmpty()), views)
                    }
                }
            }
            val fallback = images
                .filter { pi ->
                    val view = (pi.alt ?: "").split("|").getOrNull(1)?.trim()?.lowercase().orEmpty()
                    !isApparelNonCardView(view)
                }
                .map { it.src.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SLOTS)
            if (fallback.isNotEmpty()) {
                return PlpRotationBuild(
                    urls = fallback,
                    colorNames = List(fallback.size) { "" },
                    viewsByColor = views,
                )
            }
            val any = images.map { it.src.trim() }.filter { it.isNotBlank() }.distinct().take(MAX_SLOTS)
            return PlpRotationBuild(
                urls = any,
                colorNames = List(any.size) { "" },
                viewsByColor = views,
            )
        }

        return PlpRotationBuild(urls, colorNames, views)
    }

    private fun isApparelNonCardView(view: String): Boolean {
        val v = view.trim().lowercase()
        if (v.isEmpty()) return false
        return APPAREL_VIEW_HINTS.any { h -> v == h || v.startsWith("$h-") || v.startsWith("${h}_") } ||
            v == "folded_2" || v.startsWith("folded")
    }
}
