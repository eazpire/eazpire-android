package com.eazpire.creator.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Lädt Produkte aus Shopify products.json.
 * https://www.eazpire.com/collections/{handle}/products.json oder /products.json
 */
class ShopifyProductsApi(
    private val baseUrl: String = "https://www.eazpire.com",
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
        val url: String
    )

    /**
     * Lädt Produkte einer Collection oder alle Produkte.
     * @param collectionHandle z.B. "women", "men", "kids" – null = alle Produkte
     * @param limit max. Anzahl Produkte
     */
    suspend fun getProducts(
        collectionHandle: String? = null,
        limit: Int = 20
    ): List<ProductItem> = withContext(Dispatchers.IO) {
        val url = if (!collectionHandle.isNullOrBlank()) {
            "$baseUrl/collections/$collectionHandle/products.json?limit=$limit"
        } else {
            "$baseUrl/products.json?limit=$limit"
        }
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "{}"
        val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
        val products = json.optJSONArray("products") ?: JSONArray()
        (0 until products.length()).mapNotNull { i ->
            parseProduct(products.optJSONObject(i))?.let { p ->
                p.copy(url = "$baseUrl/products/${p.handle}")
            }
        }
    }

    /**
     * Lädt Produkte für mehrere Kategorien. Bei leerer Collection wird auf alle Produkte zurückgegriffen.
     */
    suspend fun getProductsByCategories(
        categories: List<Pair<String, String>>,
        limitPerCategory: Int = 12
    ): Map<String, List<ProductItem>> = withContext(Dispatchers.IO) {
        categories.associate { (handle, _) ->
            val products = getProducts(collectionHandle = handle, limit = limitPerCategory)
            val fallback = if (products.isEmpty()) getProducts(limit = limitPerCategory) else products
            handle to fallback
        }
    }

    private fun parseProduct(obj: JSONObject?): ProductItem? {
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
        return ProductItem(id = id, title = title, handle = handle, images = images, url = "")
    }
}
