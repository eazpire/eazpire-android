package com.eazpire.creator.ui.share

import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val REF_LINKS_SETTING_KEY = "community_ref_links_v1"

/**
 * Baut die aktive Ref-Link-URL aus API-Daten.
 * Gleiche Logik wie AccountCommunityTab.
 */
suspend fun getActiveRefUrl(api: CreatorApi, ownerId: String): String? = withContext(Dispatchers.IO) {
    if (ownerId.isBlank()) return@withContext null
    try {
        val ref = api.getReferralCode(ownerId)
        val baseUrl = ref.optString("short_url", "").ifBlank { ref.optString("url", "") }
        if (!ref.optBoolean("ok", false) || baseUrl.isBlank()) return@withContext null
        val settingRes = api.getCustomerSetting(ownerId, REF_LINKS_SETTING_KEY)
        val raw = if (settingRes.optBoolean("ok", false)) settingRes.optString("value", "").takeIf { it.isNotBlank() } else null
        val (links, activeId) = parseRefLinks(raw)
        val activeLink = links.find { it.id == activeId } ?: links.firstOrNull()
        buildNamedRefUrl(baseUrl, activeLink?.slug ?: "")
    } catch (_: Exception) {
        null
    }
}

private data class RefLink(val id: String, val name: String, val slug: String)

private fun parseRefLinks(raw: String?): Pair<List<RefLink>, String> {
    val defaultName = "Main link"
    val parsed = runCatching { raw?.let { JSONObject(it) } }.getOrNull()
    val linksArr = parsed?.optJSONArray("links") ?: JSONArray()
    val activeIdRaw = parsed?.optString("activeId", "") ?: ""
    val defaultNameLc = defaultName.lowercase()
    val normalized = (0 until linksArr.length()).mapNotNull { i ->
        val item = linksArr.optJSONObject(i) ?: return@mapNotNull null
        val label = item.optString("name", "").trim()
        if (label.isBlank()) return@mapNotNull null
        val id = item.optString("id", "id-${System.currentTimeMillis()}")
        val isDefault = id == "default" || label.lowercase() == defaultNameLc
        val rawSlug = item.optString("slug", "").trim()
        val slug = when {
            isDefault && (rawSlug == "main-link" || rawSlug == slugifyLabel(defaultName)) -> ""
            isDefault -> ""
            rawSlug.isNotBlank() -> slugifyLabel(rawSlug)
            else -> slugifyLabel(label)
        }
        RefLink(id = id, name = label, slug = slug)
    }.filter { it.name.isNotBlank() }.take(5)
    val links = if (normalized.isEmpty()) listOf(RefLink("default", defaultName, "")) else normalized
    val activeId = if (links.any { it.id == activeIdRaw }) activeIdRaw else links.first().id
    return Pair(links, activeId)
}

private fun slugifyLabel(name: String): String = name
    .lowercase()
    .replace(Regex("[^a-z0-9\\s-]"), "")
    .trim()
    .replace(Regex("\\s+"), "-")
    .replace(Regex("-+"), "-")
    .take(40)

private fun buildNamedRefUrl(baseUrl: String, slug: String): String {
    if (slug.isBlank()) return baseUrl
    return try {
        val u = java.net.URI(baseUrl).toURL()
        val portSuffix = if (u.port in 1..65535 && u.port != 80 && u.port != 443) ":${u.port}" else ""
        "${u.protocol}://${u.host}$portSuffix/${slug.lowercase()}"
    } catch (_: Exception) {
        baseUrl
    }
}

/**
 * Kombiniert Ref-Link mit aktuellem Seitenpfad.
 * @param refUrl Basis-Ref-URL (z.B. https://www.eazpire.com/myslug)
 * @param pagePath Aktueller Pfad (z.B. /collections/women oder /)
 */
fun buildShareUrl(refUrl: String, pagePath: String): String {
    if (pagePath.isBlank() || pagePath == "/") return refUrl
    val path = if (pagePath.startsWith("/")) pagePath else "/$pagePath"
    return refUrl.trimEnd('/') + path
}
