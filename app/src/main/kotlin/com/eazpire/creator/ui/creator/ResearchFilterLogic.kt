package com.eazpire.creator.ui.creator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class ResearchFilterSnapshot(
    val query: String = "",
    val niches: Set<String> = emptySet(),
    val designTypes: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    val personalizations: Set<String> = emptySet(),
    val audiences: Set<String> = emptySet(),
    val opportunity: Set<String> = emptySet(),
    val marketplace: String = "all",
    val sort: String = "review_growth",
    val sortDir: String = "desc",
)

internal object ResearchFilterLogic {
    fun andFilter(
        products: List<ResearchProductLike>,
        filters: ResearchFilterSnapshot,
    ): List<ResearchProductLike> {
        var rows = products.filter { it.reprintOk }
        if (filters.marketplace.isNotBlank() && filters.marketplace != "all") {
            rows = rows.filter { it.marketplace.equals(filters.marketplace, ignoreCase = true) }
        }
        if (filters.niches.isNotEmpty()) {
            rows = rows.filter { topicKeyOf(it) in filters.niches }
        }
        if (filters.designTypes.isNotEmpty()) {
            rows = rows.filter { (it.designType ?: "").lowercase(Locale.ROOT) in filters.designTypes }
        }
        if (filters.languages.isNotEmpty()) {
            rows = rows.filter { (it.language ?: "").lowercase(Locale.ROOT) in filters.languages }
        }
        if (filters.personalizations.size == 1) {
            val want = filters.personalizations.first()
            rows = rows.filter { product ->
                val key = if (product.personalizable) "personalizable" else "standard"
                key == want
            }
        }
        if (filters.audiences.isNotEmpty()) {
            rows = rows.filter { it.audience in filters.audiences }
        }
        if (filters.opportunity.isNotEmpty()) {
            rows = rows.filter { (it.opportunityBucket ?: "") in filters.opportunity }
        }
        val q = filters.query.trim().lowercase(Locale.ROOT)
        if (q.isNotEmpty()) {
            rows = rows.filter {
                listOf(it.title, it.brand, it.asin, it.nicheKey, it.marketplace, it.marketplaceTag)
                    .joinToString(" ").lowercase(Locale.ROOT).contains(q)
            }
        }
        return rows.sortedByDescending { it.searchIngestedAt ?: it.capturedAt ?: 0L }
    }

    private fun topicKeyOf(p: ResearchProductLike): String {
        val topic = p.topic.trim().lowercase(Locale.ROOT)
        if (topic.isNotBlank()) return topic
        val key = p.nicheKey.trim().lowercase(Locale.ROOT)
        return if (key.isNotBlank() && key != "user_search") key else ""
    }
}

internal object ResearchFilterPrefs {
    private const val PREFS = "eazy-research"
    private const val IDEAS_KEY = "eazy-research-filters-ideas"
    private const val TRENDS_KEY = "eazy-research-filters-trends"

    fun loadIdeas(context: Context): ResearchFilterSnapshot = load(context, IDEAS_KEY)
    fun saveIdeas(context: Context, snap: ResearchFilterSnapshot) = save(context, IDEAS_KEY, snap)

    fun loadTrends(context: Context): TrendsFilterSnapshot {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TRENDS_KEY, null)
            ?: return TrendsFilterSnapshot()
        return try {
            val obj = JSONObject(raw)
            TrendsFilterSnapshot(
                geo = obj.optString("geo", "ALL"),
                language = obj.optString("language", "all"),
                query = obj.optString("q"),
                selectedTopics = stringSet(obj.optJSONArray("topics")),
                productTypes = stringSet(obj.optJSONArray("productTypes")),
                volume = stringSet(obj.optJSONArray("volume")),
                time = obj.optString("time", "avg_12m"),
                sort = obj.optString("sort", "volume"),
                sortDir = obj.optString("sortDir", "desc"),
            )
        } catch (_: Exception) {
            TrendsFilterSnapshot()
        }
    }

    fun saveTrends(context: Context, snap: TrendsFilterSnapshot) {
        val obj = JSONObject()
            .put("geo", snap.geo)
            .put("language", snap.language)
            .put("q", snap.query)
            .put("topics", JSONArray(snap.selectedTopics.toList()))
            .put("productTypes", JSONArray(snap.productTypes.toList()))
            .put("volume", JSONArray(snap.volume.toList()))
            .put("time", snap.time)
            .put("sort", snap.sort)
            .put("sortDir", snap.sortDir)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TRENDS_KEY, obj.toString()).apply()
    }

    private fun load(context: Context, key: String): ResearchFilterSnapshot {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
            ?: return ResearchFilterSnapshot()
        return try {
            val obj = JSONObject(raw)
            ResearchFilterSnapshot(
                query = obj.optString("q"),
                niches = stringSet(obj.optJSONArray("niches")),
                designTypes = stringSet(obj.optJSONArray("designTypes")),
                languages = stringSet(obj.optJSONArray("languages")),
                personalizations = stringSet(obj.optJSONArray("personalizations")),
                audiences = stringSet(obj.optJSONArray("audiences")),
                opportunity = stringSet(obj.optJSONArray("opportunity")),
                marketplace = obj.optString("marketplace", "all"),
                sort = obj.optString("sort", "review_growth"),
                sortDir = obj.optString("sortDir", "desc"),
            )
        } catch (_: Exception) {
            ResearchFilterSnapshot()
        }
    }

    private fun save(context: Context, key: String, snap: ResearchFilterSnapshot) {
        val obj = JSONObject()
            .put("q", snap.query)
            .put("niches", JSONArray(snap.niches.toList()))
            .put("designTypes", JSONArray(snap.designTypes.toList()))
            .put("languages", JSONArray(snap.languages.toList()))
            .put("personalizations", JSONArray(snap.personalizations.toList()))
            .put("audiences", JSONArray(snap.audiences.toList()))
            .put("opportunity", JSONArray(snap.opportunity.toList()))
            .put("marketplace", snap.marketplace)
            .put("sort", snap.sort)
            .put("sortDir", snap.sortDir)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, obj.toString()).apply()
    }

    private fun stringSet(arr: JSONArray?): Set<String> {
        if (arr == null) return emptySet()
        return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
    }
}

internal data class TrendsFilterSnapshot(
    val geo: String = "ALL",
    val language: String = "all",
    val query: String = "",
    val selectedTopics: Set<String> = emptySet(),
    val productTypes: Set<String> = emptySet(),
    val volume: Set<String> = emptySet(),
    val time: String = "avg_12m",
    val sort: String = "volume",
    val sortDir: String = "desc",
)

/** Schlanke Testdaten — spiegelt die Felder der Research-Karten. */
internal data class ResearchProductLike(
    val asin: String,
    val marketplace: String = "amazon.de",
    val marketplaceTag: String = "DE",
    val title: String,
    val brand: String = "",
    val nicheKey: String = "",
    val topic: String = "",
    val designType: String? = null,
    val language: String? = null,
    val personalizable: Boolean = false,
    val audience: String = "",
    val reprintOk: Boolean = true,
    val opportunityBucket: String? = null,
    val capturedAt: Long? = null,
    val searchIngestedAt: Long? = null,
)
