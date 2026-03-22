package com.eazpire.creator.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Shopify Storefront predictive search ([/search/suggest.json]) — same as web topbar.
 */
class ShopifyPredictiveSearchApi(
    private val storeBaseUrl: String = "https://www.eazpire.com",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    data class QuerySuggestion(val text: String, val styledText: String)
    data class ProductSuggestion(
        val title: String,
        val url: String,
        val image: String?,
        val priceCents: Long?,
        val vendor: String?
    )

    data class Result(val queries: List<QuerySuggestion>, val products: List<ProductSuggestion>)

    suspend fun fetchSuggestions(query: String): Result = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext Result(emptyList(), emptyList())

        val url = "$storeBaseUrl/search/suggest.json?q=${java.net.URLEncoder.encode(q, "UTF-8")}" +
            "&resources[type]=product,query&resources[limit]=8"

        val req = Request.Builder().url(url).header("Accept", "application/json").get().build()
        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext Result(emptyList(), emptyList())
            resp.body?.string() ?: return@withContext Result(emptyList(), emptyList())
        }

        val root = try {
            JSONObject(body)
        } catch (_: Exception) {
            return@withContext Result(emptyList(), emptyList())
        }

        val resources = root.optJSONObject("resources") ?: return@withContext Result(emptyList(), emptyList())
        val results = resources.optJSONObject("results") ?: return@withContext Result(emptyList(), emptyList())

        val queriesArr = results.optJSONArray("queries") ?: JSONArray()
        val queries = (0 until queriesArr.length()).mapNotNull { i ->
            val o = queriesArr.optJSONObject(i) ?: return@mapNotNull null
            val text = o.optString("text", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val styled = o.optString("styled_text", text).ifBlank { text }
            QuerySuggestion(text = text, styledText = styled)
        }

        val productsArr = results.optJSONArray("products") ?: JSONArray()
        val products = (0 until productsArr.length()).mapNotNull { i ->
            val o = productsArr.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val rawUrl = o.optString("url", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val absUrl = resolveUrl(rawUrl)
            val price = o.opt("price")
            val priceCents = when (price) {
                is Number -> price.toLong()
                is String -> price.toLongOrNull()
                else -> null
            }
            ProductSuggestion(
                title = title,
                url = absUrl,
                image = o.optString("image", "").takeIf { it.isNotBlank() },
                priceCents = priceCents,
                vendor = o.optString("vendor", "").takeIf { it.isNotBlank() }
            )
        }

        Result(queries = queries, products = products)
    }

    private fun resolveUrl(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http://", ignoreCase = true) || pathOrUrl.startsWith("https://", ignoreCase = true)) {
            return pathOrUrl
        }
        val base = storeBaseUrl.trimEnd('/')
        val p = if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"
        return base + p
    }
}
