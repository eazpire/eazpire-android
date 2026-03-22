package com.eazpire.creator.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Lädt Produkte via Shopify Storefront API (Worker-Proxy) mit Design-Metafields.
 * Filter: Content Type, Design Type, Design Style – gleiche Werte wie im Web.
 */
class ShopifyProductsApi(
    private val workerUrl: String = "https://creator-engine.eazpire.workers.dev",
    private val storeUrl: String = "https://www.eazpire.com",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    data class ProductItem(
        val id: Long,
        val title: String,
        val handle: String,
        val images: List<String>,
        /** Variant-only images (preview view); use for collection card rotation. Falls back to images if empty. */
        val variantImages: List<String> = emptyList(),
        val url: String,
        val price: Double = 0.0,
        val compareAtPrice: Double? = null,
        val createdAt: String = "",
        val productType: String = "",
        val tags: List<String> = emptyList(),
        val vendor: String = "",
        val contentType: String = "",
        val designType: String = "",
        val designStyle: List<String> = emptyList(),
        val ratio: String = "",
        val designLanguage: String = ""
    )

    data class ProductsResult(
        val products: List<ProductItem>,
        val hasNextPage: Boolean,
        val nextCursor: String?
    )

    /**
     * Lädt Produkte einer Collection oder alle Produkte.
     * Primär: Storefront API (Worker). Fallback: products.json wenn leer/Fehler.
     */
    suspend fun getProducts(
        collectionHandle: String? = null,
        searchQuery: String? = null,
        limit: Int = 24,
        cursor: String? = null
    ): ProductsResult = withContext(Dispatchers.IO) {
        var result = fetchFromStorefrontApi(collectionHandle, searchQuery, limit, cursor)
        if (result.products.isEmpty() && collectionHandle != null && searchQuery.isNullOrBlank()) {
            result = fetchFromStorefrontApi(null, null, limit, cursor)
        }
        if (result.products.isEmpty() && searchQuery.isNullOrBlank()) {
            result = fetchFromProductsJson(collectionHandle, limit, cursor)
        }
        if (result.products.isEmpty()) return@withContext result
        // Wie Web: Enrichment wenn Design-Metadaten fehlen (Storefront oder products.json)
        val hasDesignMeta = result.products.any { it.contentType.isNotBlank() || it.designType.isNotBlank() || it.designStyle.isNotEmpty() }
        if (!hasDesignMeta) {
            val metafields = fetchMetafieldsFromWorker(result.products.map { it.handle })
            val enriched = result.products.map { p ->
                val mf = metafields[p.handle]
                if (mf != null) p.copy(
                    contentType = mf.contentType,
                    designType = mf.designType,
                    designStyle = mf.designStyle,
                    ratio = mf.ratio,
                    designLanguage = mf.designLanguage
                ) else p
            }
            return@withContext result.copy(products = enriched)
        }
        return@withContext result
    }

    private data class ProductMetafields(
        val contentType: String,
        val designType: String,
        val designStyle: List<String>,
        val ratio: String,
        val designLanguage: String
    )

    private fun fetchMetafieldsFromWorker(handles: List<String>): Map<String, ProductMetafields> {
        if (handles.isEmpty()) return emptyMap()
        val handlesParam = handles.take(50).joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
        val url = "$workerUrl/apps/creator-dispatch?op=get-storefront-metafields&handles=$handlesParam"
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
            if (!json.optBoolean("ok", false)) return emptyMap()
            val mfObj = json.optJSONObject("metafields") ?: return emptyMap()
            val keys = mfObj.keys()
            val result = mutableMapOf<String, ProductMetafields>()
            while (keys.hasNext()) {
                val handle = keys.next()
                val obj = mfObj.optJSONObject(handle) ?: continue
                val styleArr = obj.optJSONArray("designStyle")
                val designStyle = if (styleArr != null) {
                    (0 until styleArr.length()).mapNotNull { styleArr.optString(it).takeIf { s -> s.isNotBlank() } }
                } else emptyList()
                result[handle] = ProductMetafields(
                    contentType = obj.optString("contentType", ""),
                    designType = obj.optString("designType", ""),
                    designStyle = designStyle,
                    ratio = obj.optString("ratio", ""),
                    designLanguage = obj.optString("designLanguage", "")
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun fetchFromStorefrontApi(
        collectionHandle: String?,
        searchQuery: String?,
        limit: Int,
        cursor: String?
    ): ProductsResult {
        val url = buildString {
            append("$workerUrl/apps/creator-dispatch?op=get-storefront-products")
            append("&limit=$limit")
            val sq = searchQuery?.trim().orEmpty()
            if (sq.isNotEmpty()) {
                append("&search_query=${java.net.URLEncoder.encode(sq, "UTF-8")}")
            } else if (!collectionHandle.isNullOrBlank()) {
                append("&collection_handle=${java.net.URLEncoder.encode(collectionHandle, "UTF-8")}")
            }
            cursor?.takeIf { it.isNotBlank() }?.let {
                append("&cursor=${java.net.URLEncoder.encode(it, "UTF-8")}")
            }
        }
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
            if (!json.optBoolean("ok", false)) {
                return ProductsResult(emptyList(), false, null)
            }
            val productsArr = json.optJSONArray("products") ?: JSONArray()
            val products = (0 until productsArr.length()).mapNotNull { i ->
                parseProduct(productsArr.optJSONObject(i))
            }
            ProductsResult(
                products = products,
                hasNextPage = json.optBoolean("hasNextPage", false),
                nextCursor = json.optString("nextCursor").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            ProductsResult(emptyList(), false, null)
        }
    }

    private fun fetchFromProductsJson(
        collectionHandle: String?,
        limit: Int,
        cursor: String?
    ): ProductsResult {
        val page = when {
            cursor == null || cursor == "page:1" -> 1
            cursor.startsWith("page:") -> cursor.removePrefix("page:").toIntOrNull() ?: 1
            else -> 1
        }
        val base = if (!collectionHandle.isNullOrBlank()) {
            "$storeUrl/collections/$collectionHandle/products.json"
        } else {
            "$storeUrl/products.json"
        }
        val url = "$base?limit=$limit&page=$page"
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
            val productsArr = json.optJSONArray("products") ?: JSONArray()
            val products = (0 until productsArr.length()).mapNotNull { i ->
                parseProductFromJson(productsArr.optJSONObject(i))
            }
            ProductsResult(
                products = products,
                hasNextPage = products.size >= limit,
                nextCursor = if (products.size >= limit) "page:${page + 1}" else null
            )
        } catch (_: Exception) {
            ProductsResult(emptyList(), false, null)
        }
    }

    /**
     * Filter images to variant-only (preview view). Alt: "Color|View" or "Color|View|preview-default".
     * Same logic as eaz-product-card-redesign.liquid and getStorefrontProducts.js.
     */
    private fun filterVariantImages(imagesArr: JSONArray): List<String> {
        var primaryView = ""
        var hasPreviewDefault = false
        for (j in 0 until imagesArr.length()) {
            val img = imagesArr.optJSONObject(j) ?: continue
            val alt = (img.optString("alt") ?: "").trim().lowercase()
            if (alt.contains("preview-default")) hasPreviewDefault = true
            if (alt.contains("|") && primaryView.isEmpty()) {
                val parts = (img.optString("alt") ?: "").trim().split("|")
                primaryView = (parts.getOrNull(1) ?: "").trim().lowercase()
            }
        }
        val variantImages = mutableListOf<String>()
        val usedColors = mutableSetOf<String>()
        for (j in 0 until imagesArr.length()) {
            val img = imagesArr.optJSONObject(j) ?: continue
            val src = img.optString("src").takeIf { it.isNotBlank() } ?: continue
            val alt = (img.optString("alt") ?: "").trim()
            if (alt.isEmpty() || !alt.contains("|")) continue
            val parts = alt.split("|")
            val colorKey = (parts.getOrNull(0) ?: "").trim().lowercase()
            val mediaView = (parts.getOrNull(1) ?: "").trim().lowercase()
            if (colorKey.isEmpty()) continue
            if (hasPreviewDefault && !alt.lowercase().contains("preview-default")) continue
            if (!hasPreviewDefault && primaryView.isNotEmpty() && mediaView != primaryView) continue
            val colorToken = "|$colorKey|"
            if (colorToken in usedColors) continue
            usedColors.add(colorToken)
            variantImages.add(src)
        }
        return variantImages
    }

    /**
     * Variant preview URLs from parsed product detail (same rules as [filterVariantImages] on REST images).
     * Used when [parseProductFromJson] cannot build a [ProductItem] (e.g. slightly different JSON).
     */
    private fun shopCardUrlsFromProductImages(images: List<ProductImage>): List<String> {
        if (images.isEmpty()) return emptyList()
        var primaryView = ""
        var hasPreviewDefault = false
        for (pi in images) {
            val alt = (pi.alt ?: "").trim().lowercase()
            if (alt.contains("preview-default")) hasPreviewDefault = true
            if (alt.contains("|") && primaryView.isEmpty()) {
                val parts = (pi.alt ?: "").trim().split("|")
                primaryView = (parts.getOrNull(1) ?: "").trim().lowercase()
            }
        }
        val variantUrls = mutableListOf<String>()
        val usedColors = mutableSetOf<String>()
        for (pi in images) {
            val src = pi.src.takeIf { it.isNotBlank() } ?: continue
            val alt = (pi.alt ?: "").trim()
            if (alt.isEmpty() || !alt.contains("|")) continue
            val parts = alt.split("|")
            val colorKey = (parts.getOrNull(0) ?: "").trim().lowercase()
            val mediaView = (parts.getOrNull(1) ?: "").trim().lowercase()
            if (colorKey.isEmpty()) continue
            if (hasPreviewDefault && !alt.lowercase().contains("preview-default")) continue
            if (!hasPreviewDefault && primaryView.isNotEmpty() && mediaView != primaryView) continue
            val colorToken = "|$colorKey|"
            if (colorToken in usedColors) continue
            usedColors.add(colorToken)
            variantUrls.add(src)
        }
        val allSrcs = images.map { it.src }.filter { it.isNotBlank() }
        return variantUrls.ifEmpty { listOfNotNull(allSrcs.firstOrNull()) }
    }

    /**
     * Same image URL list as shop [ProductItem.variantImages] / [CollectionScreen] cards:
     * storefront `products/{handle}.json` via worker `product-json`, then variant filter or first image.
     */
    suspend fun getShopCardImageUrls(handle: String): List<String> = withContext(Dispatchers.IO) {
        if (handle.isBlank()) return@withContext emptyList()
        val url = "$workerUrl/apps/creator-dispatch?op=product-json&handle=${java.net.URLEncoder.encode(handle, "UTF-8")}"
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
            if (json.has("ok") && !json.optBoolean("ok", true)) return@withContext emptyList()
            val productObj = json.optJSONObject("product") ?: return@withContext emptyList()
            val item = parseProductFromJson(productObj)
            if (item != null) {
                val v = item.variantImages
                val imgs = item.images
                return@withContext if (v.isNotEmpty()) v else imgs
            }
            val detail = parseProductDetail(productObj, handle) ?: return@withContext emptyList()
            shopCardUrlsFromProductImages(detail.images)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseProductFromJson(obj: JSONObject?): ProductItem? {
        if (obj == null) return null
        val id = obj.optLong("id", 0L)
        val title = obj.optString("title", "").takeIf { it.isNotBlank() } ?: return null
        val handle = obj.optString("handle", "").takeIf { it.isNotBlank() } ?: return null
        val images = mutableListOf<String>()
        val imagesArr = obj.optJSONArray("images")
        if (imagesArr != null) {
            for (j in 0 until imagesArr.length()) {
                val img = imagesArr.optJSONObject(j)
                val src = img?.optString("src")?.takeIf { it.isNotBlank() }
                if (src != null) images.add(src)
            }
        }
        if (images.isEmpty()) {
            val variants = obj.optJSONArray("variants")
            for (j in 0 until (variants?.length() ?: 0)) {
                val v = variants?.optJSONObject(j)
                val feat = v?.optJSONObject("featured_image")
                val src = feat?.optString("src")?.takeIf { it.isNotBlank() }
                if (src != null && src !in images) images.add(src)
            }
        }
        if (images.isEmpty()) return null

        val variantImages = if (imagesArr != null) {
            val filtered = filterVariantImages(imagesArr)
            if (filtered.isNotEmpty()) filtered else listOfNotNull(images.firstOrNull())
        } else listOfNotNull(images.firstOrNull())
        var price = 0.0
        val variants = obj.optJSONArray("variants")
        if (variants != null && variants.length() > 0) {
            val v = variants.optJSONObject(0)
            price = v?.optString("price", "0")?.toDoubleOrNull() ?: 0.0
        }
        val productType = obj.optString("product_type", "").trim()
        val tags = obj.optJSONArray("tags")?.let { t ->
            (0 until t.length()).mapNotNull { t.optString(it).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList()
        val vendor = obj.optString("vendor", "").trim()
        return ProductItem(
            id = id,
            title = title,
            handle = handle,
            images = images,
            variantImages = variantImages.ifEmpty { images.take(1) },
            url = "$storeUrl/products/$handle",
            price = price,
            compareAtPrice = null,
            createdAt = obj.optString("created_at", ""),
            productType = productType,
            tags = tags,
            vendor = vendor,
            contentType = "",
            designType = "",
            designStyle = emptyList(),
            ratio = "",
            designLanguage = ""
        )
    }

    /** Image with variant association and alt for color-based filtering (like web getMediaForColor). */
    data class ProductImage(val src: String, val variantIds: List<Long>, val alt: String? = null)

    /** Full product detail for PDP: variants, options, body_html. */
    data class ProductDetail(
        val id: Long,
        val title: String,
        val handle: String,
        val bodyHtml: String,
        val images: List<ProductImage>,
        val variants: List<ProductVariant>,
        val options: List<ProductOption>,
        val vendor: String,
        val productType: String,
        val url: String,
        /** Product key from metafields (e.g. unisex-softstyle-cotton-tee) for display name. */
        val productKey: String? = null
    ) {
        data class ProductVariant(
            val id: Long,
            val option1: String?,
            val option2: String?,
            val option3: String?,
            val price: Double,
            val compareAtPrice: Double?,
            val available: Boolean,
            val featuredImageSrc: String?
        )
        data class ProductOption(val name: String, val values: List<String>)
    }

    /**
     * Lädt ein einzelnes Produkt per Handle (für PDP).
     * Worker: op=product-json&handle=...
     */
    suspend fun getProductByHandle(handle: String): ProductDetail? = withContext(Dispatchers.IO) {
        if (handle.isBlank()) return@withContext null
        val url = "$workerUrl/apps/creator-dispatch?op=product-json&handle=${java.net.URLEncoder.encode(handle, "UTF-8")}"
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
            val productObj = json.optJSONObject("product") ?: json
            parseProductDetail(productObj, handle)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseProductDetail(obj: JSONObject?, handle: String): ProductDetail? {
        if (obj == null) return null
        val h = obj.optString("handle", "").takeIf { it.isNotBlank() } ?: handle
        val images = mutableListOf<ProductImage>()
        // Prefer media array (like web productData.media) – has alt for color|view format
        val mediaArr = obj.optJSONArray("media")
        if (mediaArr != null) {
            for (j in 0 until mediaArr.length()) {
                val m = mediaArr.optJSONObject(j)
                val mediaType = m?.optString("media_type", "image")
                if (mediaType != null && mediaType != "image") continue
                val src = m?.optString("src")
                    ?: m?.optJSONObject("preview_image")?.optString("src")
                    ?: m?.optJSONObject("image")?.optString("src")
                    ?: m?.optJSONObject("featured_image")?.optString("src")
                val srcVal = src?.takeIf { it.isNotBlank() } ?: continue
                val alt = m?.optString("alt")?.takeIf { it.isNotBlank() }
                images.add(ProductImage(src = srcVal, variantIds = emptyList(), alt = alt))
            }
        }
        // Fallback: images array (storefront JSON)
        if (images.isEmpty()) {
            val imagesArr = obj.optJSONArray("images")
            if (imagesArr != null) {
                for (j in 0 until imagesArr.length()) {
                    val img = imagesArr.optJSONObject(j)
                    val src = img?.optString("src")?.takeIf { it.isNotBlank() } ?: continue
                    val variantIdsArr = img.optJSONArray("variant_ids")
                    val variantIds = if (variantIdsArr != null) {
                        (0 until variantIdsArr.length()).mapNotNull { i ->
                            try {
                                val v = variantIdsArr.getLong(i)
                                if (v != 0L) v else null
                            } catch (_: Exception) { null }
                        }
                    } else emptyList()
                    val alt = img.optString("alt").takeIf { it.isNotBlank() }
                    images.add(ProductImage(src = src, variantIds = variantIds, alt = alt))
                }
            }
        }
        if (images.isEmpty()) {
            val variants = obj.optJSONArray("variants")
            for (j in 0 until (variants?.length() ?: 0)) {
                val v = variants?.optJSONObject(j)
                val feat = v?.optJSONObject("featured_image")
                val src = feat?.optString("src")?.takeIf { it.isNotBlank() }
                if (src != null && images.none { it.src == src }) {
                    val vid = v?.optLong("id", 0L) ?: 0L
                    images.add(ProductImage(src = src, variantIds = if (vid != 0L) listOf(vid) else emptyList(), alt = null))
                }
            }
        }
        if (images.isEmpty()) return null

        val variants = mutableListOf<ProductDetail.ProductVariant>()
        val variantsArr = obj.optJSONArray("variants")
        if (variantsArr != null) {
            for (j in 0 until variantsArr.length()) {
                val v = variantsArr.optJSONObject(j) ?: continue
                val feat = v.optJSONObject("featured_image")
                variants.add(
                    ProductDetail.ProductVariant(
                        id = v.optLong("id", 0L),
                        option1 = v.optString("option1").takeIf { it.isNotBlank() },
                        option2 = v.optString("option2").takeIf { it.isNotBlank() },
                        option3 = v.optString("option3").takeIf { it.isNotBlank() },
                        price = v.optString("price", "0").toDoubleOrNull() ?: 0.0,
                        compareAtPrice = v.optString("compare_at_price").takeIf { it.isNotBlank() }?.toDoubleOrNull(),
                        available = v.optBoolean("available", true),
                        featuredImageSrc = feat?.optString("src")?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        val options = mutableListOf<ProductDetail.ProductOption>()
        val optionsArr = obj.optJSONArray("options")
        if (optionsArr != null) {
            for (j in 0 until optionsArr.length()) {
                val o = optionsArr.optJSONObject(j) ?: continue
                val name = o.optString("name", "").takeIf { it.isNotBlank() } ?: continue
                val valuesArr = o.optJSONArray("values")
                val values = if (valuesArr != null) {
                    (0 until valuesArr.length()).mapNotNull { valuesArr.optString(it).takeIf { s -> s.isNotBlank() } }
                } else emptyList()
                options.add(ProductDetail.ProductOption(name = name, values = values))
            }
        }

        val productKey = obj.optJSONObject("metafields")
            ?.optJSONObject("custom")
            ?.optJSONObject("product_key")
            ?.optString("value")
            ?.takeIf { it.isNotBlank() }
            ?: obj.optJSONArray("metafields")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    (arr.optJSONObject(i)?.takeIf { it.optString("namespace") == "custom" && it.optString("key") == "product_key" })
                }.firstOrNull()?.optString("value")?.takeIf { it.isNotBlank() }
            }
        return ProductDetail(
            id = obj.optLong("id", 0L),
            title = obj.optString("title", ""),
            handle = h,
            bodyHtml = obj.optString("body_html", ""),
            images = images,
            variants = variants,
            options = options,
            vendor = obj.optString("vendor", ""),
            productType = obj.optString("product_type", ""),
            url = "$storeUrl/products/$h",
            productKey = productKey
        )
    }

    /**
     * Lädt Produkte für mehrere Kategorien.
     */
    suspend fun getProductsByCategories(
        categories: List<Pair<String, String>>,
        limitPerCategory: Int = 12
    ): Map<String, List<ProductItem>> = withContext(Dispatchers.IO) {
        categories.associate { (title, handle) ->
            val result = getProducts(collectionHandle = handle, limit = limitPerCategory)
            val fallback = if (result.products.isEmpty()) {
                getProducts(limit = limitPerCategory).products
            } else result.products
            handle to fallback
        }
    }

    private fun parseProduct(obj: JSONObject?): ProductItem? {
        if (obj == null) return null
        val handle = obj.optString("handle", "").takeIf { it.isNotBlank() } ?: return null
        val images = mutableListOf<String>()
        val imagesArr = obj.optJSONArray("images")
        if (imagesArr != null) {
            for (j in 0 until imagesArr.length()) {
                val src = imagesArr.optString(j).takeIf { it.isNotBlank() }
                if (src != null) images.add(src)
            }
        }
        if (images.isEmpty()) return null

        val variantImagesArr = obj.optJSONArray("variantImages")
        val variantImages = if (variantImagesArr != null) {
            (0 until variantImagesArr.length()).mapNotNull { variantImagesArr.optString(it).takeIf { s -> s.isNotBlank() } }
        } else emptyList()
        val designStyleArr = obj.optJSONArray("designStyle") ?: obj.optJSONArray("design_style")
        val designStyle = if (designStyleArr != null) {
            (0 until designStyleArr.length()).mapNotNull { designStyleArr.optString(it).takeIf { s -> s.isNotBlank() } }
        } else {
            val styleStr = obj.optString("designStyle", "").ifBlank { obj.optString("design_style", "") }
            if (styleStr.isNotBlank()) {
                when {
                    styleStr.trimStart().startsWith("[") -> try {
                        org.json.JSONArray(styleStr).let { arr ->
                            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                        }
                    } catch (_: Exception) { styleStr.split(",").map { it.trim() }.filter { it.isNotBlank() } }
                    else -> styleStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                }
            } else emptyList()
        }
        val tagsArr = obj.optJSONArray("tags")
        val tags = if (tagsArr != null) {
            (0 until tagsArr.length()).mapNotNull { tagsArr.optString(it).takeIf { s -> s.isNotBlank() } }
        } else emptyList()
        return ProductItem(
            id = obj.optLong("id", 0L),
            title = obj.optString("title", ""),
            handle = handle,
            images = images,
            variantImages = variantImages.ifEmpty { images.take(1) },
            url = obj.optString("url", "").ifBlank { "$storeUrl/products/$handle" },
            price = obj.optDouble("price", 0.0),
            compareAtPrice = null,
            createdAt = obj.optString("createdAt", ""),
            productType = obj.optString("productType", ""),
            tags = tags,
            vendor = obj.optString("vendor", ""),
            contentType = obj.optString("contentType", "").ifBlank { obj.optString("content_type", "") },
            designType = obj.optString("designType", "").ifBlank { obj.optString("design_type", "") },
            designStyle = designStyle,
            ratio = obj.optString("ratio", "").ifBlank { obj.optString("design_ratio", "") },
            designLanguage = obj.optString("designLanguage", "").ifBlank { obj.optString("design_language", "") }
        )
    }

    companion object {
        /**
         * Public worker op `list-active-shop-promotion-products` — maps to [ProductItem] for shop grid/carousel.
         */
        fun parseActivePromotionProductsResponse(json: JSONObject, storeBase: String = "https://www.eazpire.com"): List<ProductItem> {
            if (!json.optBoolean("ok", false)) return emptyList()
            val arr = json.optJSONArray("products") ?: return emptyList()
            val out = ArrayList<ProductItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val handle = o.optString("handle", "").trim()
                if (handle.isBlank()) continue
                val imgs = mutableListOf<String>()
                val imgArr = o.optJSONArray("images")
                if (imgArr != null) {
                    for (j in 0 until imgArr.length()) {
                        imgArr.optString(j, "").takeIf { it.isNotBlank() }?.let { imgs.add(it) }
                    }
                }
                val featured = o.optString("featured_image", "").ifBlank { null }
                if (imgs.isEmpty() && featured != null) imgs.add(featured)
                if (imgs.isEmpty()) continue
                val price = o.optDouble("price", 0.0)
                val compare = if (o.has("compare_at_price") && !o.isNull("compare_at_price")) {
                    o.optDouble("compare_at_price").takeIf { !it.isNaN() }
                } else {
                    null
                }
                out.add(
                    ProductItem(
                        id = o.optLong("id", 0L),
                        title = o.optString("title", handle),
                        handle = handle,
                        images = imgs,
                        variantImages = imgs.take(1),
                        url = "$storeBase/products/$handle",
                        price = price,
                        compareAtPrice = compare,
                        createdAt = "",
                        productType = "",
                        tags = emptyList(),
                        vendor = o.optString("vendor", ""),
                        contentType = "",
                        designType = "",
                        designStyle = emptyList(),
                        ratio = "",
                        designLanguage = ""
                    )
                )
            }
            return out
        }
    }
}
