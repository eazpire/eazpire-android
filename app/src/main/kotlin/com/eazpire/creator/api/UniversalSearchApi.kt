package com.eazpire.creator.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * Shared Worker search (op=universal-search). Shopify / local match is fallback only.
 */
class UniversalSearchApi(
    private val creatorApi: CreatorApi = CreatorApi(),
) {
    data class QuerySuggestion(val text: String, val styledText: String)

    data class ProductHit(
        val handle: String,
        val url: String,
        val title: String,
        val image: String?,
        val vendor: String?,
        val shopifyId: String?,
        val creatorName: String?,
    )

    data class DesignHit(
        val id: String,
        val title: String,
        val imageUrl: String?,
        val ownerId: String?,
        val creatorName: String?,
    )

    data class Result(
        val ok: Boolean,
        val engine: String,
        val queries: List<QuerySuggestion>,
        val products: List<ProductHit>,
        val designs: List<DesignHit>,
    )

    suspend fun search(
        query: String,
        mode: String = "products",
        phase: String = "results",
        limit: Int = 24,
        ownerId: String? = null,
        collection: String? = null,
        country: String? = null,
        handles: List<String>? = null,
    ): Result? = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext Result(true, "empty", emptyList(), emptyList(), emptyList())
        val params = mutableMapOf(
            "q" to q,
            "mode" to mode,
            "phase" to phase,
            "limit" to limit.toString(),
        )
        if (!ownerId.isNullOrBlank()) params["owner_id"] = ownerId
        if (!collection.isNullOrBlank()) params["collection"] = collection
        if (!country.isNullOrBlank()) params["country"] = country
        if (!handles.isNullOrEmpty()) params["handles"] = handles.joinToString(",")
        val json = runCatching { creatorApi.call("universal-search", params, "GET") }.getOrNull()
            ?: return@withContext null
        if (!json.optBoolean("ok", false)) return@withContext null
        parse(json)
    }

    companion object {
        fun normalize(raw: String): String {
            val n = try {
                Normalizer.normalize(raw, Normalizer.Form.NFD)
            } catch (_: Exception) {
                raw
            }
            return n.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
                .lowercase(Locale.ROOT)
                .replace("\\s+".toRegex(), " ")
                .trim()
        }

        fun matchLocal(blob: String?, query: String): Boolean {
            val q = normalize(query)
            if (q.isEmpty()) return true
            val hay = normalize(blob.orEmpty())
            if (hay.contains(q)) return true
            return q.split(Regex("[\\s,.;!?/\\\\|_+-]+")).filter { it.isNotBlank() }.all { hay.contains(it) }
        }
    }

    private fun parse(json: JSONObject): Result {
        val queriesJson = json.optJSONArray("queries")
        val queries = mutableListOf<QuerySuggestion>()
        if (queriesJson != null) {
            for (i in 0 until queriesJson.length()) {
                val o = queriesJson.optJSONObject(i) ?: continue
                val text = o.optString("text", "").trim()
                if (text.isBlank()) continue
                queries.add(QuerySuggestion(text, o.optString("styled_text", text).ifBlank { text }))
            }
        }
        val productsJson = json.optJSONArray("products")
        val products = mutableListOf<ProductHit>()
        if (productsJson != null) {
            for (i in 0 until productsJson.length()) {
                val o = productsJson.optJSONObject(i) ?: continue
                val handle = o.optString("handle", "").trim()
                val url = o.optString("url", "").ifBlank { if (handle.isNotBlank()) "/products/$handle" else "" }
                if (handle.isBlank() && url.isBlank()) continue
                products.add(
                    ProductHit(
                        handle = handle,
                        url = url,
                        title = o.optString("title", handle),
                        image = o.optString("image", "").takeIf { it.isNotBlank() },
                        vendor = o.optString("vendor", "").takeIf { it.isNotBlank() },
                        shopifyId = o.optString("shopify_id", "").takeIf { it.isNotBlank() },
                        creatorName = o.optString("creator_name", "").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
        val designsJson = json.optJSONArray("designs")
        val designs = mutableListOf<DesignHit>()
        if (designsJson != null) {
            for (i in 0 until designsJson.length()) {
                val o = designsJson.optJSONObject(i) ?: continue
                val id = o.optString("id", "").trim()
                if (id.isBlank()) continue
                designs.add(
                    DesignHit(
                        id = id,
                        title = o.optString("title", ""),
                        imageUrl = o.optString("image_url", "").takeIf { it.isNotBlank() },
                        ownerId = o.optString("owner_id", "").takeIf { it.isNotBlank() },
                        creatorName = o.optString("creator_name", "").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
        return Result(
            ok = true,
            engine = json.optString("engine", "fts"),
            queries = queries,
            products = products,
            designs = designs,
        )
    }
}
