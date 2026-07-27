package com.eazpire.creator.api

import com.eazpire.creator.plp.PlpRotationUrls
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
        /** Color name per [variantImages] slot (from image alt), for mock lookup. */
        val rotationColorNames: List<String> = emptyList(),
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
        val designLanguage: String = "",
        /** custom.creator (Storefront metafield enrich) */
        val creator: String = "",
        /** custom.product_key */
        val metaProductKey: String = "",
        /** custom.product_name (PAT/display name, shop filter „Produkt“) */
        val patProductName: String = "",
        /** custom.design_id */
        val designId: String = "",
        /** Worker `list-active-shop-promotion-products`: promotion end (ms) for countdown. */
        val promotionEndsAtMs: Long? = null,
        /** In-slot: higher price for strike-through. */
        val promoBeforePrice: Double? = null,
        /** Outside slot: next campaign window start (ms). Legacy; unused with 24/7 promos. */
        val promoNextWindowStartsAtMs: Long? = null,
        /** True when promo campaign active but outside display window (legacy; unused with 24/7). */
        val promoOutsideSlot: Boolean = false,
        /** Outside slot: lower promo price when the window opens (optional). */
        val promoPreviewPrice: Double? = null,
        /** Campaign not started yet — countdown to [promoCampaignStartsAtMs]. */
        val promoPrelaunch: Boolean = false,
        val promoCampaignStartsAtMs: Long? = null
    )

    data class ProductsResult(
        val products: List<ProductItem>,
        val hasNextPage: Boolean,
        val nextCursor: String?
    )

    /** Lightweight count from Shopify `/collections/{handle}.json`. */
    suspend fun getCollectionProductCount(collectionHandle: String): Int? =
        withContext(Dispatchers.IO) {
            if (collectionHandle.isBlank()) return@withContext null
            try {
                val url = "$storeUrl/collections/$collectionHandle.json"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                val collection = JSONObject(body).optJSONObject("collection") ?: return@withContext null
                val count =
                    collection.optInt("products_count", -1).takeIf { it >= 0 }
                        ?: collection.optInt("all_products_count", -1).takeIf { it >= 0 }
                count
            } catch (_: Exception) {
                null
            }
        }

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
        val needsMockMeta = result.products.any { it.metaProductKey.isBlank() || it.designId.isBlank() }
        if (!hasDesignMeta || needsMockMeta) {
            val metafields = fetchMetafieldsFromWorker(result.products.map { it.handle })
            val enriched = result.products.map { p ->
                val mf = metafields[p.handle]
                if (mf != null) p.copy(
                    contentType = mf.contentType,
                    designType = mf.designType,
                    designStyle = mf.designStyle,
                    ratio = mf.ratio,
                    designLanguage = mf.designLanguage,
                    creator = resolveCreatorDisplay(mf.creator.ifBlank { p.creator }, p.vendor),
                    metaProductKey = mf.productKey.ifBlank { p.metaProductKey },
                    designId = mf.designId.ifBlank { p.designId },
                    patProductName = mf.productName.ifBlank { p.patProductName }
                ) else p
            }
            return@withContext result.copy(products = enriched)
        }
        return@withContext result
    }

    /**
     * Merges worker `list-active-shop-promotion-products` into collection/search results (same as web overlay).
     */
    suspend fun mergeShopPromotionOverlay(
        products: List<ProductItem>,
        countryCode: String,
        creatorApi: CreatorApi
    ): List<ProductItem> = mergeShopPromotionOverlayProducts(products, countryCode, creatorApi)

    private data class ProductMetafields(
        val contentType: String,
        val designType: String,
        val designStyle: List<String>,
        val ratio: String,
        val designLanguage: String,
        val creator: String = "",
        val productKey: String = "",
        val designId: String = "",
        val productName: String = "",
        val ratingAvg: Double = 0.0,
        val ratingCount: Int = 0
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
                    designLanguage = obj.optString("designLanguage", ""),
                    creator = obj.optString("creator", ""),
                    productKey = obj.optString("productKey", ""),
                    designId = obj.optString("designId", ""),
                    productName = obj.optString("productName", ""),
                    ratingAvg = obj.optDouble("ratingAvg", 0.0),
                    ratingCount = obj.optInt("ratingCount", 0)
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

    private fun filterVariantImages(imagesArr: JSONArray): List<String> =
        PlpRotationUrls.fromProductJsonImages(imagesArr).urls

    private fun shopCardUrlsFromProductImages(
        images: List<ProductImage>,
        productKey: String? = null,
    ): List<String> =
        PlpRotationUrls.fromProductImages(images, productKey).urls

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
            shopCardUrlsFromProductImages(detail.images, detail.productKey)
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

        val rotation = if (imagesArr != null) {
            PlpRotationUrls.fromProductJsonImages(imagesArr, obj.optString("product_key", null).takeIf { it.isNotBlank() })
        } else {
            PlpRotationUrls.fromProductImages(
                images.map { src -> ProductImage(src = src, variantIds = emptyList()) }
            )
        }
        val variantImages = rotation.urls.ifEmpty { listOfNotNull(images.firstOrNull()) }
        val rotationColorNames = rotation.colorNames
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
            rotationColorNames = rotationColorNames,
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
            designLanguage = "",
            creator = "",
            metaProductKey = "",
            designId = ""
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
        val productKey: String? = null,
        val tags: List<String> = emptyList(),
        val creatorDisplay: String = "",
        val designIdMeta: String? = null,
        val ratingAvg: Double? = null,
        val ratingCount: Int? = null
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
     * Resolves a Shopify product handle from handle and/or Product GID.
     * Worker: op=product-json&handle=... or op=product-json&gid=...
     */
    suspend fun resolveProductHandle(handle: String? = null, gid: String? = null): String? = withContext(Dispatchers.IO) {
        handle?.trim()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        val cleanGid = gid?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext null
        val url = "$workerUrl/apps/creator-dispatch?op=product-json&gid=${java.net.URLEncoder.encode(cleanGid, "UTF-8")}"
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
            if (json.has("ok") && !json.optBoolean("ok", true)) return@withContext null
            json.optJSONObject("product")
                ?.optString("handle", "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
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
            if (json.has("ok") && !json.optBoolean("ok", true)) return@withContext null
            val productObj = json.optJSONObject("product") ?: return@withContext null
            val base = parseProductDetail(productObj, handle) ?: return@withContext null
            val mf = fetchMetafieldsFromWorker(listOf(base.handle))[base.handle]
            if (mf == null) return@withContext base
            base.copy(
                productKey = mf.productKey.takeIf { it.isNotBlank() } ?: base.productKey,
                creatorDisplay = mf.creator.ifBlank { base.creatorDisplay },
                designIdMeta = mf.designId.takeIf { it.isNotBlank() } ?: base.designIdMeta,
                ratingAvg = mf.ratingAvg.takeIf { it > 0.0 } ?: base.ratingAvg,
                ratingCount = mf.ratingCount.takeIf { it > 0 } ?: base.ratingCount
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Katalog für PDP-Karussells: gleiche Metafields wie Web (creator, product_key, design_id).
     */
    suspend fun getProductsWithFullMetafields(limit: Int = 100): ProductsResult = withContext(Dispatchers.IO) {
        val result = getProducts(limit = limit)
        val mf = fetchMetafieldsFromWorker(result.products.map { it.handle })
        val merged = result.products.map { p ->
            val m = mf[p.handle] ?: return@map p
            p.copy(
                contentType = m.contentType.ifBlank { p.contentType },
                designType = m.designType.ifBlank { p.designType },
                designStyle = if (p.designStyle.isEmpty()) m.designStyle else p.designStyle,
                ratio = m.ratio.ifBlank { p.ratio },
                designLanguage = m.designLanguage.ifBlank { p.designLanguage },
                creator = m.creator.ifBlank { p.creator },
                metaProductKey = m.productKey.ifBlank { p.metaProductKey },
                designId = m.designId.ifBlank { p.designId }
            )
        }
        result.copy(products = merged)
    }

    /** Storefront JSON uses dollar strings; worker Admin fallback uses integer cents (see productJsonByGid.js). */
    private fun parseJsonPrice(value: Any?): Double? {
        if (value == null || value == JSONObject.NULL) return null
        if (value is Number) {
            val d = value.toDouble()
            return if (d >= 100 && d == kotlin.math.floor(d)) d / 100.0 else d
        }
        val str = value.toString().trim()
        if (str.isBlank()) return null
        val d = str.toDoubleOrNull() ?: return null
        return if (!str.contains('.') && d >= 100) d / 100.0 else d
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
                        price = parseJsonPrice(v.opt("price")) ?: 0.0,
                        compareAtPrice = parseJsonPrice(v.opt("compare_at_price")),
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

        val tagsRaw = obj.optString("tags", "").trim()
        val tagsList = tagsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
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
            ?: tagsList.firstOrNull { it.startsWith("product_key:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        val designIdFromTags = tagsList.firstOrNull { it.startsWith("design_id:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val vendorStr = obj.optString("vendor", "")
        return ProductDetail(
            id = obj.optLong("id", 0L),
            title = obj.optString("title", ""),
            handle = h,
            bodyHtml = obj.optString("body_html", ""),
            images = images,
            variants = variants,
            options = options,
            vendor = vendorStr,
            productType = obj.optString("product_type", ""),
            url = "$storeUrl/products/$h",
            productKey = productKey,
            tags = tagsList,
            creatorDisplay = vendorStr,
            designIdMeta = designIdFromTags
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
            designLanguage = obj.optString("designLanguage", "").ifBlank { obj.optString("design_language", "") },
            creator = obj.optString("creator", ""),
            metaProductKey = obj.optString("productKey", "").ifBlank { obj.optString("metaProductKey", "") },
            patProductName = obj.optString("patProductName", "").ifBlank { obj.optString("product_name", "").ifBlank { obj.optString("productName", "") } },
            designId = obj.optString("designId", "")
        )
    }

    companion object {
        fun isPrintifyName(name: String?): Boolean =
            name?.trim()?.equals("printify", ignoreCase = true) == true

        fun resolveCreatorDisplay(creator: String?, vendor: String?): String {
            val c = creator?.trim().orEmpty()
            val v = vendor?.trim().orEmpty()
            if (c.isNotEmpty() && !isPrintifyName(c)) return c
            if (v.isNotEmpty() && !isPrintifyName(v)) return v
            return ""
        }

        suspend fun mergeShopPromotionOverlayProducts(
            products: List<ProductItem>,
            countryCode: String,
            creatorApi: CreatorApi
        ): List<ProductItem> = withContext(Dispatchers.IO) {
            if (products.isEmpty()) return@withContext products
            return@withContext try {
                val j = creatorApi.listActiveShopPromotionProducts(countryCode)
                val promos = parseActivePromotionProductsResponse(j).associateBy { it.handle }
                products.map { p ->
                    val o = promos[p.handle] ?: return@map p
                    p.copy(
                        promotionEndsAtMs = o.promotionEndsAtMs ?: p.promotionEndsAtMs,
                        promoBeforePrice = o.promoBeforePrice ?: p.promoBeforePrice,
                        compareAtPrice = o.compareAtPrice ?: p.compareAtPrice,
                        promoNextWindowStartsAtMs = o.promoNextWindowStartsAtMs ?: p.promoNextWindowStartsAtMs,
                        promoOutsideSlot = o.promoOutsideSlot || p.promoOutsideSlot,
                        promoPreviewPrice = o.promoPreviewPrice ?: p.promoPreviewPrice,
                        promoPrelaunch = o.promoPrelaunch || p.promoPrelaunch,
                        promoCampaignStartsAtMs = o.promoCampaignStartsAtMs ?: p.promoCampaignStartsAtMs
                    )
                }
            } catch (_: Exception) {
                products
            }
        }

        /** Worker JSON may use Long ms; avoid [JSONObject.optDouble] precision loss on large timestamps. */
        private fun parsePromotionEndsAtMs(o: JSONObject): Long? {
            if (!o.has("promotion_ends_at") || o.isNull("promotion_ends_at")) return null
            return try {
                when (val v = o.get("promotion_ends_at")) {
                    is Number -> v.toLong().takeIf { it > 0L }
                    is String -> v.trim().toLongOrNull()?.takeIf { it > 0L }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun parsePromoNextWindowStartsAtMs(o: JSONObject): Long? {
            if (!o.has("promo_next_window_starts_at") || o.isNull("promo_next_window_starts_at")) return null
            return try {
                when (val v = o.get("promo_next_window_starts_at")) {
                    is Number -> v.toLong().takeIf { it > 0L }
                    is String -> v.trim().toLongOrNull()?.takeIf { it > 0L }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun parsePromoCampaignStartsAtMs(o: JSONObject): Long? {
            if (!o.has("promo_campaign_starts_at") || o.isNull("promo_campaign_starts_at")) return null
            return try {
                when (val v = o.get("promo_campaign_starts_at")) {
                    is Number -> v.toLong().takeIf { it > 0L }
                    is String -> v.trim().toLongOrNull()?.takeIf { it > 0L }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun optTimestampMs(o: JSONObject, vararg keys: String): Long? {
            for (key in keys) {
                if (!o.has(key) || o.isNull(key)) continue
                try {
                    when (val v = o.get(key)) {
                        is Number -> v.toLong().takeIf { it > 0L }?.let { return it }
                        is String -> v.trim().toLongOrNull()?.takeIf { it > 0L }?.let { return it }
                    }
                } catch (_: Exception) {
                    /* try next key */
                }
            }
            return null
        }

        /**
         * Public worker op `list-active-shop-promotion-products` — maps to [ProductItem] for shop grid/carousel.
         */
        fun parseActivePromotionProductsResponse(json: JSONObject, storeBase: String = "https://www.eazpire.com"): List<ProductItem> {
            // Missing "ok" must not fail: optBoolean("ok", false) returns false when key absent → was wrongly treated as error.
            if (!json.optBoolean("ok", true)) return emptyList()
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
                // Do not skip products without images (matches web placeholder cards; Admin API may omit image fields).
                val price = when {
                    o.has("after_price") && !o.isNull("after_price") ->
                        o.optDouble("after_price").takeIf { !it.isNaN() } ?: o.optDouble("price", 0.0)
                    else -> o.optDouble("price", 0.0)
                }
                val compare = if (o.has("compare_at_price") && !o.isNull("compare_at_price")) {
                    o.optDouble("compare_at_price").takeIf { !it.isNaN() }
                } else {
                    null
                }
                val beforeRaw = if (o.has("before_price") && !o.isNull("before_price")) {
                    o.optDouble("before_price")
                } else {
                    Double.NaN
                }
                val prelaunch = o.optBoolean("promo_prelaunch", false)
                val outside = o.optBoolean("promo_outside_slot", false)
                val promoEndsMs = parsePromotionEndsAtMs(o)
                // Live slot discounts only — skip upcoming “Discount in …” / prelaunch teasers.
                if (outside || prelaunch || promoEndsMs == null || promoEndsMs <= System.currentTimeMillis()) {
                    continue
                }
                val promoStrike = if (!beforeRaw.isNaN() && beforeRaw > price + 1e-6) beforeRaw else null
                val compareForDisplay = promoStrike ?: compare
                out.add(
                    ProductItem(
                        id = o.optLong("id", 0L),
                        title = o.optString("title", handle),
                        handle = handle,
                        images = imgs,
                        variantImages = imgs.take(1),
                        url = "$storeBase/products/$handle",
                        price = price,
                        compareAtPrice = compareForDisplay,
                        createdAt = "",
                        productType = "",
                        tags = emptyList(),
                        vendor = o.optString("vendor", ""),
                        contentType = "",
                        designType = "",
                        designStyle = emptyList(),
                        ratio = "",
                        designLanguage = "",
                        creator = resolveCreatorDisplay(o.optString("creator", ""), o.optString("vendor", "")),
                        metaProductKey = "",
                        designId = "",
                        promotionEndsAtMs = promoEndsMs,
                        promoBeforePrice = promoStrike,
                        promoNextWindowStartsAtMs = null,
                        promoOutsideSlot = false,
                        promoPreviewPrice = null,
                        promoPrelaunch = false,
                        promoCampaignStartsAtMs = null
                    )
                )
            }
            return out
        }

        /** Worker op `list-home-carousel-products` — home carousel rows (sorted/deduped server-side). */
        fun parseHomeCarouselProductsResponse(json: JSONObject, storeBase: String = "https://www.eazpire.com"): List<ProductItem> {
            if (!json.optBoolean("ok", true)) return emptyList()
            val arr = json.optJSONArray("products") ?: return emptyList()
            val out = ArrayList<ProductItem>()
            val seen = HashSet<String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val handle = o.optString("handle", "").trim()
                if (handle.isBlank() || !seen.add(handle)) continue
                val imgs = mutableListOf<String>()
                val imgArr = o.optJSONArray("images")
                if (imgArr != null) {
                    for (j in 0 until imgArr.length()) {
                        imgArr.optString(j, "").takeIf { it.isNotBlank() }?.let { imgs.add(it) }
                    }
                }
                val variantArr = o.optJSONArray("variantImages")
                val variantImgs = mutableListOf<String>()
                if (variantArr != null) {
                    for (j in 0 until variantArr.length()) {
                        variantArr.optString(j, "").takeIf { it.isNotBlank() }?.let { variantImgs.add(it) }
                    }
                }
                val rotation = variantImgs.ifEmpty { imgs }
                val designStyleArr = o.optJSONArray("designStyle")
                val designStyle = mutableListOf<String>()
                if (designStyleArr != null) {
                    for (j in 0 until designStyleArr.length()) {
                        designStyleArr.optString(j, "").takeIf { it.isNotBlank() }?.let { designStyle.add(it) }
                    }
                }
                val price = o.optDouble("price", 0.0)
                val afterRaw = if (o.has("after_price") && !o.isNull("after_price")) {
                    o.optDouble("after_price")
                } else {
                    price
                }
                val beforeRaw = if (o.has("before_price") && !o.isNull("before_price")) {
                    o.optDouble("before_price")
                } else {
                    Double.NaN
                }
                val compare = if (o.has("compareAtPrice") && !o.isNull("compareAtPrice")) {
                    o.optDouble("compareAtPrice").takeIf { !it.isNaN() }
                } else if (o.has("compare_at_price") && !o.isNull("compare_at_price")) {
                    o.optDouble("compare_at_price").takeIf { !it.isNaN() }
                } else {
                    null
                }
                val promoEndsMs = optTimestampMs(o, "promotion_ends_at", "promotionEndsAt")
                val promoNextMs = optTimestampMs(o, "promo_next_window_starts_at", "promoNextWindowStartsAt")
                val promoCampaignStartMs = optTimestampMs(o, "promo_campaign_starts_at", "promoCampaignStartsAt")
                val outside = o.optBoolean("promoOutsideSlot", o.optBoolean("promo_outside_slot", false))
                val prelaunch = o.optBoolean("promoPrelaunch", o.optBoolean("promo_prelaunch", false))
                val promoPreviewFromApi = if (o.has("promoPreviewPrice") && !o.isNull("promoPreviewPrice")) {
                    o.optDouble("promoPreviewPrice").takeIf { !it.isNaN() }
                } else {
                    null
                }
                val displayPrice = if (outside) afterRaw else afterRaw
                val promoStrike = if (!outside && !beforeRaw.isNaN() && beforeRaw > displayPrice + 1e-6) {
                    beforeRaw
                } else if (!outside && compare != null && compare > displayPrice + 1e-6) {
                    compare
                } else {
                    null
                }
                val promoPreview = promoPreviewFromApi
                    ?: if (outside && !beforeRaw.isNaN() && beforeRaw < displayPrice - 1e-6) beforeRaw else null
                val compareForDisplay = when {
                    outside -> null
                    else -> promoStrike ?: compare
                }
                val vendorRaw = o.optString("vendor", "")
                val creatorRaw = o.optString("creator", vendorRaw)
                out.add(
                    ProductItem(
                        id = o.optLong("id", 0L),
                        title = o.optString("title", handle),
                        handle = handle,
                        images = imgs,
                        variantImages = rotation,
                        url = o.optString("url", "").ifBlank { "$storeBase/products/$handle" },
                        price = displayPrice,
                        compareAtPrice = compareForDisplay,
                        createdAt = o.optString("createdAt", o.optString("created_at", "")),
                        productType = o.optString("productType", o.optString("product_type", "")),
                        tags = emptyList(),
                        vendor = vendorRaw,
                        contentType = o.optString("contentType", o.optString("content_type", "")),
                        designType = o.optString("designType", o.optString("design_type", "")),
                        designStyle = designStyle,
                        ratio = o.optString("ratio", ""),
                        designLanguage = o.optString("designLanguage", o.optString("design_language", "")),
                        creator = resolveCreatorDisplay(creatorRaw, vendorRaw),
                        metaProductKey = o.optString("productKey", o.optString("product_key", "")),
                        patProductName = o.optString("productName", o.optString("product_name", "")),
                        designId = "",
                        promotionEndsAtMs = promoEndsMs,
                        promoBeforePrice = promoStrike,
                        promoNextWindowStartsAtMs = promoNextMs,
                        promoOutsideSlot = outside,
                        promoPreviewPrice = promoPreview,
                        promoPrelaunch = prelaunch,
                        promoCampaignStartsAtMs = promoCampaignStartMs,
                    )
                )
            }
            return out
        }
    }
}

/** True when the product card should use promotion-style pricing (compare-at sale or worker promo fields). Matches web eaz-promo-card / Liquid on_sale. Live discounts only — no upcoming “Discount in …” teasers. */
fun ShopifyProductsApi.ProductItem.hasPromoPricingUi(): Boolean {
    val cmp = compareAtPrice
    if (cmp != null && cmp > price + 1e-6) return true
    val ends = promotionEndsAtMs
    if (ends != null && ends > System.currentTimeMillis() && !promoOutsideSlot && !promoPrelaunch) return true
    return false
}
