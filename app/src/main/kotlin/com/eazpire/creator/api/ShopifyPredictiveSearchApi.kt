package com.eazpire.creator.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Shopify predictive search aligned with [theme/assets/eaz-redesign-common.js]:
 * - [fetchSuggestions]: /search/suggest.json (limit_scope each, limit 10) + query suggestions
 * - [fetchPredictiveSectionProducts]: Section Rendering JSON for view `eaz-predictive` (up to 50 PLP rows)
 * - [collectPredictiveSearch]: parallel fetches with partial UI updates (suggest first, then merged list)
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

    /** Raw suggest.json shape (legacy single-call). */
    data class Result(val queries: List<QuerySuggestion>, val products: List<ProductSuggestion>)

    /** One row after merging section PLP order + suggest-only fill. */
    data class PredictiveProductRow(
        val handle: String,
        val url: String,
        val images: List<String>,
        val title: String?,
        val priceCents: Long?,
        val vendor: String?
    )

    data class PredictiveSearchState(
        val queries: List<QuerySuggestion>,
        val products: List<PredictiveProductRow>,
        /** True until the section/PLP request has finished (even if it returned zero rows). */
        val sectionStillLoading: Boolean
    )

    private data class SectionProductRow(
        val handle: String,
        val url: String,
        val images: List<String>
    )

    private val sectionIdLock = Any()
    private var cachedSectionApiId: String? = null

    suspend fun fetchSuggestions(query: String): Result = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext Result(emptyList(), emptyList())

        val url = "$storeBaseUrl/search/suggest.json?q=${java.net.URLEncoder.encode(q, "UTF-8")}" +
            "&resources[type]=product,query&resources[limit_scope]=each&resources[limit]=10"

        val body = httpGetBody(url, acceptJson = true) ?: return@withContext Result(emptyList(), emptyList())

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
            val rawUrl = o.optString("url", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val absUrl = resolveUrl(rawUrl)
            val handleJson = o.optString("handle", "").trim()
            val handle = handleJson.takeIf { it.isNotBlank() } ?: productHandleFromUrl(absUrl)
            if (handle.isBlank()) return@mapNotNull null
            val title = o.optString("title", "").takeIf { it.isNotBlank() } ?: handle
            val price = o.opt("price")
            val priceCents = computePriceCents(price)
            val imageStr = o.optString("image", "").takeIf { it.isNotBlank() }
                ?: o.optJSONObject("featured_image")?.optString("url", "")?.takeIf { it.isNotBlank() }
            ProductSuggestion(
                title = title,
                url = absUrl,
                image = imageStr,
                priceCents = priceCents,
                vendor = o.optString("vendor", "").takeIf { it.isNotBlank() }
            )
        }

        Result(queries = queries, products = products)
    }

    /**
     * Runs suggest + section requests in parallel; invokes [onUpdate] on the main thread when either completes
     * (partial list from suggest first, then merged up to [PRODUCT_CAP] when section returns).
     */
    suspend fun collectPredictiveSearch(
        query: String,
        onUpdate: suspend (PredictiveSearchState) -> Unit
    ) = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) {
            withContext(Dispatchers.Main) {
                onUpdate(PredictiveSearchState(emptyList(), emptyList(), sectionStillLoading = false))
            }
            return@withContext
        }

        val mutex = Mutex()
        var suggest: Result? = null
        var section: List<SectionProductRow>? = null
        var sectionFinished = false

        suspend fun emit() {
            val state = mutex.withLock {
                val s = suggest
                val sec = section
                if (s == null && sec == null) return@withLock null
                buildPredictiveState(s, sec, sectionStillLoading = !sectionFinished)
            } ?: return
            withContext(Dispatchers.Main) { onUpdate(state) }
        }

        coroutineScope {
            launch {
                val r = runCatching { fetchSuggestions(q) }.getOrElse { Result(emptyList(), emptyList()) }
                mutex.withLock { suggest = r }
                emit()
            }
            launch {
                val rows = runCatching { fetchPredictiveSectionProductsLocked(q) }.getOrElse { emptyList() }
                mutex.withLock {
                    section = rows
                    sectionFinished = true
                }
                emit()
            }
        }
    }

    private fun buildPredictiveState(
        suggest: Result?,
        section: List<SectionProductRow>?,
        sectionStillLoading: Boolean
    ): PredictiveSearchState {
        val queries = suggest?.queries ?: emptyList()
        var merged = mergeProductRows(section, suggest)
        if (merged.size > PRODUCT_CAP) merged = merged.take(PRODUCT_CAP)
        return PredictiveSearchState(
            queries = queries,
            products = merged,
            sectionStillLoading = sectionStillLoading
        )
    }

    private fun mergeProductRows(
        sectionRows: List<SectionProductRow>?,
        suggest: Result?
    ): List<PredictiveProductRow> {
        val map = LinkedHashMap<String, PredictiveProductRow>()
        sectionRows?.forEach { r ->
            val h = r.handle.trim()
            if (h.isEmpty()) return@forEach
            if (map.containsKey(h)) return@forEach
            map[h] = PredictiveProductRow(
                handle = h,
                url = resolveUrl(r.url),
                images = r.images.map { resolveUrl(it) },
                title = null,
                priceCents = null,
                vendor = null
            )
        }
        suggest?.products?.forEach { p ->
            val h = productHandleFromUrl(p.url)
            if (h.isBlank() || map.containsKey(h)) return@forEach
            val imgs = listOfNotNull(p.image?.let { resolveUrl(it) }).ifEmpty { emptyList() }
            map[h] = PredictiveProductRow(
                handle = h,
                url = p.url,
                images = imgs,
                title = p.title,
                priceCents = p.priceCents,
                vendor = p.vendor
            )
        }
        return map.values.toList()
    }

    private fun fetchPredictiveSectionProductsLocked(query: String): List<SectionProductRow> {
        if (query.length < 2) return emptyList()
        fun attempt(clearedCache: Boolean): List<SectionProductRow> {
            val id = resolveSectionApiId() ?: return emptyList()
            val frag = fetchSectionFragment(query, id) ?: ""
            val parsed = parsePredictiveJsonFromHtml(frag)
            if (parsed.isEmpty() && !clearedCache) {
                synchronized(sectionIdLock) { cachedSectionApiId = null }
                return attempt(true)
            }
            return parsed
        }
        return attempt(false)
    }

    private fun resolveSectionApiId(): String? {
        synchronized(sectionIdLock) {
            cachedSectionApiId?.let { if (SECTION_ID_STRICT.matches(it)) return it }
        }
        val probeUrl = "${storeBaseUrl.trimEnd('/')}/search?q=${enc("a")}&type=product&view=${enc(PREDICTIVE_VIEW)}"
        val html = httpGetBody(probeUrl, acceptJson = false) ?: return null
        var m = SECTION_ID_HTML_REGEX.find(html)
        if (m == null) m = SECTION_ID_FALLBACK_REGEX.find(html)
        val id = m?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (id.isEmpty() || !SECTION_ID_STRICT.matches(id)) return null
        synchronized(sectionIdLock) { cachedSectionApiId = id }
        return id
    }

    private fun fetchSectionFragment(query: String, sectionId: String): String? {
        val base = storeBaseUrl.trimEnd('/')
        val url = "$base/search?q=${enc(query)}&type=product&view=${enc(PREDICTIVE_VIEW)}&sections=${enc(sectionId)}"
        val body = httpGetBody(url, acceptJson = true) ?: return null
        val trimmed = body.trim()
        if (trimmed.startsWith("<")) return null
        return extractSectionHtml(JSONObject(trimmed), sectionId)
    }

    private fun extractSectionHtml(sectionJson: JSONObject, sectionApiId: String): String {
        if (sectionApiId.isNotBlank()) {
            val primary = sectionJson.optString(sectionApiId, "")
            if (primary.contains("data-eaz-predictive-json")) return primary
        }
        val keys = sectionJson.keys()
        while (keys.hasNext()) {
            val v = sectionJson.optString(keys.next(), "")
            if (v.contains("data-eaz-predictive-json")) return v
        }
        return ""
    }

    private fun parsePredictiveJsonFromHtml(html: String): List<SectionProductRow> {
        if (html.isBlank()) return emptyList()
        val re = PREDICTIVE_SCRIPT_REGEX
        val m = re.find(html) ?: return emptyList()
        val jsonText = m.groupValues[1].trim()
        if (jsonText.isEmpty()) return emptyList()
        return try {
            val data = JSONObject(jsonText)
            val products = data.optJSONArray("products") ?: return emptyList()
            val out = ArrayList<SectionProductRow>(products.length())
            for (i in 0 until products.length()) {
                val o = products.optJSONObject(i) ?: continue
                val handle = o.optString("handle", "").takeIf { it.isNotBlank() } ?: continue
                val urlRaw = o.optString("url", "")
                val images = mutableListOf<String>()
                val imgArr = o.optJSONArray("images")
                if (imgArr != null) {
                    for (j in 0 until imgArr.length()) {
                        val s = imgArr.optString(j, "").takeIf { it.isNotBlank() } ?: continue
                        images.add(s)
                    }
                }
                out.add(SectionProductRow(handle = handle, url = urlRaw, images = images))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun httpGetBody(url: String, acceptJson: Boolean): String? {
        val accept = if (acceptJson) {
            "application/json, text/javascript, */*"
        } else {
            "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8"
        }
        val b = Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", STOREFRONT_HTTP_UA)
            .header("Referer", storeBaseUrl.trimEnd('/') + "/")
            .get()
            .build()
        return try {
            client.newCall(b).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveUrl(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http://", ignoreCase = true) ||
            pathOrUrl.startsWith("https://", ignoreCase = true)
        ) {
            return pathOrUrl
        }
        val base = storeBaseUrl.trimEnd('/')
        val p = if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"
        return base + p
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    private fun computePriceCents(price: Any?): Long? {
        val d = when (price) {
            is Number -> price.toDouble()
            is String -> price.toDoubleOrNull() ?: return null
            else -> return null
        }
        if (d <= 0) return null
        return (d * 100.0).toLong()
    }

    companion object {
        private const val PREDICTIVE_VIEW = "eaz-predictive"
        private const val PRODUCT_CAP = 50

        /** Shopify storefront + Cloudflare expect a browser-like UA; default OkHttp UA often gets empty/challenge HTML. */
        private const val STOREFRONT_HTTP_UA =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"

        private val SECTION_ID_STRICT = Regex("^template--\\d+__eaz_predictive_search_json$")

        private val SECTION_ID_HTML_REGEX = Regex(
            """id="shopify-section-(template--\d+__eaz_predictive_search_json)"""",
            RegexOption.IGNORE_CASE
        )

        private val SECTION_ID_FALLBACK_REGEX = Regex(
            """shopify-section-(template--\d+__eaz_predictive_search_json)""",
            RegexOption.IGNORE_CASE
        )

        private val PREDICTIVE_SCRIPT_REGEX = Regex(
            """<script\b[^>]*\bdata-eaz-predictive-json\b[^>]*>([\s\S]*?)</script\s*>""",
            RegexOption.IGNORE_CASE
        )

        fun productHandleFromUrl(url: String): String {
            val path = try {
                java.net.URI(url).path ?: ""
            } catch (_: Exception) {
                ""
            }
            val prefix = "/products/"
            val i = path.indexOf(prefix)
            if (i < 0) return ""
            return path.substring(i + prefix.length).trimEnd('/').substringBefore('?').trim()
        }

        fun titleFromHandle(handle: String): String {
            if (handle.isBlank()) return ""
            return handle.split('-').joinToString(" ") { word ->
                word.replaceFirstChar { c ->
                    if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
                }
            }
        }
    }
}
