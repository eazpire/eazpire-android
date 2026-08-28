package com.eazpire.creator.ui.creator

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eazpire.creator.api.CreatorApi
import org.json.JSONObject

internal data class ResearchJobSnapshot(
    val searchId: String = "",
    val query: String = "",
    val running: Boolean = false,
    val tab: String = "ideas",
    val resultCount: Int = 0,
)

internal data class ResearchDoneToast(
    val query: String,
    val count: Int,
    val tab: String,
    val shownAt: Long = System.currentTimeMillis(),
)

/**
 * Analyze-Job überlebt das Verlassen von Research (Android disposed den Screen).
 */
internal object ResearchAnalyzeStore {
    private const val PREFS = "eazy-research"
    private const val IDEAS_KEY = "eazy-research-analyze-job"
    private const val TRENDS_KEY = "eazy-research-trends-job"

    var ideas by mutableStateOf(ResearchJobSnapshot(tab = "ideas"))
        private set
    var trends by mutableStateOf(ResearchJobSnapshot(tab = "trends"))
        private set
    var doneToast by mutableStateOf<ResearchDoneToast?>(null)

    fun restore(context: Context) {
        ideas = load(context, IDEAS_KEY, "ideas")
        trends = load(context, TRENDS_KEY, "trends")
    }

    fun saveIdeas(context: Context, snap: ResearchJobSnapshot) {
        ideas = snap.copy(tab = "ideas")
        persist(context, IDEAS_KEY, ideas)
    }

    fun saveTrends(context: Context, snap: ResearchJobSnapshot) {
        trends = snap.copy(tab = "trends")
        persist(context, TRENDS_KEY, trends)
    }

    fun showDone(query: String, count: Int, tab: String) {
        doneToast = ResearchDoneToast(query = query, count = count, tab = tab)
    }

    fun clearToast() {
        doneToast = null
    }

    suspend fun tick(api: CreatorApi) {
        if (ideas.running && ideas.searchId.isNotBlank()) {
            try {
                val data = api.call("eazy-research-search-status", mapOf("search_id" to ideas.searchId))
                if (data.optBoolean("ok", false)) {
                    val done = data.optBoolean("done", false) ||
                        data.optString("status") == "done" ||
                        data.optString("status") == "error"
                    val count = data.optJSONArray("products")?.length() ?: ideas.resultCount
                    if (done) {
                        val q = data.optString("query").ifBlank { ideas.query }
                        ideas = ideas.copy(running = false, resultCount = count, query = q)
                        showDone(q, count, "ideas")
                    } else {
                        ideas = ideas.copy(resultCount = count)
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (trends.running && trends.searchId.isNotBlank()) {
            try {
                val data = api.call("eazy-research-trends-search-status", mapOf("search_id" to trends.searchId))
                if (data.optBoolean("ok", false)) {
                    val done = data.optBoolean("done", false) ||
                        data.optString("status") == "done" ||
                        data.optString("status") == "error"
                    val count = data.optJSONArray("keywords")?.length() ?: trends.resultCount
                    if (done) {
                        val q = data.optString("query").ifBlank { trends.query }
                        trends = trends.copy(running = false, resultCount = count, query = q)
                        showDone(q, count, "trends")
                    } else {
                        trends = trends.copy(resultCount = count)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun load(context: Context, key: String, tab: String): ResearchJobSnapshot {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null) ?: return ResearchJobSnapshot(tab = tab)
        return try {
            val obj = JSONObject(raw)
            ResearchJobSnapshot(
                searchId = obj.optString("search_id"),
                query = obj.optString("q"),
                running = obj.optBoolean("running") || obj.optBoolean("analyzing") || obj.optBoolean("searching"),
                tab = tab,
                resultCount = obj.optInt("resultCount"),
            )
        } catch (_: Exception) {
            ResearchJobSnapshot(tab = tab)
        }
    }

    private fun persist(context: Context, key: String, snap: ResearchJobSnapshot) {
        val obj = JSONObject()
            .put("search_id", snap.searchId)
            .put("q", snap.query)
            .put("running", snap.running)
            .put("analyzing", snap.running)
            .put("searching", snap.running)
            .put("resultCount", snap.resultCount)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, obj.toString())
            .apply()
    }
}
