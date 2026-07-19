package com.eazpire.creator.ui.share

import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val REF_LINKS_SETTING_KEY = "community_ref_links_v1"
private const val WEB_BASE = "https://www.eazpire.com"
private const val JOIN_BASE = "https://join.eazpire.com"
private const val PLAY_STORE_APP = "https://play.google.com/store/apps/details?id=com.eazpire.creator"

enum class ReferralShareTarget {
    /** Web: eazpire.com homepage with ref tracking */
    Homepage,
    /** App share: ref link targeting Play Store install */
    AndroidApp,
}

private data class ActiveRefMeta(val joinUrl: String, val refName: String)

/**
 * Baut die aktive Ref-Link-Basis aus API-Daten.
 * Format: join.eazpire.com/{slug} oder join.eazpire.com/{code}
 */
suspend fun getActiveRefUrl(api: CreatorApi, ownerId: String): String? =
    getActiveRefMeta(api, ownerId)?.joinUrl

private suspend fun getActiveRefMeta(api: CreatorApi, ownerId: String): ActiveRefMeta? = withContext(Dispatchers.IO) {
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
        ActiveRefMeta(
            joinUrl = "$JOIN_BASE/$pathPart",
            refName = slug?.lowercase() ?: "main",
        )
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

private fun sanitizeShareTargetUrl(targetUrl: String): String {
    return try {
        val u = java.net.URI(targetUrl)
        val query = u.query
            ?.split("&")
            ?.mapNotNull { part ->
                val key = part.substringBefore("=").lowercase()
                if (key in setOf("eaz_pdp_modal", "ref", "ref_name", "logged_in_customer_id", "path_prefix")) {
                    null
                } else {
                    part
                }
            }
            ?.joinToString("&")
            ?.takeIf { it.isNotBlank() }
        buildString {
            append(u.scheme).append("://").append(u.host)
            if (u.port > 0) append(":").append(u.port)
            append(u.path ?: "/")
            if (!query.isNullOrBlank()) append("?").append(query)
        }
    } catch (_: Exception) {
        targetUrl
    }
}

/**
 * Resolve a shareable URL: prefers opaque short link join.eazpire.com/s/{token}.
 * Falls back to legacy join/{slug}?url=… when the shortener is unavailable.
 */
suspend fun resolveShareUrl(api: CreatorApi, ownerId: String, pagePath: String): String = withContext(Dispatchers.IO) {
    val targetUrl = when {
        pagePath.isBlank() || pagePath == "/" -> WEB_BASE
        pagePath.startsWith("http://") || pagePath.startsWith("https://") -> pagePath
        else -> WEB_BASE + (if (pagePath.startsWith("/")) pagePath else "/$pagePath")
    }.let(::sanitizeShareTargetUrl)

    val meta = getActiveRefMeta(api, ownerId)
    val refUrl = meta?.joinUrl
    val refName = meta?.refName ?: "main"

    try {
        val shortRes = api.createShortRefLink(ownerId, targetUrl, refName)
        if (shortRes.optBoolean("ok", false)) {
            val shortUrl = shortRes.optString("short_url", "").takeIf { it.isNotBlank() }
            if (!shortUrl.isNullOrBlank()) return@withContext shortUrl
            if (shortRes.optBoolean("home", false) && !refUrl.isNullOrBlank()) {
                return@withContext refUrl
            }
        }
    } catch (_: Exception) {
        // fall through
    }

    if (!refUrl.isNullOrBlank()) {
        return@withContext encodeJoinUrlWithTarget(refUrl, targetUrl)
    }
    targetUrl
}

/**
 * Baut Share-URL im Web-Format: bevorzugt Short-Link, sonst join?url=
 * @deprecated Prefer [resolveShareUrl] which creates opaque short tokens.
 */
fun buildShareUrl(refUrl: String, pagePath: String): String {
    val targetUrl = when {
        pagePath.isBlank() || pagePath == "/" -> WEB_BASE
        pagePath.startsWith("http://") || pagePath.startsWith("https://") -> pagePath
        else -> WEB_BASE + (if (pagePath.startsWith("/")) pagePath else "/$pagePath")
    }
    return encodeJoinUrlWithTarget(refUrl, sanitizeShareTargetUrl(targetUrl))
}

/** Ref share URL for journey invite etc. — uses opaque shortener when possible. */
suspend fun buildReferralShareUrl(
    api: CreatorApi,
    ownerId: String,
    target: ReferralShareTarget,
): String = when (target) {
    ReferralShareTarget.Homepage -> resolveShareUrl(api, ownerId, "/")
    ReferralShareTarget.AndroidApp -> resolveShareUrl(api, ownerId, PLAY_STORE_APP)
}

/** Legacy overload kept for call sites that already have a join base URL. */
fun buildReferralShareUrl(refUrl: String, target: ReferralShareTarget): String = when (target) {
    ReferralShareTarget.Homepage -> buildShareUrl(refUrl, "/")
    ReferralShareTarget.AndroidApp -> buildShareUrl(refUrl, PLAY_STORE_APP)
}

private fun encodeJoinUrlWithTarget(refUrl: String, targetUrl: String): String {
    return try {
        val u = java.net.URI(refUrl)
        val base = "${u.scheme}://${u.host}${u.path}"
        "$base?url=${java.net.URLEncoder.encode(targetUrl, "UTF-8")}"
    } catch (_: Exception) {
        refUrl
    }
}
