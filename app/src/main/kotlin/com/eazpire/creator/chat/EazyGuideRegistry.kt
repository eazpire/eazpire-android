package com.eazpire.creator.chat

import android.content.Context
import org.json.JSONObject

data class EazyGuidePage(
    val category: String,
    val body: String
)

data class EazyGuideEntry(
    val title: String,
    val summary: String,
    val tips: List<String>,
    val pages: List<EazyGuidePage>
)

object EazyGuideRegistry {
    private var loaded: Map<String, EazyGuideEntry>? = null

    fun ensureLoaded(context: Context) {
        if (loaded != null) return
        loaded = try {
            val raw = context.assets.open("eazy-guide-registry.json").bufferedReader().use { it.readText() }
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { key ->
                    val o = root.optJSONObject(key) ?: return@forEach
                    val tips = mutableListOf<String>()
                    o.optJSONArray("tips")?.let { arr ->
                        for (i in 0 until arr.length()) tips.add(arr.optString(i))
                    }
                    val pages = mutableListOf<EazyGuidePage>()
                    o.optJSONArray("pages")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val p = arr.optJSONObject(i) ?: continue
                            val body = p.optString("body").trim()
                            if (body.isBlank()) continue
                            pages.add(
                                EazyGuidePage(
                                    category = p.optString("category", "Info").ifBlank { "Info" },
                                    body = body
                                )
                            )
                        }
                    }
                    if (pages.isEmpty()) {
                        val summary = o.optString("summary")
                        if (summary.isNotBlank()) pages.add(EazyGuidePage("Overview", summary))
                        tips.forEachIndexed { index, tip ->
                            pages.add(
                                EazyGuidePage(
                                    category = if (tips.size > 1) "Tip ${index + 1}" else "Tip",
                                    body = tip
                                )
                            )
                        }
                        if (pages.isEmpty()) {
                            val title = o.optString("title")
                            if (title.isNotBlank()) pages.add(EazyGuidePage("Overview", title))
                        }
                    }
                    put(
                        key,
                        EazyGuideEntry(
                            title = o.optString("title"),
                            summary = o.optString("summary"),
                            tips = tips,
                            pages = pages
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun entryFor(context: Context, guideKey: String?): EazyGuideEntry? {
        if (guideKey.isNullOrBlank()) return null
        ensureLoaded(context)
        return loaded?.get(guideKey)
    }

    fun pagesFor(context: Context, guideKey: String?): List<EazyGuidePage>? {
        val entry = entryFor(context, guideKey) ?: return null
        if (entry.pages.isEmpty()) return null
        return entry.pages
    }

    /** Flattened fallback for callers that still expect a single string. */
    fun textFor(context: Context, guideKey: String?): String? {
        val pages = pagesFor(context, guideKey) ?: return null
        return pages.joinToString("\n\n") { page ->
            if (page.category.isBlank()) page.body else "${page.category}\n${page.body}"
        }.trim().ifBlank { null }
    }

    fun pagesFromPlainText(text: String): List<EazyGuidePage> {
        val chunks = text.trim().split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        if (chunks.isEmpty()) return emptyList()
        if (chunks.size == 1) return listOf(EazyGuidePage("Answer", chunks[0]))
        return chunks.mapIndexed { index, body ->
            EazyGuidePage(if (index == 0) "Answer" else "More", body)
        }
    }
}
