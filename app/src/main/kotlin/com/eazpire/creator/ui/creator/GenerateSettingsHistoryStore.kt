package com.eazpire.creator.ui.creator

import com.eazpire.creator.api.CreatorApi
import org.json.JSONArray
import org.json.JSONObject

data class GenerateSettingsHistoryEntry(
    val id: String,
    val ts: Long,
    val prompt: String,
    val designType: String,
    val targetProduct: String,
    val generatorMode: String,
    val ratio: String,
    val contentType: String,
    val styles: List<String>,
    val designColors: List<String>,
    val backgroundTransparent: Boolean,
    val languageMode: String,
    val languageCode: String,
    val origin: String,
    val refs: List<RefImage>,
) {
    fun label(): String {
        val words = prompt.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(6).joinToString(" ")
        val shown = if (words.isBlank()) "No prompt" else words
        return shown
    }
}

object GenerateSettingsHistoryStore {
    private const val MAX = 16
    private var cacheOwner: String = ""
    private var cache: List<GenerateSettingsHistoryEntry> = emptyList()

    fun cached(ownerId: String): List<GenerateSettingsHistoryEntry> {
        return if (ownerId.isNotBlank() && ownerId == cacheOwner) cache else emptyList()
    }

    suspend fun list(api: CreatorApi, ownerId: String): List<GenerateSettingsHistoryEntry> {
        if (ownerId.isBlank()) {
            cacheOwner = ""
            cache = emptyList()
            return emptyList()
        }
        return try {
            val res = api.listGenerateSettingsHistory(ownerId, MAX)
            if (!res.optBoolean("ok", false)) {
                if (res.optString("error") == "unauthorized") {
                    cacheOwner = ownerId
                    cache = emptyList()
                    return emptyList()
                }
                return cached(ownerId)
            }
            val arr = res.optJSONArray("items") ?: JSONArray()
            val items = (0 until arr.length()).mapNotNull { i -> parse(arr.optJSONObject(i)) }
            cacheOwner = ownerId
            cache = items
            items
        } catch (_: Exception) {
            cached(ownerId)
        }
    }

    suspend fun push(
        api: CreatorApi,
        ownerId: String,
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
        generatorMode: String = "design",
        origin: String = "android",
    ) {
        if (ownerId.isBlank()) return
        val slimRefs = JSONArray()
        refs.take(4).forEach { r ->
            val url = slimRefUrl(r.dataUrl)
            slimRefs.put(
                JSONObject()
                    .put("url", url)
                    .put("similarity", r.similarity.toDouble())
            )
        }
        val language = JSONObject().put("mode", languageMode)
        if (languageCode.isNotBlank()) language.put("language", languageCode)
        val body = JSONObject()
            .put("prompt", prompt.trim())
            .put("designType", designType)
            .put("targetProduct", targetProduct)
            .put("generatorMode", generatorMode)
            .put("ratio", ratio)
            .put("contentType", contentType)
            .put("styles", JSONArray(styles.take(12)))
            .put("designColors", JSONArray(designColors.take(12)))
            .put("background", JSONObject().put("mode", if (backgroundTransparent) "transparent" else "solid"))
            .put("language", language)
            .put("origin", origin)
            .put("refs", slimRefs)
        try {
            val res = api.pushGenerateSettingsHistory(ownerId, body)
            val saved = if (res.optBoolean("ok", false)) parse(res.optJSONObject("item")) else null
            if (saved != null) {
                cacheOwner = ownerId
                cache = listOf(saved) + cache.filter { it.id != saved.id }.take(MAX - 1)
            }
        } catch (_: Exception) {
        }
    }

    private fun slimRefUrl(raw: String): String {
        val url = raw.trim()
        if (url.isBlank()) return ""
        val lower = url.lowercase()
        if (lower.startsWith("data:") || lower.startsWith("blob:")) return ""
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return ""
        if (url.length > 2048) return ""
        return url
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
        val language = o.optJSONObject("language")
        val background = o.optJSONObject("background")
        return GenerateSettingsHistoryEntry(
            id = o.optString("id"),
            ts = o.optLong("ts"),
            prompt = o.optString("prompt"),
            designType = o.optString("designType", "classic"),
            targetProduct = o.optString("targetProduct", "all"),
            generatorMode = o.optString("generatorMode", "design"),
            ratio = o.optString("ratio", "portrait"),
            contentType = o.optString("contentType", "design-text"),
            styles = strList("styles"),
            designColors = strList("designColors"),
            backgroundTransparent = background == null || background.optString("mode", "transparent") != "solid",
            languageMode = language?.optString("mode", "as-design") ?: o.optString("languageMode", "as-design"),
            languageCode = language?.optString("language", "") ?: o.optString("languageCode"),
            origin = o.optString("origin"),
            refs = refs,
        )
    }
}
