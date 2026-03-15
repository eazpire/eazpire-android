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
        val url: String,
        val price: Double = 0.0,
        val compareAtPrice: Double? = null,
        val createdAt: String = "",
        val productType: String = "",
        val tags: List<String> = emptyList(),
        val vendor: String = "",
        val contentType: String = "",
        val designType: String = "",
        val designStyle: List<String> = emptyList()
    )

    data class ProductsResult(
        val products: List<ProductItem>,
        val hasNextPage: Boolean,
        val nextCursor: String?
    )

    /**
     * Lädt Produkte einer Collection oder alle Produkte via Storefront API.
     * @param collectionHandle z.B. "women", "men" – null = alle Produkte
     * @param limit max. Anzahl Produkte
     * @param cursor Pagination-Cursor (null für erste Seite)
     */
    suspend fun getProducts(
        collectionHandle: String? = null,
        limit: Int = 24,
        cursor: String? = null
    ): ProductsResult = withContext(Dispatchers.IO) {
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
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "{}"
        val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
        if (!json.optBoolean("ok", false)) {
            return@withContext ProductsResult(emptyList(), false, null)
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
        val designStyleArr = obj.optJSONArray("designStyle")
        val designStyle = if (designStyleArr != null) {
            (0 until designStyleArr.length()).mapNotNull { designStyleArr.optString(it).takeIf { s -> s.isNotBlank() } }
        } else emptyList()
        val tagsArr = obj.optJSONArray("tags")
        val tags = if (tagsArr != null) {
            (0 until tagsArr.length()).mapNotNull { tagsArr.optString(it).takeIf { s -> s.isNotBlank() } }
        } else emptyList()
        return ProductItem(
            id = obj.optLong("id", 0L),
            title = obj.optString("title", ""),
            handle = handle,
            images = images,
            url = obj.optString("url", "").ifBlank { "$storeUrl/products/$handle" },
            price = obj.optDouble("price", 0.0),
            compareAtPrice = null,
            createdAt = obj.optString("createdAt", ""),
            productType = obj.optString("productType", ""),
            tags = tags,
            vendor = obj.optString("vendor", ""),
            contentType = obj.optString("contentType", ""),
            designType = obj.optString("designType", ""),
            designStyle = designStyle
        )
    }
}
