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
        limit: Int = 24,
        cursor: String? = null
    ): ProductsResult = withContext(Dispatchers.IO) {
        var result = fetchFromStorefrontApi(collectionHandle, limit, cursor)
        if (result.products.isEmpty()) {
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
        limit: Int,
        cursor: String?
    ): ProductsResult {
        val url = buildString {
            append("$workerUrl/apps/creator-dispatch?op=get-storefront-products")
            append("&limit=$limit")
            if (!collectionHandle.isNullOrBlank()) {
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
     * Filter images to variant-only (preview view). Alt format: "Color|View" (e.g. "Black|front").
     * Same logic as eaz-product-card-redesign.liquid and getStorefrontProducts.js.
     */
    private fun filterVariantImages(imagesArr: JSONArray): List<String> {
        var primaryView = ""
        for (j in 0 until imagesArr.length()) {
            val img = imagesArr.optJSONObject(j) ?: continue
            val alt = (img.optString("alt") ?: "").trim()
            if (alt.contains("|")) {
                val parts = alt.split("|")
                primaryView = (parts.getOrNull(1) ?: "").trim().lowercase()
                break
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
            if (primaryView.isNotEmpty() && mediaView != primaryView) continue
            val colorToken = "|$colorKey|"
            if (colorToken in usedColors) continue
            usedColors.add(colorToken)
            variantImages.add(src)
        }
        return variantImages
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

        val variantImages = if (imagesArr != null) filterVariantImages(imagesArr) else emptyList()
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
            variantImages = variantImages.ifEmpty { images },
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

    /**
     * Lädt Produkte für mehrere Kategorien.
     */
    suspend fun getProductsByCategories(
        categories: List<Pair<String, String>>,
        limitPerCategory: Int = 12
    ): Map<String, List<ProductItem>> = withContext(Dispatchers.IO) {
        categories.associate { (handle, _) ->
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
            variantImages = variantImages.ifEmpty { images },
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
}
