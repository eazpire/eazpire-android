package com.eazpire.creator.chat

import android.content.Context
import org.json.JSONObject

data class EazyGuideEntry(
    val title: String,
    val summary: String,
    val tips: List<String>
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
                    put(
                        key,
                        EazyGuideEntry(
                            title = o.optString("title"),
                            summary = o.optString("summary"),
                            tips = tips
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun textFor(context: Context, guideKey: String?): String? {
        if (guideKey.isNullOrBlank()) return null
        ensureLoaded(context)
        val entry = loaded?.get(guideKey) ?: return null
        return buildString {
            if (entry.title.isNotBlank()) append(entry.title)
            if (entry.summary.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(entry.summary)
            }
            if (entry.tips.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(entry.tips.joinToString(" "))
            }
        }.trim().ifBlank { null }
    }
}
