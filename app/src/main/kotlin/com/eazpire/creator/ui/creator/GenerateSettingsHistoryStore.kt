package com.eazpire.creator.ui.creator

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.genSettingsHistoryStore: DataStore<Preferences> by preferencesDataStore(
    name = "eaz_gen_settings_history_v1"
)

private val BLOB_KEY = stringPreferencesKey("entries")

data class GenerateSettingsHistoryEntry(
    val id: String,
    val ts: Long,
    val prompt: String,
    val designType: String,
    val targetProduct: String,
    val ratio: String,
    val contentType: String,
    val styles: List<String>,
    val designColors: List<String>,
    val backgroundTransparent: Boolean,
    val languageMode: String,
    val languageCode: String,
    val refs: List<RefImage>,
) {
    fun label(): String {
        val words = prompt.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(6).joinToString(" ")
        val shown = if (words.isBlank()) "No prompt" else words
        return shown
    }
}

object GenerateSettingsHistoryStore {
    private const val MAX = 12

    suspend fun list(context: Context): List<GenerateSettingsHistoryEntry> {
        val raw = context.applicationContext.genSettingsHistoryStore.data.first()[BLOB_KEY].orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> parse(arr.optJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun push(
        context: Context,
        prompt: String,
        designType: String,
        targetProduct: String,
        ratio: String,
        contentType: String,
        styles: List<String>,
        designColors: List<String>,
        backgroundTransparent: Boolean,
        languageMode: String,
        languageCode: String,
        refs: List<RefImage>,
    ) {
        val slimRefs = JSONArray()
        var used = 0
        refs.take(4).forEach { r ->
            var url = r.dataUrl
            if (url.isNotBlank() && used + url.length > 400_000) url = ""
            used += url.length
            slimRefs.put(
                JSONObject()
                    .put("url", url)
                    .put("similarity", r.similarity.toDouble())
            )
        }
        val item = JSONObject()
            .put("id", "h_${System.currentTimeMillis()}_${(Math.random() * 1_000_000).toInt()}")
            .put("ts", System.currentTimeMillis())
            .put("prompt", prompt.trim())
            .put("designType", designType)
            .put("targetProduct", targetProduct)
            .put("ratio", ratio)
            .put("contentType", contentType)
            .put("styles", JSONArray(styles.take(12)))
            .put("designColors", JSONArray(designColors.take(12)))
            .put("backgroundTransparent", backgroundTransparent)
            .put("languageMode", languageMode)
            .put("languageCode", languageCode)
            .put("refs", slimRefs)
        val current = list(context)
        val next = JSONArray().put(item)
        current.take(MAX - 1).forEach { next.put(it.toJson()) }
        context.applicationContext.genSettingsHistoryStore.edit {
            it[BLOB_KEY] = next.toString()
        }
    }

    private fun parse(o: JSONObject?): GenerateSettingsHistoryEntry? {
        if (o == null) return null
        val refsArr = o.optJSONArray("refs") ?: JSONArray()
        val refs = (0 until refsArr.length()).mapNotNull { i ->
            val r = refsArr.optJSONObject(i) ?: return@mapNotNull null
            val url = r.optString("url")
            if (url.isBlank()) return@mapNotNull null
            RefImage(dataUrl = url, similarity = r.optDouble("similarity", 0.8).toFloat())
        }
        fun strList(key: String): List<String> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf { it.isNotBlank() } }
        }
        return GenerateSettingsHistoryEntry(
            id = o.optString("id"),
            ts = o.optLong("ts"),
            prompt = o.optString("prompt"),
            designType = o.optString("designType", "classic"),
            targetProduct = o.optString("targetProduct", "all"),
            ratio = o.optString("ratio", "portrait"),
            contentType = o.optString("contentType", "design-text"),
            styles = strList("styles"),
            designColors = strList("designColors"),
            backgroundTransparent = o.optBoolean("backgroundTransparent", true),
            languageMode = o.optString("languageMode", "as-design"),
            languageCode = o.optString("languageCode"),
            refs = refs,
        )
    }

    private fun GenerateSettingsHistoryEntry.toJson(): JSONObject {
        val refsArr = JSONArray()
        refs.forEach { r ->
            refsArr.put(JSONObject().put("url", r.dataUrl).put("similarity", r.similarity.toDouble()))
        }
        return JSONObject()
            .put("id", id)
            .put("ts", ts)
            .put("prompt", prompt)
            .put("designType", designType)
            .put("targetProduct", targetProduct)
            .put("ratio", ratio)
            .put("contentType", contentType)
            .put("styles", JSONArray(styles))
            .put("designColors", JSONArray(designColors))
            .put("backgroundTransparent", backgroundTransparent)
            .put("languageMode", languageMode)
            .put("languageCode", languageCode)
            .put("refs", refsArr)
    }
}
