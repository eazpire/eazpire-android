package com.eazpire.creator.ui.share

import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val REF_LINKS_SETTING_KEY = "community_ref_links_v1"
private const val WEB_BASE = "https://www.eazpire.com"
private const val JOIN_BASE = "https://join.eazpire.com"

/**
 * Baut die aktive Ref-Link-URL aus API-Daten.
 * Format wie Web: join.eazpire.com/{slug} oder join.eazpire.com/{code}
 */
suspend fun getActiveRefUrl(api: CreatorApi, ownerId: String): String? = withContext(Dispatchers.IO) {
    if (ownerId.isBlank()) return@withContext null
    try {
        val ref = api.getReferralCode(ownerId)
        if (!ref.optBoolean("ok", false)) return@withContext null
        val code = ref.optString("code", "").takeIf { it.isNotBlank() } ?: return@withContext null
        val settingRes = api.getCustomerSetting(ownerId, REF_LINKS_SETTING_KEY)
        val raw = if (settingRes.optBoolean("ok", false)) settingRes.optString("value", "").takeIf { it.isNotBlank() } else null
        val (links, activeId) = parseRefLinks(raw)
        val activeLink = links.find { it.id == activeId } ?: links.firstOrNull()
        val slug = activeLink?.slug?.takeIf { it.isNotBlank() }
        val pathPart = (slug ?: code).lowercase()
        "$JOIN_BASE/$pathPart"
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

/**
 * Baut Share-URL im Web-Format: join.eazpire.com/{slug}?url={encoded_target}
 * Worker leitet weiter zu target + ?ref=code. Ohne App: Web lädt. Mit App: App öffnet via Deep Link.
 *
 * @param refUrl join.eazpire.com/{slug} oder join.eazpire.com/{code}
 * @param pagePath Zielpfad (z.B. /collections/women, /products/xyz)
 */
fun buildShareUrl(refUrl: String, pagePath: String): String {
    val targetUrl = when {
        pagePath.isBlank() || pagePath == "/" -> WEB_BASE
        else -> WEB_BASE + (if (pagePath.startsWith("/")) pagePath else "/$pagePath")
    }
    return try {
        val u = java.net.URI(refUrl)
        val base = "${u.scheme}://${u.host}${u.path}"
        "$base?url=${java.net.URLEncoder.encode(targetUrl, "UTF-8")}"
    } catch (_: Exception) {
        refUrl
    }
}
